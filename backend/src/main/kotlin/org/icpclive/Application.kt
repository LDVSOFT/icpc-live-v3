package org.icpclive

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.serialization.*
import kotlinx.serialization.json.Json
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.icpclive.admin.configureAdminRouting
import org.icpclive.events.EventsLoader
import org.icpclive.listeners.LoggerEventListener
import org.icpclive.overlay.configureOverlayRouting
import org.icpclive.queue.QueueLoader
import org.slf4j.event.Level
import java.time.Duration

fun main(args: Array<String>): Unit =
        io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            isLenient = true
            allowSpecialFloatingPointValues = true
            allowStructuredMapKeys = true
            prettyPrint = false
            useArrayPolymorphism = false
        })
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        static("/static") { resources("static") }
    }
    configureAdminRouting()
    configureOverlayRouting()
    environment.config.propertyOrNull("live.configDirectory")?.getString()?.run {
        environment.log.info("Using config directory $this")
        Config.setConfigDirectory(this)
    }
    Thread(EventsLoader.getInstance()).start()
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.IO) { QueueLoader().run() }
    runBlocking { EventManager.registerListener(LoggerEventListener()) }
}
