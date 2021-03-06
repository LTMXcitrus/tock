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

package fr.vsct.tock.bot.test

import fr.vsct.tock.bot.connector.ConnectorMessage
import fr.vsct.tock.bot.connector.ConnectorType
import fr.vsct.tock.bot.engine.action.ActionPriority
import fr.vsct.tock.bot.engine.action.ActionNotificationType

/**
 *
 */
internal data class BusMockData(
        var currentDelay: Long = 0,
        val connectorMessages: MutableMap<ConnectorType, ConnectorMessage> = mutableMapOf(),
        val contextMap: MutableMap<String, Any> = mutableMapOf(),
        var priority: ActionPriority = ActionPriority.normal,
        var notificationType: ActionNotificationType? = null) {

    fun clear() {
        connectorMessages.clear()
        priority = ActionPriority.normal
    }

    fun addMessage(message: ConnectorMessage) {
        connectorMessages.put(message.connectorType, message)
    }

    fun getMessage(type: ConnectorType): ConnectorMessage? = connectorMessages[type]
}