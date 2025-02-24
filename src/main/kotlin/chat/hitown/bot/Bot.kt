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

/**
 * Bot instance.
 */
val bot = Bot()

class Bot {
    /**
     * Bot details as shown in Hi Town.
     */
    val details = BotDetails(
        /**
         * Bot name.
         */
        name = "Hi Town Bot",
        /**
         * Bot description.
         */
        description = "A Hi Town bot",
        /**
         * Keywords that will cause a Hi Town group message to be sent to the bot.
         *
         * Leaving this empty means the bot receives all messages sent to the group.
         */
        keywords = emptyList(),
        /**
         * Available configuration options for the bot (optional).
         */
        config = emptyList()
    )

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

    }

    /**
     * Handle the bot being uninstalled from a Hi Town group.
     */
    suspend fun message(
        /**
         * The unique token associated with the Hi Town group.
         */
        token: String,
        /**
         * Details about the message that was sent to the Hi Town group.
         */
        body: MessageBotBody,
    ): MessageBotResponse {
        /**
         * Tell Hi Town if the message was handled successfully or not.
         *
         * See `Models.kt` for details.
         */
        return MessageBotResponse(
            success = false,
            note = "The bot is not configured to respond to messages.",
        )
    }
}
