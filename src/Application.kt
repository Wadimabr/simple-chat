package dev.vaabr

import dev.vaabr.json.Message
import dev.vaabr.sessions.Session
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.routing.*
import io.ktor.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.sessions.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(Sessions) {
        cookie<Session>("SESSION", storage = SessionStorageMemory())
    }

    install(ContentNegotiation) {
        json()
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val messages = Channel<Message>()
    val clients = mutableListOf<WebSocketServerSession>()

    launch(coroutineContext) {
        while (true) {
            val message = messages.receive()
            clients.asFlow().collect {
                it.outgoing.send(Frame.Text(Json.encodeToString(message)))
            }
        }
    }

    intercept(ApplicationCallPipeline.Call) {
        call.sessions.get<Session>() ?: call.sessions.set(Session())
    }

    routing {
        get("/") {
            call.respondRedirect("/index.html")
        }

        static {
            resources("static")
        }

        webSocket("/chat") {
            clients.add(this)
            try {
                for (frame in incoming) {
                    val uuid = call.sessions.get<Session>()?.uuid ?: throw InvalidUUIDException()
                    val text = (frame as Frame.Text).readText()
                    val message = Message(uuid.toString(), text)
                    messages.send(message)
                }
            } catch (e: InvalidUUIDException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "UUID is invalid"))
            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${closeReason.await()}")
            } finally {
                clients.remove(this)
            }
        }
    }
}

private class InvalidUUIDException : RuntimeException()