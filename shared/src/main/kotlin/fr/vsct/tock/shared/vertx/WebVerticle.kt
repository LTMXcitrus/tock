/*
 * Copyright (C) 2017 VSCT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.vsct.tock.shared.vertx

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.readValue
import fr.vsct.tock.shared.booleanProperty
import fr.vsct.tock.shared.devEnvironment
import fr.vsct.tock.shared.error
import fr.vsct.tock.shared.intProperty
import fr.vsct.tock.shared.jackson.mapper
import fr.vsct.tock.shared.longProperty
import fr.vsct.tock.shared.property
import fr.vsct.tock.shared.security.PropertyBasedAuthProvider
import fr.vsct.tock.shared.security.TockUser
import fr.vsct.tock.shared.security.TockUserRole
import fr.vsct.tock.shared.security.TockUserRole.user
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpMethod.DELETE
import io.vertx.core.http.HttpMethod.GET
import io.vertx.core.http.HttpMethod.POST
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.web.FileUpload
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BasicAuthHandler
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.UserSessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import mu.KLogger
import mu.KotlinLogging
import org.litote.kmongo.Id
import org.litote.kmongo.toId
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.EnumSet

/**
 * Base class for web Tock [io.vertx.core.Verticle]s. Provides utility methods.
 */
abstract class WebVerticle : AbstractVerticle() {

    open protected val logger: KLogger = KotlinLogging.logger {}

    private data class AuthenticateRequest(val email: String, val password: String)

    private data class AuthenticateResponse(
            val authenticated: Boolean,
            val email: String? = null,
            val organization: String? = null)

    private data class BooleanResponse(val success: Boolean = true)

    protected val router: Router by lazy {
        Router.router(vertx)
    }

    protected val server: HttpServer by lazy {
        vertx.createHttpServer(
                HttpServerOptions()
                        .setCompressionSupported(verticleBooleanProperty("tock_vertx_compression_supported", true))
                        .setDecompressionSupported(verticleBooleanProperty("tock_vertx_compression_supported", true))
        )
    }

    protected open val rootPath: String = ""

    protected open val authenticatePath: String = "/rest/authenticate"

    protected open val logoutPath: String = "/rest/logout"

    private val verticleName: String = this::class.simpleName!!

    protected open fun protectedPath(): String = rootPath

    abstract fun configure()

    abstract fun healthcheck(): (RoutingContext) -> Unit

    override fun start(startFuture: Future<Void>) {
        vertx.executeBlocking<Unit>(
                {
                    try {
                        router.route().handler(bodyHandler())
                        addDevCorsHandler()
                        authProvider()?.let {
                            addAuth(it)
                        }

                        router.get("$rootPath/healthcheck").handler(healthcheck())
                        configure()

                        it.complete()
                    } catch (t: MissingKotlinParameterException) {
                        logger.error(t)
                        it.fail(BadRequestException(t.message ?: ""))
                    } catch (t: JsonProcessingException) {
                        logger.error(t)
                        it.fail(BadRequestException(t.message ?: ""))
                    } catch (t: Throwable) {
                        logger.error(t)
                        it.fail(t)
                    }
                },
                false,
                {
                    if (it.succeeded()) {
                        startServer(startFuture)
                    }
                })
    }


    protected fun addAuth(
            authProvider: AuthProvider = defaultAuthProvider(),
            pathsToProtect: List<String> = listOf("${protectedPath()}/*")) {

        val authHandler = BasicAuthHandler.create(authProvider)

        pathsToProtect.forEach { protectedPath ->
            router.route(protectedPath).handler(CookieHandler.create())
            router.route(protectedPath).handler(SessionHandler.create(LocalSessionStore.create(vertx))
                    .setSessionTimeout(6 * 60 * 60 * 1000 /*6h*/)
                    .setNagHttps(devEnvironment)
                    .setCookieHttpOnlyFlag(!devEnvironment)
                    .setCookieSecureFlag(!devEnvironment)
                    .setSessionCookieName("tock-session"))
            router.route(protectedPath).handler(UserSessionHandler.create(authProvider))
            router.route(protectedPath).handler(authHandler)
        }


        router.post(authenticatePath).handler { context ->
            val request = mapper.readValue<AuthenticateRequest>(context.bodyAsString)
            val authInfo = JsonObject().put("username", request.email).put("password", request.password)
            authProvider.authenticate(authInfo, {
                if (it.succeeded()) {
                    val user = it.result()
                    context.setUser(user)
                    context.endJson(AuthenticateResponse(true, request.email, (user as TockUser).namespace))
                } else {
                    context.endJson(AuthenticateResponse(false))
                }
            })
        }

        router.post(logoutPath).handler {
            it.clearUser()
            it.success()
        }
    }

    /**
     * The auth provider provided by default.
     */
    protected open fun defaultAuthProvider(): AuthProvider = PropertyBasedAuthProvider

    /**
     * By default there is no auth provider - ie nothing is protected.
     */
    protected open fun authProvider(): AuthProvider? = null

    /**
     * The default role of a service.
     */
    protected open fun defaultRole(): TockUserRole? =
            if (authProvider() == null) null else user

    protected open fun startServer(startFuture: Future<Void>) {
        val port = verticleIntProperty("port", 8080)
        server.requestHandler { r -> router.accept(r) }
                .listen(port,
                        { r ->
                            if (r.succeeded()) {
                                logger.info { "$verticleName started on port $port" }
                                startFuture.complete()
                            } else {
                                logger.error { "$verticleName NOT started on port $port" }
                                startFuture.fail(r.cause())
                            }
                        })
    }

    private fun verticleProperty(propertyName: String) = "${verticleName.toLowerCase()}_$propertyName"

    protected fun verticleIntProperty(propertyName: String, defaultValue: Int): Int = intProperty(verticleProperty(propertyName), defaultValue)

    protected fun verticleLongProperty(propertyName: String, defaultValue: Long): Long = longProperty(verticleProperty(propertyName), defaultValue)

    protected fun verticleBooleanProperty(propertyName: String, defaultValue: Boolean): Boolean = booleanProperty(verticleProperty(propertyName), defaultValue)

    protected fun verticleProperty(propertyName: String, defaultValue: String): String = property(verticleProperty(propertyName), defaultValue)

    protected fun blocking(method: HttpMethod,
                           path: String,
                           role: TockUserRole? = defaultRole(),
                           handler: (RoutingContext) -> Unit) {
        fun sendBlocking(context: RoutingContext) {
            vertx.executeBlocking<Unit>({
                try {
                    handler.invoke(context)
                    it.succeeded()
                } catch (t: Throwable) {
                    it.fail(t)
                }
            },
                    false,
                    {
                        if (it.failed()) {
                            it.cause().apply {
                                if (this is RestException) {
                                    context.response().statusMessage = message
                                    context.fail(code)
                                } else if (this != null) {
                                    logger.error(this)
                                    context.fail(this)
                                } else {
                                    logger.error { "unknown error" }
                                    context.fail(500)
                                }
                            }

                        }
                    }
            )
        }

        router.route(method, "$rootPath$path")
                .handler { context ->
                    val user = context.user()
                    if (user == null || role == null) {
                        sendBlocking(context)
                    } else {
                        user.isAuthorised(role.name) {
                            if (it.succeeded()) {
                                sendBlocking(context)
                            } else {
                                context.fail(401)
                            }
                        }
                    }
                }
    }

    protected inline fun <reified I : Any, O> blockingWithBodyJson(
            method: HttpMethod,
            path: String,
            role: TockUserRole?,
            crossinline handler: (RoutingContext, I) -> O) {
        blocking(method, path, role, { context ->
            val input = context.readJson<I>()

            val result = handler.invoke(context, input)
            context.endJson(result)
        })
    }

    private fun <O> blockingWithoutBodyJson(
            method: HttpMethod,
            path: String,
            role: TockUserRole?,
            handler: (RoutingContext) -> O) {
        blocking(method, path, role, { context ->
            val result = handler.invoke(context)
            context.endJson(result)
        })
    }

    protected fun <O> blockingJsonGet(path: String, role: TockUserRole? = defaultRole(), handler: (RoutingContext) -> O) {
        blocking(GET, path, role, { context ->
            val result = handler.invoke(context)
            context.endJson(result)
        })
    }

    protected fun blockingPost(path: String, role: TockUserRole? = defaultRole(), handler: (RoutingContext) -> Unit) {
        blocking(POST, path, role) { context ->
            handler.invoke(context)
            context.success()
        }
    }

    protected inline fun <reified F : Any, O> blockingUploadJsonPost(path: String, role: TockUserRole? = defaultRole(), crossinline handler: (RoutingContext, F) -> O) {
        blocking(POST, path, role) { context ->
            val upload = context.fileUploads().first()
            val f = readJson<F>(upload)
            val result = handler.invoke(context, f)
            context.endJson(result)
        }
    }

    protected inline fun <O> blockingUploadPost(path: String, role: TockUserRole? = defaultRole(), crossinline handler: (RoutingContext, String) -> O) {
        blocking(POST, path, role) { context ->
            val upload = context.fileUploads().first()
            val f = readString(upload)
            val result = handler.invoke(context, f)
            context.endJson(result)
        }
    }

    protected inline fun <reified I : Any, O> blockingJsonPost(path: String, role: TockUserRole? = defaultRole(), crossinline handler: (RoutingContext, I) -> O) {
        blockingWithBodyJson<I, O>(POST, path, role, handler)
    }

    protected fun blockingDelete(path: String, role: TockUserRole? = defaultRole(), handler: (RoutingContext) -> Unit) {
        blocking(DELETE, path, role) { context ->
            handler.invoke(context)
            context.success()
        }
    }

    protected fun blockingJsonDelete(path: String, role: TockUserRole? = defaultRole(), handler: (RoutingContext) -> Boolean) {
        blockingWithoutBodyJson(DELETE, path, role, { BooleanResponse(handler.invoke(it)) })
    }

    override fun stop(stopFuture: Future<Void>?) {
        server.close { e -> logger.info { "$verticleName stopped result : ${e.succeeded()}" } }
    }

    open protected fun addDevCorsHandler() {
        if (booleanProperty("tock_web_use_default_cors_handler", devEnvironment)) {
            router.route().handler(
                    corsHandler(
                            property("tock_web_use_default_cors_handler_url", "http://localhost:4200"),
                            booleanProperty("tock_web_use_default_cors_handler_with_credentials", true)
                    )
            )
        }
    }

    protected fun corsHandler(
            origin: String = "*",
            allowCredentials: Boolean = false,
            allowedMethods: Set<HttpMethod> = EnumSet.of(GET, POST, DELETE),
            allowedHeaders: Set<String> = listOfNotNull("X-Requested-With", "Access-Control-Allow-Origin", if (allowCredentials) "Authorization" else null, "Content-Type").toSet()
    ): CorsHandler {
        return CorsHandler.create(origin)
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .allowCredentials(allowCredentials)
    }

    protected fun bodyHandler(): BodyHandler {
        return BodyHandler.create().setBodyLimit(verticleLongProperty("body_limit", 1000000L)).setMergeFormAttributes(false)
    }

// extension methods ->

    inline fun <reified T : Any> RoutingContext.readJson(): T {
        return mapper.readValue<T>(this.bodyAsString)
    }

    inline fun <reified T : Any> readJson(upload: FileUpload): T {
        return mapper.readValue<T>(File(upload.uploadedFileName()))
    }

    fun readString(upload: FileUpload): String {
        return String(Files.readAllBytes(Paths.get(upload.uploadedFileName())), StandardCharsets.UTF_8)
    }

    fun RoutingContext.success() {
        this.endJson(true)
    }

    fun RoutingContext.endJson(success: Boolean) {
        this.endJson(fr.vsct.tock.shared.vertx.WebVerticle.BooleanResponse(success))
    }

    fun RoutingContext.endJson(result: Any?) {
        this.response().endJson(result)
    }

    fun <T> RoutingContext.pathId(name: String): Id<T> = pathParam(name).toId()

    fun RoutingContext.queryParam(name: String): String? = request().getParam(name)

    fun <T> RoutingContext.queryId(name: String): Id<T>? = queryParam(name)?.toId()

    val RoutingContext.organization: String
        get() = (this.user() as TockUser).namespace

    fun HttpServerResponse.endJson(result: Any?) {
        if (result == null) {
            statusCode = 204
        }
        this.putHeader("content-type", "application/json; charset=utf-8")
        if (result == null) {
            end()
        } else {
            val output = mapper.writeValueAsString(result)
            end(output)
        }
    }

    fun unauthorized(): Nothing = throw UnauthorizedException()

    fun notFound(): Nothing = throw NotFoundException()

    fun badRequest(message: String): Nothing = throw BadRequestException(message)
}