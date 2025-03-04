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
import io.ktor.client.request.forms.*
import io.ktor.http.*
import java.util.Base64
import kotlinx.serialization.json.jsonObject

private const val INSTALL_SECRET = "hitownbot"
private const val REDDIT_API_RATE_LIMIT = 1000L // 1 second between requests
private const val REDDIT_TOKEN_REFRESH_BUFFER = 300000L // Refresh token 5 minutes before expiry

@Serializable
data class GroupInstall(
    val groupId: String,
    val groupName: String,
    val webhook: String,
    val config: List<BotConfigValue> = emptyList(),
    val isPaused: Boolean = false
)

@Serializable
private data class RedditToken(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val scope: String
)

@Serializable
private data class RedditError(
    val error: String,
    val message: String? = null
)

/**
 * Bot instance.
 */
val bot = Bot()

class Bot {
    private val redditClientId = System.getenv("REDDIT_CLIENT_ID") ?: "OuXEbIhZbHzPJYC5W2m1ew"
    private val redditClientSecret = System.getenv("REDDIT_CLIENT_SECRET") ?: "KURAkvMgqinxDXjudNMOyPgvekj_Bw"
    private val redditUserAgent = "weekly-summarizer/1.0 (by /u/AcrobaticGroup1639)"
    private val redditRedirectUri = System.getenv("REDDIT_REDIRECT_URI") ?: "https://hi-town-bot-1.onrender.com"
    
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
    private var redditAccessToken: String? = null
    private var tokenExpirationTime: Long = 0
    private var lastRequestTime: Long = 0

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

            println("\n=== Fetching Reddit Posts ===")
            val redditContent = fetchRedditPosts(subreddit)
            println("\n=== Reddit Content ===")
            println(redditContent)
            println("=== End Reddit Content ===\n")

            val response = MessageBotResponse(
                success = true,
                actions = listOf(
                    BotAction(
                        message = redditContent
                    )
                )
            )
            println("=== Message Handler End ===\n")
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

    private suspend fun getRedditAccessToken(): String {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Check if current token is still valid (with buffer time for refresh)
            if (redditAccessToken != null && currentTime < (tokenExpirationTime - REDDIT_TOKEN_REFRESH_BUFFER)) {
                return redditAccessToken!!
            }

            println("=== Reddit OAuth2 Token Request ===")
            println("Previous token expired or close to expiry")
            
            // Respect rate limiting
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < REDDIT_API_RATE_LIMIT) {
                delay(REDDIT_API_RATE_LIMIT - timeSinceLastRequest)
            }
            
            val auth = Base64.getEncoder().encodeToString("$redditClientId:$redditClientSecret".toByteArray())
            
            val response = client.post("https://www.reddit.com/api/v1/access_token") {
                header("Authorization", "Basic $auth")
                header("User-Agent", redditUserAgent)
                
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "client_credentials")
                    append("redirect_uri", redditRedirectUri)
                }))
            }

            lastRequestTime = System.currentTimeMillis()

            if (response.status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                try {
                    val error = Json.decodeFromString<RedditError>(errorBody)
                    throw Exception("Reddit OAuth2 error: ${error.error} - ${error.message}")
                } catch (e: Exception) {
                    throw Exception("Failed to get Reddit access token: ${response.status.value} - $errorBody")
                }
            }

            val tokenResponse = Json.decodeFromString<RedditToken>(response.bodyAsText())
            redditAccessToken = tokenResponse.access_token
            tokenExpirationTime = System.currentTimeMillis() + (tokenResponse.expires_in * 1000)
            
            println("Successfully obtained new Reddit access token")
            println("Token expires in: ${tokenResponse.expires_in} seconds")
            println("Scopes granted: ${tokenResponse.scope}")
            
            return redditAccessToken!!
        } catch (e: Exception) {
            println("Error getting Reddit access token: ${e.message}")
            println("Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }

    private suspend fun fetchRedditPosts(subreddit: String): String {
        try {
            println("=== Reddit API Request Start ===")
            println("Subreddit: $subreddit")
            
            val accessToken = try {
                getRedditAccessToken()
            } catch (e: Exception) {
                println("Failed to get access token: ${e.message}")
                return "Error: Unable to authenticate with Reddit API. Please try again later."
            }
            
            val url = "https://oauth.reddit.com/r/$subreddit/top.json?limit=10&t=week"
            println("Request URL: $url")
            
            // Respect rate limiting
            val currentTime = System.currentTimeMillis()
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < REDDIT_API_RATE_LIMIT) {
                delay(REDDIT_API_RATE_LIMIT - timeSinceLastRequest)
            }
            
            val response = client.get(url) {
                header("Authorization", "Bearer $accessToken")
                header("User-Agent", redditUserAgent)
                header("Accept", "application/json")
            }

            lastRequestTime = System.currentTimeMillis()
            println("Response Status: ${response.status.value}")

            when (response.status.value) {
                200 -> return processRedditResponse(response.bodyAsText(), subreddit)
                401, 403 -> {
                    // Clear token and retry once with fresh token
                    redditAccessToken = null
                    tokenExpirationTime = 0
                    println("Auth failed, retrying with new token...")
                    
                    val newToken = getRedditAccessToken()
                    
                    // Respect rate limiting before retry
                    delay(REDDIT_API_RATE_LIMIT)
                    
                    val retryResponse = client.get(url) {
                        header("Authorization", "Bearer $newToken")
                        header("User-Agent", redditUserAgent)
                        header("Accept", "application/json")
                    }

                    lastRequestTime = System.currentTimeMillis()

                    if (retryResponse.status.value == 200) {
                        return processRedditResponse(retryResponse.bodyAsText(), subreddit)
                    }
                    
                    // Try to parse error response
                    val errorBody = retryResponse.bodyAsText()
                    try {
                        val error = Json.decodeFromString<RedditError>(errorBody)
                        return "Error: ${error.message ?: error.error}"
                    } catch (e: Exception) {
                        return "Error: Unable to access r/$subreddit. The subreddit might be private or restricted."
                    }
                }
                404 -> return "Error: Subreddit r/$subreddit not found."
                429 -> return "Error: Rate limit exceeded. Please try again later."
                else -> {
                    val errorBody = response.bodyAsText()
                    try {
                        val error = Json.decodeFromString<RedditError>(errorBody)
                        return "Error: ${error.message ?: error.error}"
                    } catch (e: Exception) {
                        return "Error: Reddit API returned status ${response.status.value}. Please try again later."
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error in fetchRedditPosts: ${e.message}")
            return "Error fetching posts from Reddit: ${e.message}. Please try again later."
        }
    }

    private fun processRedditResponse(responseText: String, subreddit: String): String {
        try {
            println("Processing Reddit response...")
            println("Response Body Length: ${responseText.length}")
            
            val jsonResponse = Json.parseToJsonElement(responseText)
            println("JSON Response Type: ${jsonResponse::class.simpleName}")
            
            val data = jsonResponse.jsonObject["data"]?.jsonObject
            if (data == null) {
                println("Data object is null in response")
                return "Error: Invalid response format from Reddit API"
            }
            
            val posts = data.get("children")?.jsonArray
            if (posts == null || posts.isEmpty()) {
                println("No posts found in response")
                return "No posts found in the subreddit"
            }

            println("Found ${posts.size} posts")
            val formattedPosts = posts.mapNotNull { post ->
                try {
                    val postData = post.jsonObject["data"]?.jsonObject
                    if (postData == null) {
                        println("Post data is null")
                        return@mapNotNull null
                    }

                    // Log all available fields for debugging
                    println("\nPost Data Fields:")
                    postData.keys.forEach { key ->
                        val value = postData[key]
                        println("$key: $value")
                    }

                    // Safely extract values with null checks
                    val title = postData.get("title")?.jsonPrimitive?.content
                    val url = postData.get("permalink")?.jsonPrimitive?.content
                    val score = postData.get("score")?.jsonPrimitive?.content?.toIntOrNull()
                    val numComments = postData.get("num_comments")?.jsonPrimitive?.content?.toIntOrNull()
                    val author = postData.get("author")?.jsonPrimitive?.content
                    val created = postData.get("created_utc")?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong()
                    val selftext = postData.get("selftext")?.jsonPrimitive?.content
                    val isSelf = postData.get("is_self")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val url_overridden_by_dest = postData.get("url_overridden_by_dest")?.jsonPrimitive?.content
                    val subreddit_name_prefixed = postData.get("subreddit_name_prefixed")?.jsonPrimitive?.content

                    if (title == null || url == null || score == null) {
                        println("Missing required fields: title=${title != null}, url=${url != null}, score=${score != null}")
                        return@mapNotNull null
                    }

                    val timeAgo = if (created != null) {
                        val now = System.currentTimeMillis() / 1000
                        val diff = now - created
                        when {
                            diff < 60 -> "just now"
                            diff < 3600 -> "${diff / 60}m ago"
                            diff < 86400 -> "${diff / 3600}h ago"
                            else -> "${diff / 86400}d ago"
                        }
                    } else "unknown time"

                    // Format the post with more details and better visibility
                    val formattedPost = buildString {
                        // Title with emoji
                        append("📌 $title\n")
                        
                        // Stats line with emojis
                        append("📊 Stats: ")
                        append("↑ $score points | ")
                        append("💬 ${numComments ?: 0} comments | ")
                        append("👤 ${author ?: "deleted"} | ")
                        append("⏰ $timeAgo\n")
                        
                        // Content preview for self posts
                        if (isSelf && !selftext.isNullOrBlank()) {
                            append("📝 Content:\n")
                            append("${selftext.take(200)}${if (selftext.length > 200) "..." else ""}\n")
                        }
                        
                        // Image preview for link posts
                        if (!isSelf && url_overridden_by_dest != null) {
                            append("🖼️ Image: $url_overridden_by_dest\n")
                        }
                        
                        // Link to post
                        append("🔗 https://reddit.com$url")
                    }
                    
                    println("Successfully formatted post: $title")
                    formattedPost
                } catch (e: Exception) {
                    println("Error parsing post: ${e.message}")
                    // Try to get at least the title and link
                    try {
                        val postData = post.jsonObject["data"]?.jsonObject
                        val title = postData?.get("title")?.jsonPrimitive?.content
                        val url = postData?.get("permalink")?.jsonPrimitive?.content
                        if (title != null && url != null) {
                            return@mapNotNull "📌 $title\n🔗 https://reddit.com$url"
                        }
                    } catch (e2: Exception) {
                        println("Failed to get basic post info: ${e2.message}")
                    }
                    null
                }
            }

            if (formattedPosts.isEmpty()) {
                println("No posts were successfully formatted")
                // Return raw JSON for debugging
                return "Error: Unable to format posts. Raw response:\n${responseText.take(1000)}..."
            }

            println("Successfully formatted ${formattedPosts.size} posts")
            
            // Create a more visible header
            val header = buildString {
                append("🔥 TOP POSTS FROM r/$subreddit 🔥\n")
                append("📅 This Week's Best\n")
                append("=".repeat(30) + "\n\n")
            }
            
            return header + formattedPosts.joinToString("\n\n" + "=".repeat(30) + "\n\n")
        } catch (e: Exception) {
            println("Error processing response: ${e.message}")
            e.printStackTrace()
            // Return raw JSON for debugging
            return "Error processing Reddit response. Raw data:\n${responseText.take(1000)}..."
        }
    }
}
