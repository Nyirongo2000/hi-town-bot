/**
 * The Ktor server.
 */

package chat.hitown.bot.plugins

import configureRouting
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = {
            configureSerialization()
            configureRouting()
        }
    )
        .start(wait = true)
}
