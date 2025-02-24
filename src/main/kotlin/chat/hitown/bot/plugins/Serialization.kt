/**
 * Json serialization Ktor plugin.
 */
package chat.hitown.bot.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

val json = Json {
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(json)
    }
}
