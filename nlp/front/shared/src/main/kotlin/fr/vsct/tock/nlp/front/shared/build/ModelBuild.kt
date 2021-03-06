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

package fr.vsct.tock.nlp.front.shared.build

import fr.vsct.tock.nlp.front.shared.config.ApplicationDefinition
import fr.vsct.tock.nlp.front.shared.config.IntentDefinition
import org.litote.kmongo.Id
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 *
 */
data class ModelBuild(
        val applicationId: Id<ApplicationDefinition>,
        val language: Locale,
        val type: ModelBuildType,
        val intentId: Id<IntentDefinition>?,
        val entityTypeName: String?,
        val nbSentences: Int,
        val duration: Duration,
        val error: Boolean,
        val errorMessage: String?,
        val date: Instant) {
}