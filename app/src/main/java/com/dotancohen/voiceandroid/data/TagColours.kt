package com.dotancohen.voiceandroid.data

import java.security.MessageDigest

/**
 * The colour of a Tag.
 *
 * Every Tag has one without anybody choosing it: the first six characters of
 * the MD5 hash of its name, read as `RRGGBB`. That gives each Tag a settled
 * colour that is the same on every device and in every application without
 * anything being stored or synced, and two Tags with the same name are
 * always the same colour.
 *
 * A colour the user picks is stored instead, in the synced settings under
 * `tag_color.<tag id>`, so a choice made on the phone reaches the desktop.
 * The key holds `RRGGBB`; an empty value means "back to the calculated one".
 */
object TagColours {

    /** The prefix of the synced setting that holds a chosen colour. */
    const val SETTING_PREFIX = "tag_color."

    /** The synced-settings key for one Tag's chosen colour. */
    fun settingKey(tagName: String): String = SETTING_PREFIX + tagName

    /**
     * The colour a Tag has when nobody has chosen one: `RRGGBB`, from the
     * first six characters of the MD5 of its name.
     */
    fun calculatedFor(name: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(name.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(6)
    }

    /**
     * The colour to draw a Tag in: the one chosen for it, or the calculated
     * one. A stored value that is not six hexadecimal characters is ignored
     * rather than trusted — it may have been written by a future version, or
     * by hand.
     */
    fun colourFor(name: String, chosen: String?): String {
        val cleaned = chosen?.removePrefix("#")?.trim()?.lowercase()
        return if (cleaned != null && HEX.matches(cleaned)) cleaned else calculatedFor(name)
    }

    /** That colour as the 0xAARRGGBB integer Compose wants. */
    fun argbFor(name: String, chosen: String? = null): Int =
        (0xFF000000L or colourFor(name, chosen).toLong(16)).toInt()

    /**
     * Whether text on this colour should be black rather than white.
     *
     * A Tag's colour comes from a hash, so it is as likely to be pale yellow
     * as navy blue; the label on it has to be readable either way. The weights
     * are the usual ones for how bright each channel looks to the eye.
     */
    fun prefersDarkText(colour: String): Boolean {
        val value = colour.removePrefix("#").lowercase().takeIf { HEX.matches(it) } ?: return false
        val r = value.substring(0, 2).toInt(16)
        val g = value.substring(2, 4).toInt(16)
        val b = value.substring(4, 6).toInt(16)
        // 140 rather than the midpoint: a strong green comes out at about
        // 150 and reads as a light background, so black text sits better on
        // it than white.
        return (0.299 * r + 0.587 * g + 0.114 * b) > 140
    }

    private val HEX = Regex("^[0-9a-f]{6}$")
}
