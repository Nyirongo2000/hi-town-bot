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
    val config: List<BotConfigValue>,
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
        loadState()
    }

    private fun loadState() {
        try {
            if (saveFile.exists()) {
                val state = Json.decodeFromString<Map<String, GroupInstall>>(saveFile.readText())
                groupInstalls.clear()
                groupInstalls.putAll(state)
            }
        } catch (e: Exception) {
            println("Error loading state: ${e.message}")
        }
    }

    private fun saveState() {
        try {
            saveFile.writeText(Json.encodeToString(groupInstalls))
        } catch (e: Exception) {
            println("Error saving state: ${e.message}")
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

    fun validateInstall(secret: String?): Boolean {
        println("Validating install secret: $secret")
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
            groupInstalls[token] = GroupInstall(
                groupId = body.groupId,
                groupName = body.groupName,
                webhook = body.webhook,
                config = body.config ?: emptyList()
            )
            saveState()
            println("Bot installed successfully")
        } catch (e: Exception) {
            println("Error installing bot: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    fun reinstall(token: String, config: List<BotConfigValue>) {
        groupInstalls[token]?.let { install ->
            groupInstalls[token] = install.copy(config = config)
            saveState()
        }
    }

    fun uninstall(token: String) {
        groupInstalls.remove(token)
        saveState()
    }

    fun pause(token: String) {
        groupInstalls[token]?.let { install ->
            groupInstalls[token] = install.copy(isPaused = true)
            saveState()
        }
    }

    fun resume(token: String) {
        groupInstalls[token]?.let { install ->
            groupInstalls[token] = install.copy(isPaused = false)
            saveState()
        }
    }

    /**
     * Handle messages sent to the Hi Town group.
     */
    suspend fun message(token: String, body: MessageBotBody): MessageBotResponse {
        try {
            println("=== Message Handler Start ===")
            println("Token: $token")
            println("Message Body: $body")
            
            val groupInstall = groupInstalls[token]
            println("Group Install: $groupInstall")
            
            if (groupInstall == null) {
                println("Bot not installed in this group")
                return MessageBotResponse(
                    success = false,
                    note = "The bot is not installed in this group. Please install it first."
                )
            }

            if (groupInstall.isPaused) {
                println("Bot is paused in this group")
                return MessageBotResponse(
                    success = false,
                    note = "The bot is paused in this group."
                )
            }

            val message = body.message
            println("Received message: $message")
            
            if (message == null) {
                println("No message provided")
                return MessageBotResponse(
                    success = false,
                    note = "No message provided"
                )
            }

            if (!message.startsWith("!reddit")) {
                println("Not a Reddit command: $message")
                return MessageBotResponse(
                    success = false,
                    note = "Not a Reddit command. Use !reddit to get posts."
                )
            }

            val parts = message.split(" ")
            val subreddit = if (parts.size > 1) {
                parts[1].trim()
            } else {
                groupInstall.config.find { it.key == "subreddit" }?.value ?: "programming"
            }
            println("Using subreddit: $subreddit")

            println("Fetching Reddit posts...")
            val redditContent = fetchRedditPosts(subreddit)
            println("Reddit content fetched successfully")

            val response = MessageBotResponse(
                success = true,
                actions = listOf(
                    BotAction(
                        message = redditContent
                    )
                )
            )
            println("=== Message Handler End ===")
            return response
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error in message handler: ${e.message}")
            println("Stack trace: ${e.stackTraceToString()}")
            return MessageBotResponse(
                success = false,
                note = "Error processing message: ${e.message}"
            )
        }
    }

    private suspend fun fetchRedditPosts(subreddit: String): String {
        try {
            println("=== Reddit API Request Start ===")
            println("Subreddit: $subreddit")
            
            val url = "https://www.reddit.com/r/$subreddit/top.json?limit=10&t=week"
            println("Request URL: $url")
            
            val response = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                header("Accept", "application/json")
                header("Accept-Language", "en-US,en;q=0.9")
                header("Connection", "keep-alive")
                header("Cache-Control", "no-cache")
            }

            println("Response Status: ${response.status.value}")
            println("Response Headers: ${response.headers}")

            if (response.status.value !in 200..299) {
                println("Reddit API Error: ${response.status.value}")
                println("Response Headers: ${response.headers}")
                return "Error: Reddit API returned status ${response.status.value}. Please try again later."
            }

            val responseText = response.bodyAsText()
            println("Response Body Length: ${responseText.length}")
            
            val jsonResponse = Json.parseToJsonElement(responseText)
            val data = jsonResponse.jsonObject["data"]?.jsonObject
            val posts = data?.get("children")?.jsonArray

            if (posts == null || posts.isEmpty()) {
                println("No posts found in subreddit")
                return "No posts found in r/$subreddit"
            }

            println("Found ${posts.size} posts")
            val formattedPosts = posts.mapNotNull { post ->
                try {
                    val postData = post.jsonObject["data"]?.jsonObject
                    val title = postData?.get("title")?.jsonPrimitive?.content
                    val url = postData?.get("permalink")?.jsonPrimitive?.content
                    val score = postData?.get("score")?.jsonPrimitive?.int
                    val numComments = postData?.get("num_comments")?.jsonPrimitive?.int
                    val author = postData?.get("author")?.jsonPrimitive?.content
                    val created = postData?.get("created_utc")?.jsonPrimitive?.long

                    if (title != null && url != null && score != null) {
                        val timeAgo = if (created != null) {
                            val now = System.currentTimeMillis() / 1000
                            val diff = now - created
                            when {
                                diff < 60 -> "just now"
                                diff < 3600 -> "${diff / 60}m ago"
                                diff < 86400 -> "${diff / 3600}h ago"
                                else -> "${diff / 86400}d ago"
                            }
                        } else ""
                        
                        "• $title\n  ↑ $score points | 💬 $numComments comments | 👤 $author | ⏰ $timeAgo\n  https://reddit.com$url"
                    } else null
                } catch (e: Exception) {
                    println("Error parsing post: ${e.message}")
                    null
                }
            }.joinToString("\n\n")

            val result = "Top 10 posts this week from r/$subreddit:\n\n$formattedPosts"
            println("=== Reddit API Request End ===")
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error in fetchRedditPosts: ${e.message}")
            println("Stack trace: ${e.stackTraceToString()}")
            return "Error fetching posts from Reddit: ${e.message}. Please try again later."
        }
    }
}
