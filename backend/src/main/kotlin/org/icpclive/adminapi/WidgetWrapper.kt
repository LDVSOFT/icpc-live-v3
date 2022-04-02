package org.icpclive.adminapi

import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.html.Entities
import org.icpclive.api.*
import org.icpclive.data.WidgetManager

class WidgetWrapper<SettingsType : ObjectSettings, WidgetType : Widget>(
        private val createWidget: (SettingsType) -> WidgetType,
        var settings: SettingsType,
        val id: Int? = null) {
    private val mutex = Mutex()

    private var widgetId: String? = null

    suspend fun getStatus(): ObjectStatus<SettingsType> = mutex.withLock {
        return ObjectStatus(widgetId != null, settings, id)
    }

    suspend fun set(newSettings: SettingsType) {
        mutex.withLock {
            settings = newSettings
        }
    }

    suspend fun show() {
        mutex.withLock {
            if (widgetId != null)
                return
            val widget = createWidget(settings)
            WidgetManager.showWidget(widget)
            widgetId = widget.widgetId
        }
    }

    suspend fun show(newSettings: SettingsType) {
        set(newSettings)
        show()
    }

    suspend fun hide() {
        mutex.withLock {
            widgetId?.let {
                WidgetManager.hideWidget(it)
            }
            widgetId = null
        }
    }
}