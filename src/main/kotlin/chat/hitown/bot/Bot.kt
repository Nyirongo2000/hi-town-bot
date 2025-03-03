/**
 * Write your bot here!
 *
 * Also see `Models.kt` for additional information.
 */

package chat.hitown.bot

import chat.hitown.bot.plugins.BotConfigValue
import chat.hitown.bot.plugins.BotDetails
import chat.hitown.bot.plugins.InstallBotBody
import chat.hitown.bot.plugins.MessageBotBody
import chat.hitown.bot.plugins.MessageBotResponse
import chat.hitown.bot.plugins.BotConfigField
import chat.hitown.bot.plugins.BotAction
import chat.hitown.bot.plugins.json
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import kotlinx.coroutines.*

/**
 * Bot instance.
 */
val bot = Bot()

class Bot {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            maxConnectionsCount = 100
            endpoint {
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                keepAliveTime = 5000
                connectTimeout = 5000
                connectAttempts = 5
            }
        }
    }

    /**
     * Bot details as shown in Hi Town.
     */
    val details = BotDetails(
        /**
         * Bot name.
         */
        name = "Hi-Town Reddit Bot",
        /**
         * Bot description.
         */
        description = "A bot that fetches top posts of the week from Reddit subreddit. Use commands like !reddit <subreddit> to get the latest posts.",
        /**
         * Keywords that will cause a Hi Town group message to be sent to the bot.
         *
         * Leaving this empty means the bot receives all messages sent to the group.
         */
        keywords = listOf("!reddit"),
        /**
         * Available configuration options for the bot (optional).
         */
        config = listOf(
            BotConfigField(
                key = "subreddit",
                label = "Default Subreddit",
                placeholder = "programming",
                type = "string",
                required = false
            )
        )
    )

    private suspend fun fetchRedditPosts(subreddit: String): String {
        try {
            val response = client.get("https://www.reddit.com/r/$subreddit/top.json?limit=10&t=week") {
                header("User-Agent", "HiTownBot/1.0 (by /u/hitown)")
                header("Accept", "application/json")
            }

            if (response.status.value !in 200..299) {
                return "Error: Reddit API returned status ${response.status.value}"
            }

            val responseText = response.bodyAsText()
            println("Reddit API Response: $responseText")
            
            val jsonResponse = Json.parseToJsonElement(responseText)
            val data = jsonResponse.jsonObject["data"]?.jsonObject
            val posts = data?.get("children")?.jsonArray

            if (posts == null || posts.isEmpty()) {
                return "No posts found in r/$subreddit"
            }

            val formattedPosts = posts.mapNotNull { post ->
                try {
                    val postData = post.jsonObject["data"]?.jsonObject
                    val title = postData?.get("title")?.jsonPrimitive?.content
                    val url = postData?.get("permalink")?.jsonPrimitive?.content
                    val score = postData?.get("score")?.jsonPrimitive?.int
                    val numComments = postData?.get("num_comments")?.jsonPrimitive?.int

                    if (title != null && url != null && score != null) {
                        "• $title\n  ↑ $score points | 💬 $numComments comments\n  https://reddit.com$url"
                    } else null
                } catch (e: Exception) {
                    println("Error parsing post: ${e.message}")
                    null
                }
            }.joinToString("\n\n")

            return "Top 10 posts this week from r/$subreddit:\n\n$formattedPosts"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error fetching posts from Reddit: ${e.message}"
        }
    }

    /**
     * Handle messages sent to the Hi Town group.
     */
    suspend fun message(token: String, body: MessageBotBody): MessageBotResponse {
        try {
            val message = body.message ?: return MessageBotResponse(
                success = false,
                note = "No message provided"
            )

            if (!message.startsWith("!reddit")) {
                return MessageBotResponse(
                    success = false,
                    note = "Not a Reddit command"
                )
            }

            val parts = message.split(" ")
            val subreddit = if (parts.size > 1) {
                parts[1].trim()
            } else {
                "programming" // Default subreddit
            }

            val redditContent = fetchRedditPosts(subreddit)

            return MessageBotResponse(
                success = true,
                actions = listOf(
                    BotAction(
                        message = redditContent
                    )
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return MessageBotResponse(
                success = false,
                note = "Error processing message: ${e.message}"
            )
        }
    }

    // Dummy methods to satisfy the interface
    fun validateInstall(secret: String?): Boolean = true
    fun install(token: String, body: InstallBotBody) {}
    fun reinstall(token: String, config: List<BotConfigValue>) {}
    fun uninstall(token: String) {}
}
