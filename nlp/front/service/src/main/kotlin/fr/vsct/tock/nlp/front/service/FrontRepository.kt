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

package fr.vsct.tock.nlp.front.service

import fr.vsct.tock.nlp.core.Application
import fr.vsct.tock.nlp.core.EntitiesRegexp
import fr.vsct.tock.nlp.core.Entity
import fr.vsct.tock.nlp.core.EntityType
import fr.vsct.tock.nlp.core.Intent
import fr.vsct.tock.nlp.core.NlpCore
import fr.vsct.tock.nlp.front.shared.ApplicationConfiguration
import fr.vsct.tock.nlp.front.shared.config.ApplicationDefinition
import fr.vsct.tock.nlp.front.shared.config.EntityTypeDefinition
import fr.vsct.tock.shared.injector
import fr.vsct.tock.shared.provide
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 *
 */
internal object FrontRepository {

    private val logger = KotlinLogging.logger {}

    val core: NlpCore get() = injector.provide()

    val config: ApplicationConfiguration get() = injector.provide()

    val entityTypes: MutableMap<String, EntityType?> by lazy {
        loadEntityTypes()
    }

    private fun reloadEntityTypes() {
        entityTypes.putAll(loadEntityTypes())
    }

    private fun loadEntityTypes(): MutableMap<String, EntityType?> {
        logger.info { "load entity types" }
        val entityTypesDefinitionMap = config.getEntityTypes().map { it.name to it }.toMap()
        val entityTypesMap = entityTypesDefinitionMap.mapValues { (_, v) -> EntityType(v.name) }

        val entityTypes: MutableMap<String, EntityType?> =
                entityTypesMap
                        .mapValues { (_, v) ->
                            v.copy(
                                    subEntities = entityTypesDefinitionMap[v.name]?.subEntities?.mapNotNull {
                                        entityTypesMap[it.entityTypeName]?.let { e ->
                                            Entity(e, it.role)
                                        }.apply {
                                            if (this == null) {
                                                logger.error { "entity ${it.entityTypeName} not found" }
                                            }
                                        }
                                    } ?: emptyList<Entity>()
                            )
                        }
                        .toMap()
                        .toMutableMap()
        return ConcurrentHashMap(entityTypes)
                //try to reload and refresh the cache if not found
                .withDefault {
                    val newValues = loadEntityTypes()
                    entityTypes.forEach { e ->
                        if (!newValues.containsKey(e.key)) {
                            entityTypes.remove(e.key)
                        }
                    }
                    entityTypes.putAll(newValues)
                    val result = newValues[it]
                    if (result == null) {
                        logger.error { "unknown entity type $it" }
                    }
                    result
                }
    }

    fun entityTypeExists(name: String): Boolean {
        if (entityTypes.isEmpty()) {
            reloadEntityTypes()
        }
        return entityTypes.containsKey(name)
    }

    fun entityTypeByName(name: String): EntityType? {
        if (entityTypes.isEmpty()) {
            reloadEntityTypes()
        }
        return entityTypes.getValue(name) ?: null.apply { error("unknown entity $name") }
    }

    fun toEntityType(entityType: EntityTypeDefinition): EntityType {
        return EntityType(entityType.name, entityType.subEntities.mapNotNull { it.toEntity() })
    }

    fun toEntity(type: String, role: String): Entity? {
        return entityTypeByName(type)?.let { Entity(it, role) }
    }

    fun toApplication(applicationDefinition: ApplicationDefinition): Application {
        val intentDefinitions = config.getIntentsByApplicationId(applicationDefinition._id)
        val intents = intentDefinitions.map {
            Intent(it.qualifiedName,
                    it.entities.mapNotNull { toEntity(it.entityTypeName, it.role) },
                    it.entitiesRegexp.mapValues { it.value.map { EntitiesRegexp(it.regexp) } })
        }
        return Application(applicationDefinition.name, intents, applicationDefinition.supportedLocales)
    }

    fun registerBuiltInEntities() {
        core.getEvaluableEntityTypes().forEach {
            if (!entityTypeExists(it)) {
                try {
                    logger.debug { "save built-in entity type $it" }
                    val entityType = EntityTypeDefinition(it, "built-in entity $it")
                    config.save(entityType)
                } catch (e: Exception) {
                    logger.warn("Fail to save built-in entity type $it", e)
                }
            }
        }

    }

}