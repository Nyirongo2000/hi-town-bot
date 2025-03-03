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
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(
        factory = Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = {
            configureRouting()
        }
    ).start(wait = true)
}

fun Application.configureRouting() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Origin)
        allowHeader(HttpHeaders.UserAgent)
        allowCredentials = true
        maxAgeInSeconds = 3600
        anyHost()
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            "$method $path - $status - $duration ms"
        }
    }

    routing {
        route("/") {
            get {
                call.respond(bot.details)
            }
        }

        route("/health") {
            get {
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "healthy",
                    "timestamp" to System.currentTimeMillis()
                ))
            }
        }

        route("/install") {
            post {
                try {
                    val body = call.receive<InstallBotBody>()
                    if (!bot.validateInstall(body.secret)) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid install secret"))
                        return@post
                    }
                    val token = java.util.UUID.randomUUID().toString()
                    bot.install(token, body)
                    call.respond(InstallBotResponse(token = token))
                } catch (e: Exception) {
                    call.application.log.error("Install error", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to e.message,
                        "type" to e.javaClass.simpleName
                    ))
                }
            }
        }

        route("/reinstall") {
            post {
                try {
                    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))

                    val body = call.receive<ReinstallBotBody>()
                    body.config?.let { config ->
                        bot.reinstall(token, config)
                    }
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.application.log.error("Reinstall error", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to e.message,
                        "type" to e.javaClass.simpleName
                    ))
                }
            }
        }

        route("/uninstall") {
            post {
                try {
                    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))

                    bot.uninstall(token)
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.application.log.error("Uninstall error", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to e.message,
                        "type" to e.javaClass.simpleName
                    ))
                }
            }
        }

        route("/message") {
            post {
                try {
                    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))

                    val body = call.receive<MessageBotBody>()
                    val response = bot.message(token, body)
                    call.respond(response)
                } catch (e: Exception) {
                    call.application.log.error("Message error", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to e.message,
                        "type" to e.javaClass.simpleName
                    ))
                }
            }
        }
    }
}
