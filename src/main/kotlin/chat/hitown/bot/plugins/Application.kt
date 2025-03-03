/**
 * The Ktor server.
 */

package chat.hitown.bot.plugins

import chat.hitown.bot.bot
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.defaultheaders.*
import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Install CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    // Install default headers
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    // Install content negotiation
    install(ContentNegotiation) {
        json()
    }

    // Install call logging
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    routing {
        // Health check endpoint
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        // GET / - Bot Information
        get("/") {
            call.respond(bot.details)
        }

        // POST /install - Bot Installation
        post("/install") {
            try {
                val body = call.receive<InstallBotBody>()
                val token = java.util.UUID.randomUUID().toString()
                bot.install(token, body)
                call.respond(InstallBotResponse(token = token))
            } catch (e: Exception) {
                call.application.log.error("Install error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // POST /reinstall - Update Configuration
        post("/reinstall") {
            try {
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val body = call.receive<ReinstallBotBody>()
                body.config?.let { config ->
                    bot.reinstall(token, config)
                }
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.application.log.error("Reinstall error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // POST /uninstall - Remove Bot
        post("/uninstall") {
            try {
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                bot.uninstall(token)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.application.log.error("Uninstall error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // POST /message - Handle Messages
        post("/message") {
            try {
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val body = call.receive<MessageBotBody>()
                val response = bot.message(token, body)
                call.respond(response)
            } catch (e: Exception) {
                call.application.log.error("Message error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // POST /pause - Pause Bot
        post("/pause") {
            try {
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                bot.pause(token)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.application.log.error("Pause error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // POST /resume - Resume Bot
        post("/resume") {
            try {
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                bot.resume(token)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.application.log.error("Resume error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }
    }
}
