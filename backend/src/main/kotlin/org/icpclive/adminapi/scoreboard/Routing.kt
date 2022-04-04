package org.icpclive.adminapi.scoreboard

import io.ktor.server.routing.*
import org.icpclive.adminapi.WidgetWrapper
import org.icpclive.adminapi.setupSimpleWidgetRouting
import org.icpclive.api.ScoreboardSettings
import org.icpclive.api.ScoreboardWidget


fun Routing.configureScoreboardApi() =
        setupSimpleWidgetRouting<ScoreboardSettings, ScoreboardWidget>(
                prefix = "scoreboard",
                WidgetWrapper(createWidget = { ScoreboardWidget(it) }, ScoreboardSettings())
        )
