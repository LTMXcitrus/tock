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

package fr.vsct.tock.bot.mongo

import fr.vsct.tock.bot.engine.event.EventType
import fr.vsct.tock.bot.engine.user.PlayerId
import java.time.Instant

/**
 *
 */
data class DialogStatCol(
        val userId: PlayerId,
        val applicationId: String,
        val dialogId: String,
        val handled: Boolean,
        val date: Instant,
        val eventType: EventType,
        val message: String?,
        val story: String?,
        val intent: String?,
        val step: String?,
        val _id: String?) {
}