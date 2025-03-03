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
import java.util.*

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
            encodeDefaults = true
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
        get("/") {
            call.respond(bot.details)
        }

        post("/install") {
            try {
                println("=== Install Request Received ===")
                val body = call.receive<InstallBotBody>()
                println("Install request body: $body")
                println("Group ID: ${body.groupId}")
                println("Group Name: ${body.groupName}")
                println("Webhook: ${body.webhook}")
                println("Config: ${body.config}")
                println("Secret: ${body.secret}")
                
                if (!bot.validateInstall(body.secret)) {
                    println("Invalid install secret")
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid install secret"))
                    return@post
                }
                
                val token = UUID.randomUUID().toString()
                println("Generated token: $token")
                
                bot.install(token, body)
                println("Bot installed successfully")
                
                call.respond(InstallBotResponse(token = token))
            } catch (e: Exception) {
                println("Install error: ${e.message}")
                e.printStackTrace()
                call.application.log.error("Install error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to e.message,
                    "type" to e.javaClass.simpleName
                ))
            }
        }

        post("/reinstall") {
            try {
                println("=== Reinstall Request Received ===")
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: run {
                        println("Missing authorization token")
                        return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))
                    }
                println("Token: $token")

                val body = call.receive<ReinstallBotBody>()
                println("Reinstall request body: $body")
                println("Config: ${body.config}")
                
                val config = body.config ?: emptyList()
                println("Using config: $config")
                
                bot.reinstall(token, config)
                println("Bot reinstalled successfully")
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                println("Reinstall error: ${e.message}")
                e.printStackTrace()
                call.application.log.error("Reinstall error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to e.message,
                    "type" to e.javaClass.simpleName
                ))
            }
        }

        post("/uninstall") {
            try {
                println("=== Uninstall Request Received ===")
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: run {
                        println("Missing authorization token")
                        return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))
                    }
                println("Token: $token")

                bot.uninstall(token)
                println("Bot uninstalled successfully")
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                println("Uninstall error: ${e.message}")
                e.printStackTrace()
                call.application.log.error("Uninstall error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to e.message,
                    "type" to e.javaClass.simpleName
                ))
            }
        }

        post("/message") {
            try {
                println("=== Message Request Received ===")
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: run {
                        println("Missing authorization token")
                        return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))
                    }
                println("Token: $token")

                val body = call.receive<MessageBotBody>()
                println("Message request body: $body")
                println("Message: ${body.message}")
                println("Person: ${body.person}")
                
                val response = bot.message(token, body)
                println("Message response: $response")
                call.respond(response)
            } catch (e: Exception) {
                println("Message error: ${e.message}")
                e.printStackTrace()
                call.application.log.error("Message error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to e.message,
                    "type" to e.javaClass.simpleName
                ))
            }
        }

        post("/pause") {
            try {
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))

                bot.pause(token)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.application.log.error("Pause error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to e.message,
                    "type" to e.javaClass.simpleName
                ))
            }
        }

        post("/resume") {
            try {
                val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authorization token"))

                bot.resume(token)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.application.log.error("Resume error", e)
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to e.message,
                    "type" to e.javaClass.simpleName
                ))
            }
        }
    }
}
