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
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.*

/**
 * Bot instance.
 */
val bot = Bot()

class Bot {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    /**
     * Bot details as shown in Hi Town.
     */
    val details = BotDetails(
        /**
         * Bot name.
         */
        name = " Hi-Town Reddit Bot",
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

    private val groupConfigs = mutableMapOf<String, List<BotConfigValue>>()
    private val pausedGroups = mutableSetOf<String>()  // Track paused groups

    /**
     * Validate the bot secret (optional).
     *
     * Returning false will not allow the bot to be installed.
     */
    suspend fun validateInstall(
        /**
         * The secret as configured by the bot owner when creating the bot in Hi Town.
         */
        secret: String?,
    ): Boolean = true

    /**
     * Handle the bot being installed in a Hi Town group.
     */
    suspend fun install(
        /**
         * The unique token associated with the Hi Town group.
         */
        token: String,
        /**
         * Install information and configuration.
         *
         * See `Models.kt` for details.
         */
        body: InstallBotBody,
    ) {
        body.config?.let { config ->
            groupConfigs[token] = config
        }
    }

    /**
     * Handle the bot being reinstalled in a Hi Town group due to config changes made by the group host.
     */
    suspend fun reinstall(
        /**
         * The unique token associated with the Hi Town group.
         */
        token: String,
        /**
         * The updated bot config.
         */
        config: List<BotConfigValue>,
    ) {
        groupConfigs[token] = config
    }

    /**
     * Handle the bot being uninstalled from a Hi Town group.
     */
    suspend fun uninstall(
        /**
         * The unique token associated with the Hi Town group.
         */
        token: String,
    ) {
        groupConfigs.remove(token)
    }

    /**
     * Handle the bot being paused in a Hi Town group.
     */
    suspend fun pause(
        /**
         * The unique token associated with the Hi Town group.
         */
        token: String,
    ) {
        pausedGroups.add(token)
    }

    /**
     * Handle the bot being resumed in a Hi Town group.
     */
    suspend fun resume(
        /**
         * The unique token associated with the Hi Town group.
         */
        token: String,
    ) {
        pausedGroups.remove(token)
    }

    private suspend fun fetchRedditPosts(subreddit: String): String {
        try {
            // Using 'top' instead of 'hot' and specifying 't=week' for weekly top posts
            val response = client.get("https://www.reddit.com/r/$subreddit/top.json?limit=10&t=week") {
                header("User-Agent", "HiTownBot/1.0")
            }

            val jsonResponse = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val posts = jsonResponse["data"]?.jsonObject?.get("children")?.jsonArray

            if (posts == null || posts.isEmpty()) {
                return "No posts found in r/$subreddit"
            }

            val formattedPosts = posts.mapNotNull { post ->
                val data = post.jsonObject["data"]?.jsonObject
                val title = data?.get("title")?.jsonPrimitive?.content
                val url = data?.get("permalink")?.jsonPrimitive?.content
                val score = data?.get("score")?.jsonPrimitive?.int
                val numComments = data?.get("num_comments")?.jsonPrimitive?.int

                if (title != null && url != null && score != null) {
                    "• $title\n  ↑ $score points | 💬 $numComments comments\n  https://reddit.com$url"
                } else null
            }.joinToString("\n\n")

            return "Top 10 posts this week from r/$subreddit:\n\n$formattedPosts"
        } catch (e: Exception) {
            return "Error fetching posts from Reddit: ${e.message}"
        }
    }

    /**
     * Handle messages sent to the Hi Town group.
     */
    suspend fun message(
        token: String,
        body: MessageBotBody,
    ): MessageBotResponse {
        // Check if bot is paused for this group
        if (pausedGroups.contains(token)) {
            return MessageBotResponse(
                success = false,
                note = "Bot is currently paused in this group"
            )
        }

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
            // Use default subreddit from config if available
            groupConfigs[token]?.find { it.key == "subreddit" }?.value ?: "programming"
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
    }
}
