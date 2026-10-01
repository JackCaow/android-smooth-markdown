package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.jackcaow.smoothmarkdown.ast.Node

class MentionNode(val username: String) : PluginInlineNode()
class HashtagNode(val tag: String) : PluginInlineNode()
class EmojiNode(val shortcode: String, val emoji: String) : PluginInlineNode()

class MentionPlugin : InlineParserPlugin {
    override val id = "mention"
    override val name = "Mention Plugin"
    override val priority = 10
    override val triggerCharacter = '@'
    private val username = Regex("^[A-Za-z][A-Za-z0-9_-]*")
    override fun canParse(text: String, index: Int): Boolean =
        index in text.indices && text[index] == '@' && index + 1 < text.length && text[index + 1].isAsciiLetter()
    override fun parse(text: String, startIndex: Int): InlineParseResult? {
        if (!canParse(text, startIndex)) return null
        val value = username.find(text.substring(startIndex + 1))?.value ?: return null
        return InlineParseResult(MentionNode(value), value.length + 1)
    }
    override fun render(node: PluginInlineNode): InlinePluginPresentation? =
        (node as? MentionNode)?.let { InlinePluginPresentation("@${it.username}", MarkdownPluginTokens().mentionStyle) }
}

class HashtagPlugin : InlineParserPlugin {
    override val id = "hashtag"
    override val name = "Hashtag Plugin"
    override val priority = 10
    override val triggerCharacter = '#'
    private val tag = Regex("^[A-Za-z_][A-Za-z0-9_]*")
    override fun canParse(text: String, index: Int): Boolean =
        index in text.indices && text[index] == '#' && index + 1 < text.length &&
            (text[index + 1].isAsciiLetter() || text[index + 1] == '_')
    override fun parse(text: String, startIndex: Int): InlineParseResult? {
        if (!canParse(text, startIndex)) return null
        val value = tag.find(text.substring(startIndex + 1))?.value ?: return null
        return InlineParseResult(HashtagNode(value), value.length + 1)
    }
    override fun render(node: PluginInlineNode): InlinePluginPresentation? =
        (node as? HashtagNode)?.let { InlinePluginPresentation("#${it.tag}", MarkdownPluginTokens().hashtagStyle) }
}

class EmojiPlugin(private val customEmojis: Map<String, String> = emptyMap()) : InlineParserPlugin {
    override val id = "emoji"
    override val name = "Emoji Plugin"
    override val priority = 5
    override val triggerCharacter = ':'
    private val shortcode = Regex("^([A-Za-z0-9_]+):")
    override fun canParse(text: String, index: Int): Boolean =
        index in text.indices && text[index] == ':' && index + 2 < text.length &&
            (text[index + 1].isAsciiLetter() || text[index + 1].isDigit() || text[index + 1] == '_')
    override fun parse(text: String, startIndex: Int): InlineParseResult? {
        if (!canParse(text, startIndex)) return null
        val value = shortcode.find(text.substring(startIndex + 1))?.groupValues?.get(1)?.lowercase() ?: return null
        val emoji = customEmojis[value] ?: defaultEmojis[value] ?: return null
        return InlineParseResult(EmojiNode(value, emoji), value.length + 2)
    }
    override fun render(node: PluginInlineNode): InlinePluginPresentation? =
        (node as? EmojiNode)?.let { InlinePluginPresentation(it.emoji) }

    companion object {
        val defaultEmojis: Map<String, String> = mapOf(
            // Kept in sync with Flutter's EmojiPlugin.defaultEmojis.
            "smile" to "😄",
            "grinning" to "😀",
            "laughing" to "😆",
            "joy" to "😂",
            "rofl" to "🤣",
            "wink" to "😉",
            "blush" to "😊",
            "innocent" to "😇",
            "heart_eyes" to "😍",
            "star_struck" to "🤩",
            "thinking" to "🤔",
            "raised_eyebrow" to "🤨",
            "neutral_face" to "😐",
            "expressionless" to "😑",
            "unamused" to "😒",
            "roll_eyes" to "🙄",
            "worried" to "😟",
            "frowning" to "😦",
            "cry" to "😢",
            "sob" to "😭",
            "angry" to "😠",
            "rage" to "😡",
            "skull" to "💀",
            "poop" to "💩",
            "clown" to "🤡",
            "ghost" to "👻",
            "alien" to "👽",
            "robot" to "🤖",
            "sunglasses" to "😎",
            "nerd" to "🤓",
            "thumbsup" to "👍",
            "thumbsdown" to "👎",
            "ok_hand" to "👌",
            "pinching_hand" to "🤏",
            "wave" to "👋",
            "clap" to "👏",
            "pray" to "🙏",
            "handshake" to "🤝",
            "muscle" to "💪",
            "point_up" to "☝️",
            "point_down" to "👇",
            "point_left" to "👈",
            "point_right" to "👉",
            "middle_finger" to "🖕",
            "raised_hand" to "✋",
            "vulcan_salute" to "🖖",
            "fist" to "✊",
            "punch" to "👊",
            "heart" to "❤️",
            "orange_heart" to "🧡",
            "yellow_heart" to "💛",
            "green_heart" to "💚",
            "blue_heart" to "💙",
            "purple_heart" to "💜",
            "black_heart" to "🖤",
            "white_heart" to "🤍",
            "broken_heart" to "💔",
            "sparkling_heart" to "💖",
            "heartbeat" to "💓",
            "two_hearts" to "💕",
            "kiss" to "💋",
            "sun" to "☀️",
            "moon" to "🌙",
            "star" to "⭐",
            "cloud" to "☁️",
            "rain" to "🌧️",
            "snow" to "❄️",
            "fire" to "🔥",
            "rainbow" to "🌈",
            "ocean" to "🌊",
            "earth" to "🌍",
            "tree" to "🌳",
            "flower" to "🌸",
            "rose" to "🌹",
            "dog" to "🐶",
            "cat" to "🐱",
            "mouse" to "🐭",
            "rabbit" to "🐰",
            "fox" to "🦊",
            "bear" to "🐻",
            "panda" to "🐼",
            "koala" to "🐨",
            "tiger" to "🐯",
            "lion" to "🦁",
            "cow" to "🐮",
            "pig" to "🐷",
            "frog" to "🐸",
            "monkey" to "🐵",
            "chicken" to "🐔",
            "penguin" to "🐧",
            "bird" to "🐦",
            "eagle" to "🦅",
            "owl" to "🦉",
            "butterfly" to "🦋",
            "snail" to "🐌",
            "bug" to "🐛",
            "ant" to "🐜",
            "bee" to "🐝",
            "spider" to "🕷️",
            "turtle" to "🐢",
            "snake" to "🐍",
            "dragon" to "🐉",
            "whale" to "🐳",
            "dolphin" to "🐬",
            "fish" to "🐟",
            "octopus" to "🐙",
            "crab" to "🦀",
            "unicorn" to "🦄",
            "apple" to "🍎",
            "banana" to "🍌",
            "grapes" to "🍇",
            "watermelon" to "🍉",
            "strawberry" to "🍓",
            "peach" to "🍑",
            "pizza" to "🍕",
            "hamburger" to "🍔",
            "fries" to "🍟",
            "hotdog" to "🌭",
            "taco" to "🌮",
            "burrito" to "🌯",
            "sushi" to "🍣",
            "ramen" to "🍜",
            "cake" to "🎂",
            "cookie" to "🍪",
            "chocolate" to "🍫",
            "candy" to "🍬",
            "icecream" to "🍦",
            "coffee" to "☕",
            "tea" to "🍵",
            "beer" to "🍺",
            "wine" to "🍷",
            "cocktail" to "🍸",
            "soccer" to "⚽",
            "basketball" to "🏀",
            "football" to "🏈",
            "baseball" to "⚾",
            "tennis" to "🎾",
            "golf" to "⛳",
            "trophy" to "🏆",
            "medal" to "🥇",
            "video_game" to "🎮",
            "dart" to "🎯",
            "bowling" to "🎳",
            "phone" to "📱",
            "computer" to "💻",
            "keyboard" to "⌨️",
            "camera" to "📷",
            "tv" to "📺",
            "radio" to "📻",
            "book" to "📖",
            "pen" to "🖊️",
            "pencil" to "✏️",
            "scissors" to "✂️",
            "lock" to "🔒",
            "key" to "🔑",
            "hammer" to "🔨",
            "wrench" to "🔧",
            "bulb" to "💡",
            "money" to "💰",
            "gem" to "💎",
            "gift" to "🎁",
            "balloon" to "🎈",
            "check" to "✅",
            "x" to "❌",
            "warning" to "⚠️",
            "question" to "❓",
            "exclamation" to "❗",
            "plus" to "➕",
            "minus" to "➖",
            "100" to "💯",
            "sparkles" to "✨",
            "boom" to "💥",
            "zzz" to "💤",
            "speech_balloon" to "💬",
            "thought_balloon" to "💭",
            "checkered_flag" to "🏁",
            "triangular_flag" to "🚩",
            "white_flag" to "🏳️",
            "rainbow_flag" to "🏳️‍🌈",
            "rocket" to "🚀",
            "airplane" to "✈️",
            "car" to "🚗",
            "bus" to "🚌",
            "train" to "🚂",
            "ship" to "🚢",
            "anchor" to "⚓",
            "construction" to "🚧",
        )
    }
}

enum class AdmonitionType { NOTE, TIP, WARNING, DANGER, IMPORTANT, CUSTOM }
class AdmonitionNode(
    val admonitionType: AdmonitionType,
    val title: String,
    val customType: String? = null,
) : PluginBlockNode() {
    var content: List<Node> = emptyList()
        internal set
}

class AdmonitionPlugin : BlockParserPlugin {
    override val id = "admonition"
    override val name = "Admonition Plugin"
    override val priority = 10
    private val start = Regex("^:::\\s*(\\w+)(?:\\s+(.+))?$")
    override fun canStart(line: String): Boolean = start.matches(line.trim())
    override fun createNode(openingLine: String): PluginBlockNode? {
        val match = start.matchEntire(openingLine.trim()) ?: return null
        val typeName = match.groupValues[1].lowercase()
        val type = when (typeName) {
            "note", "info" -> AdmonitionType.NOTE
            "tip", "hint" -> AdmonitionType.TIP
            "warning", "caution" -> AdmonitionType.WARNING
            "danger", "error" -> AdmonitionType.DANGER
            "important" -> AdmonitionType.IMPORTANT
            else -> AdmonitionType.CUSTOM
        }
        return AdmonitionNode(type, match.groupValues[2].trim(), typeName.takeIf { type == AdmonitionType.CUSTOM })
    }
    override fun isClosingLine(line: String): Boolean = line.trim() == ":::"
    override fun complete(node: PluginBlockNode, contentLines: List<String>) {
        val admonition = node as AdmonitionNode
        val content = contentLines.joinToString("\n").trim()
        admonition.content = if (content.isEmpty()) emptyList() else parseMarkdown(content).childrenList()
    }
    @Composable
    override fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit) {
        val admonition = node as AdmonitionNode
        val strings = LocalMarkdownStrings.current
        val tokens = LocalMarkdownStyleSheet.current.designTokens.plugins.admonition
        val accent = tokens.accentColors[admonition.admonitionType] ?: MaterialTheme.colorScheme.primary
        val shape = RoundedCornerShape(tokens.cornerRadius)
        Row(Modifier.fillMaxWidth().padding(tokens.outerPadding).clip(shape)
            .background(tokens.backgroundColor ?: accent.copy(alpha = tokens.backgroundAlpha))
            .then(if (tokens.borderWidth.value > 0) Modifier.border(tokens.borderWidth, tokens.borderColor ?: accent, shape) else Modifier)
            .padding(tokens.contentPadding)) {
            Spacer(Modifier.width(tokens.accentWidth).height(tokens.accentHeight).background(accent))
            Column(Modifier.padding(start = tokens.contentSpacing)) {
                Text(admonition.title.ifEmpty { admonition.customType ?: strings[admonition.admonitionType.name.lowercase().replaceFirstChar(Char::uppercase)] },
                    color = tokens.titleColor ?: accent,
                    style = tokens.titleStyle ?: TextStyle(fontWeight = FontWeight.Bold))
                admonition.content.forEach { renderChild(it) }
            }
        }
    }
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
private fun Node.childrenList(): List<Node> = buildList {
    var child = firstChild
    while (child != null) { add(child); child = child.next }
}
