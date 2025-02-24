/**
 * Routes as defined in the Hi Town bot specification.
 */

import chat.hitown.bot.bot
import chat.hitown.bot.plugins.InstallBotBody
import chat.hitown.bot.plugins.InstallBotResponse
import chat.hitown.bot.plugins.MessageBotBody
import chat.hitown.bot.plugins.ReinstallBotBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlin.random.Random

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond(bot.details)
        }

        post("/install") {
            val body = call.receive<InstallBotBody>()

            if (!bot.validateInstall(body.secret)) {
                return@post call.respond(HttpStatusCode.Unauthorized)
            }

            val token = (0..255).token()

            bot.install(token, body)

            call.respond(InstallBotResponse(token = token))
        }

        post("/reinstall") {
            val token = call.token
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val body = call.receive<ReinstallBotBody>()

            bot.reinstall(token, body.config.orEmpty())

            call.respond(HttpStatusCode.NoContent)
        }

        post("/uninstall") {
            val token = call.token
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            bot.uninstall(token)

            call.respond(HttpStatusCode.NoContent)
        }

        post("/message") {
            val token = call.token
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val body = call.receive<MessageBotBody>()

            val response = bot.message(token, body)

            call.respond(response)
        }
    }
}

private val ApplicationCall.token get() =
    request.header(HttpHeaders.Authorization)?.split(" ")?.last()

private fun IntRange.token() =
    joinToString("") {
        Random.nextInt(35).toString(36).let {
            if (Random.nextBoolean()) it.uppercase() else it
        }
    }
