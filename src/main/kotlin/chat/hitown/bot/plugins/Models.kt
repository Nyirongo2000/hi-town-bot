/**
 * API models as defined in the Hi Town bot specification.
 */

package chat.hitown.bot.plugins

import kotlinx.serialization.Serializable

/**
 * A bot config field value.
 */
@Serializable
data class BotConfigValue(
    /**
     * The config field key.
     */
    var key: String? = null,
    /**
     * The user supplied config value.
     */
    var value: String? = null,
)

/**
 * An available bot configuration to show the user.
 */
@Serializable
data class BotConfigField(
    /**
     * The config field key.
     */
    var key: String? = null,
    /**
     * The config field name as shown to the user.
     */
    var label: String? = null,
    /**
     * The input placeholder as shown to the user.
     */
    var placeholder: String? = null,
    /**
     * The configuration type.
     *
     * Supported types:
     *  - "string"
     */
    var type: String? = null,
    /**
     * Define the field as required or optional.
     */
    var required: Boolean? = null,
)

/**
 * Bot details as shown to the user.
 */
@Serializable
data class BotDetails(
    /**
     * Bot name.
     */
    val name: String? = null,
    /**
     * Bot description.
     */
    val description: String? = null,
    /**
     * Keywords that will cause a Hi Town group message to be sent to the bot.
     *
     * Leaving this empty means the bot receives all messages sent to the group.
     */
    val keywords: List<String>? = null,
    /**
     * Available configuration options for the bot (optional).
     */
    val config: List<BotConfigField>? = null,
)

/**
 * Bot install response.
 */
@Serializable
data class InstallBotResponse(
    /**
     * The unique token associated with the Hi Town group.
     *
     * This will be sent by Hi Town to determine the install that is being reinstalled or uninstalled.
     */
    val token: String,
)

/**
 * Bot install request.
 */
@Serializable
data class InstallBotBody(
    /**
     * The group the bot is being installed in.
     */
    val groupId: String,
    /**
     * The name of the group.
     */
    val groupName: String,
    /**
     * The complete webhook URL that can be used to send messages to the group at any time.
     */
    val webhook: String,
    /**
     * The configuration supplied by the user.
     */
    val config: List<BotConfigValue>? = null,
    /**
     * The secret as defined bt the bot owner in Hi Town.
     */
    val secret: String? = null,
)

/**
 * But reinstall request.
 */
@Serializable
data class ReinstallBotBody(
    /**
     * The updated configuration for the install.
     */
    val config: List<BotConfigValue>? = null,
)

/**
 * The message result sent back to Hi Town.
 */
@Serializable
data class MessageBotResponse(
    /**
     * The message was handled successfully or not.
     *
     * This shows up as a check or X mark on the message in Hi Town.
     *
     * Leaving this field `null` will cause the bot result not to be shown to the user.
     */
    val success: Boolean? = null,
    /**
     * Let the user know what went right or wrong.
     */
    val note: String? = null,
    /**
     * Messages to send to the group (optional).
     */
    val actions: List<BotAction>? = null,
)

/**
 * An action the bot takes in the group.
 */
@Serializable
data class BotAction(
    /**
     * Text of the message to send to the group.
     */
    val message: String? = null,
)

/**
 * A message that was sent to the Hi Town group that the bot is installed in.
 */
@Serializable
data class MessageBotBody(
    /**
     * Text of the message.
     */
    val message: String? = null,
    /**
     * The Hi Town user that sent the message.
     */
    val person: Person? = null,
    /**
     * The Hi Town bot that sent the message.
     */
    val bot: Bot? = null,
)

/**
 * A Hi Town bot.
 */
@Serializable
data class Bot(
    var id: String? = null,
    var name: String? = null,
)

/**
 * A Hi Town user.
 */
@Serializable
class Person(
    var id: String? = null,
    var name: String? = null,
)
