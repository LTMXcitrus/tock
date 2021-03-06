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

package fr.vsct.tock.translator

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinInjector
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.provider
import com.nhaarman.mockito_kotlin.mock
import fr.vsct.tock.shared.tockInternalInjector
import org.junit.After
import org.junit.Before

/**
 *
 */
abstract class AbstractTest {

    companion object {
        val i18nDAO: I18nDAO = mock()
        val translatorEngine: TranslatorEngine = mock()
    }

    open fun baseModule(): Kodein.Module {
        return Kodein.Module {
            bind<I18nDAO>() with provider { i18nDAO }
            bind<TranslatorEngine>() with provider { translatorEngine }
        }
    }

    @Before
    fun initContext() {
        tockInternalInjector = KodeinInjector()
        tockInternalInjector.inject(Kodein {
            import(baseModule())
        })
    }

    @After
    fun cleanupContext() {
        tockInternalInjector = KodeinInjector()
    }


}