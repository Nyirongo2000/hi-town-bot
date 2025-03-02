package chat.hitown.bot

import chat.hitown.bot.plugins.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class BotTest {
    private val testBot = Bot()
    private val testToken = "test-token-123"
    private val testGroupId = "test-group-123"
    private val testGroupName = "Test Group"
    private val testWebhook = "https://api.hitown.chat/webhook/123"

    @Test
    fun `test bot details`() {
        val details = testBot.details
        assertNotNull(details.name)
        assertNotNull(details.description)
        assertTrue(details.keywords?.contains("!reddit") == true)
        assertTrue(details.config?.any { it.key == "subreddit" } == true)
    }

    @Test
    fun `test bot installation`() = runBlocking {
        val config = listOf(
            BotConfigValue(
                key = "subreddit",
                value = "kotlin"
            )
        )

        val installBody = InstallBotBody(
            groupId = testGroupId,
            groupName = testGroupName,
            webhook = testWebhook,
            config = config
        )

        // Test installation
        testBot.install(testToken, installBody)

        // Test message with configured subreddit
        val messageBody = MessageBotBody(
            message = "!reddit",
            person = Person(id = "user123", name = "Test User")
        )

        val response = testBot.message(testToken, messageBody)
        assertTrue(response.success == true)
        assertNotNull(response.actions)
        assertTrue(response.actions?.isNotEmpty() == true)
        assertTrue(response.actions?.first()?.message?.contains("r/kotlin") == true)
    }

    @Test
    fun `test bot pause and resume`() = runBlocking {
        // Test pause
        testBot.pause(testToken)

        val messageBody = MessageBotBody(
            message = "!reddit programming",
            person = Person(id = "user123", name = "Test User")
        )

        var response = testBot.message(testToken, messageBody)
        assertTrue(response.success == false)
        assertTrue(response.note?.contains("paused") == true)

        // Test resume
        testBot.resume(testToken)
        response = testBot.message(testToken, messageBody)
        assertTrue(response.success == true)
        assertNotNull(response.actions)
    }

    @Test
    fun `test bot reinstall with new config`() = runBlocking {
        val newConfig = listOf(
            BotConfigValue(
                key = "subreddit",
                value = "java"
            )
        )

        testBot.reinstall(testToken, newConfig)

        val messageBody = MessageBotBody(
            message = "!reddit",
            person = Person(id = "user123", name = "Test User")
        )

        val response = testBot.message(testToken, messageBody)
        assertTrue(response.success == true)
        assertTrue(response.actions?.first()?.message?.contains("r/java") == true)
    }

    @Test
    fun `test bot uninstall`() = runBlocking {
        testBot.uninstall(testToken)

        // After uninstall, the bot should use default subreddit
        val messageBody = MessageBotBody(
            message = "!reddit",
            person = Person(id = "user123", name = "Test User")
        )

        val response = testBot.message(testToken, messageBody)
        assertTrue(response.success == true)
        assertTrue(response.actions?.first()?.message?.contains("r/programming") == true)
    }

    @Test
    fun `test invalid message handling`() = runBlocking {
        // Test null message
        var response = testBot.message(testToken, MessageBotBody(message = null))
        assertTrue(response.success == false)
        assertTrue(response.note?.contains("No message") == true)

        // Test non-reddit command
        response = testBot.message(testToken, MessageBotBody(message = "!other command"))
        assertTrue(response.success == false)
        assertTrue(response.note?.contains("Not a Reddit command") == true)
    }
} 