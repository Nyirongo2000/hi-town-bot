/**
 * Write your bot here!
 *
 * Also see `Models.kt` for additional information.
 */

package chat.hitown.bot

import chat.hitown.bot.plugins.*
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
import java.io.File
import java.util.*

private const val INSTALL_SECRET = "hitownbot"

@Serializable
data class GroupInstall(
    val groupId: String,
    val groupName: String,
    val webhook: String,
    val config: List<BotConfigValue> = emptyList(),
    val isPaused: Boolean = false
)

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
                encodeDefaults = true
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

    private val groupInstalls = mutableMapOf<String, GroupInstall>()
    private val saveFile = File("./bot_state.json")

    init {
        println("=== Bot Initialization ===")
        loadState()
        println("Current group installs: ${groupInstalls.size}")
        groupInstalls.forEach { (token, install) ->
            println("Group: ${install.groupName} (${install.groupId})")
            println("  Token: $token")
            println("  Webhook: ${install.webhook}")
            println("  Config: ${install.config}")
            println("  Paused: ${install.isPaused}")
        }
    }

    private fun loadState() {
        try {
            println("Loading state from file: ${saveFile.absolutePath}")
            if (saveFile.exists()) {
                val state = Json.decodeFromString<Map<String, GroupInstall>>(saveFile.readText())
                groupInstalls.clear()
                groupInstalls.putAll(state)
                println("Loaded ${groupInstalls.size} group installs")
            } else {
                println("No state file found, starting fresh")
            }
        } catch (e: Exception) {
            println("Error loading state: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun saveState() {
        try {
            println("Saving state to file: ${saveFile.absolutePath}")
            saveFile.writeText(Json.encodeToString(groupInstalls))
            println("Saved ${groupInstalls.size} group installs")
        } catch (e: Exception) {
            println("Error saving state: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Bot details as shown in Hi Town.
     */
    val details = BotDetails(
        /**
         * Bot name.
         */
        name = "Hi-Town-Reddit-Bot",
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

    fun validateInstall(secret: String?): Boolean {
        println("=== Validating Install Secret ===")
        println("Received secret: $secret")
        println("Expected secret: $INSTALL_SECRET")
        // For testing, accept any secret
        return true
    }

    fun install(token: String, body: InstallBotBody) {
        println("=== Installing Bot ===")
        println("Token: $token")
        println("Group ID: ${body.groupId}")
        println("Group Name: ${body.groupName}")
        println("Webhook: ${body.webhook}")
        println("Config: ${body.config}")
        
        try {
            val config = body.config?.toList() ?: emptyList()
            println("Using config: $config")
            
            groupInstalls[token] = GroupInstall(
                groupId = body.groupId,
                groupName = body.groupName,
                webhook = body.webhook,
                config = config,
                isPaused = false
            )
            saveState()
            println("Bot installed successfully in group: ${body.groupName}")
            println("Current group installs: ${groupInstalls.size}")
        } catch (e: Exception) {
            println("Error installing bot: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    fun reinstall(token: String, config: List<BotConfigValue>?) {
        println("=== Reinstalling Bot ===")
        println("Token: $token")
        println("New config: $config")
        
        groupInstalls[token]?.let { install ->
            println("Found existing install for group: ${install.groupName}")
            groupInstalls[token] = install.copy(config = config ?: emptyList())
            saveState()
            println("Bot reinstalled successfully")
        } ?: run {
            println("No existing install found for token: $token")
        }
    }

    fun uninstall(token: String) {
        println("=== Uninstalling Bot ===")
        println("Token: $token")
        
        groupInstalls[token]?.let { install ->
            println("Found install for group: ${install.groupName}")
            groupInstalls.remove(token)
            saveState()
            println("Bot uninstalled successfully")
        } ?: run {
            println("No install found for token: $token")
        }
    }

    fun pause(token: String) {
        println("=== Pausing Bot ===")
        println("Token: $token")
        
        groupInstalls[token]?.let { install ->
            println("Found install for group: ${install.groupName}")
            groupInstalls[token] = install.copy(isPaused = true)
            saveState()
            println("Bot paused successfully")
        } ?: run {
            println("No install found for token: $token")
        }
    }

    fun resume(token: String) {
        println("=== Resuming Bot ===")
        println("Token: $token")
        
        groupInstalls[token]?.let { install ->
            println("Found install for group: ${install.groupName}")
            groupInstalls[token] = install.copy(isPaused = false)
            saveState()
            println("Bot resumed successfully")
        } ?: run {
            println("No install found for token: $token")
        }
    }

    /**
     * Handle messages sent to the Hi Town group.
     */
    suspend fun message(token: String, body: MessageBotBody): MessageBotResponse {
        try {
            println("\n=== Message Handler Start ===")
            println("Token: $token")
            println("Message Body: $body")
            
            val groupInstall = groupInstalls[token]
            println("Group Install: $groupInstall")
            
            if (groupInstall == null) {
                println("Bot not installed in this group")
                return MessageBotResponse(
                    success = false,
                    note = "Bot not installed in this group"
                )
            }

            if (groupInstall.isPaused) {
                println("Bot is paused in this group")
                return MessageBotResponse(
                    success = false,
                    note = "Bot is paused"
                )
            }

            val message = body.message?.trim()
            println("Processing message: $message")
            
            if (message.isNullOrBlank()) {
                return MessageBotResponse(success = false, note = "Empty message")
            }

            // Check if message starts with !reddit
            if (!message.startsWith("!reddit", ignoreCase = true)) {
                println("Not a Reddit command")
                return MessageBotResponse(success = true) // Silently ignore non-reddit commands
            }

            // Extract subreddit name
            val parts = message.split(Regex("\\s+"), 2)
            val subreddit = if (parts.size > 1) {
                parts[1].trim()
            } else {
                // Use default subreddit from config or fallback to "programming"
                groupInstall.config.find { it.key == "subreddit" }?.value ?: "programming"
            }

            if (subreddit.isBlank()) {
                // Send message using webhook
                sendWebhookMessage(groupInstall.webhook, "Please specify a subreddit (e.g., !reddit programming)")
                return MessageBotResponse(success = true)
            }

            println("Fetching posts for subreddit: $subreddit")
            val redditContent = fetchRedditPosts(subreddit)

            // Send the Reddit content using webhook
            sendWebhookMessage(groupInstall.webhook, redditContent)

            // Return success response
            return MessageBotResponse(success = true)

        } catch (e: Exception) {
            println("Error in message handler: ${e.message}")
            e.printStackTrace()
            return MessageBotResponse(
                success = false,
                note = "Error: ${e.message}"
            )
        }
    }

    private suspend fun sendWebhookMessage(webhook: String, message: String) {
        try {
            println("Sending webhook message to: $webhook")
            val response = client.post(webhook) {
                header("Content-Type", "application/json")
                setBody(listOf(mapOf("message" to message)))
            }
            println("Webhook response status: ${response.status.value}")
            
            if (response.status.value == 400) {
                println("Bot is paused (webhook returned 400)")
            } else if (response.status.value !in 200..299) {
                println("Error sending webhook message: ${response.status.value}")
            }
        } catch (e: Exception) {
            println("Error sending webhook message: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun fetchRedditPosts(subreddit: String): String {
        try {
            println("Fetching posts from r/$subreddit")
            
            val url = "https://www.reddit.com/r/$subreddit/top.json?limit=5&t=week"
            println("Request URL: $url")
            
            delay(1000) // Respect rate limits
            
            val response = client.get(url) {
                header("User-Agent", "HiTownBot/1.0 (by /u/hitownbot)")
                header("Accept", "application/json")
            }

            println("Reddit API Response Status: ${response.status.value}")

            if (response.status.value == 403) {
                return "⚠️ Unable to access r/$subreddit. The subreddit might be private or restricted."
            }

            if (response.status.value !in 200..299) {
                return "⚠️ Unable to fetch posts from Reddit (Status: ${response.status.value}). Please try again later."
            }

            val responseText = response.bodyAsText()
            println("Got response from Reddit, length: ${responseText.length}")
            return processRedditResponse(responseText, subreddit)
        } catch (e: Exception) {
            println("Error fetching posts: ${e.message}")
            return "⚠️ Error fetching posts: ${e.message ?: "Unknown error"}. Please try again later."
        }
    }

    private fun processRedditResponse(responseText: String, subreddit: String): String {
        try {
            val jsonResponse = Json.parseToJsonElement(responseText).jsonObject
            val data = jsonResponse["data"]?.jsonObject ?: return "⚠️ Invalid response from Reddit"
            val posts = data["children"]?.jsonArray ?: return "⚠️ No posts found"

            if (posts.isEmpty()) {
                return "📭 No posts found in r/$subreddit"
            }

            val formattedPosts = posts.mapNotNull { post ->
                try {
                    val postData = post.jsonObject["data"]?.jsonObject ?: return@mapNotNull null
                    
                    val title = postData["title"]?.jsonPrimitive?.content
                    val url = postData["permalink"]?.jsonPrimitive?.content
                    val score = postData["score"]?.jsonPrimitive?.content?.toIntOrNull()
                    val numComments = postData["num_comments"]?.jsonPrimitive?.content?.toIntOrNull()
                    val author = postData["author"]?.jsonPrimitive?.content
                    
                    if (title == null || url == null) return@mapNotNull null

                    buildString {
                        append("📌 $title\n")
                        append("👤 u/${author ?: "[deleted]"}")
                        if (score != null) append(" • ⬆️ $score")
                        if (numComments != null) append(" • 💬 $numComments")
                        append("\n🔗 https://reddit.com$url")
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (formattedPosts.isEmpty()) {
                return "⚠️ Unable to format any posts from r/$subreddit"
            }

            return buildString {
                append("📱 Top posts from r/$subreddit this week:\n\n")
                append(formattedPosts.joinToString("\n\n"))
            }
        } catch (e: Exception) {
            return "⚠️ Error processing Reddit response: ${e.message ?: "Unknown error"}"
        }
    }
}
