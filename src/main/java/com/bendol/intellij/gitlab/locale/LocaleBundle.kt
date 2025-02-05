package com.bendol.intellij.gitlab.locale

import com.bendol.intellij.gitlab.util.camelCase
import com.intellij.AbstractBundle
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.NonNls

@NonNls
private const val BUNDLE_NAME: String = "messages.PluginBundle"

object LocaleBundle : AbstractBundle(BUNDLE_NAME) {

    private val logger: Logger = Logger.getInstance(LocaleBundle::class.java)

    /**
     * Returns the localized message for the given key and parameters.
     *
     * @param key    the key in the resource bundle.
     * @param params any parameters to be substituted into the message.
     * @return the localized message.
     */
    @JvmStatic
    fun localize(key: String, vararg params: Any): String {
        val normalizedKey = normalizeKey(key)
        try {
            return getMessage(normalizedKey, *params)
        } catch (e: Exception) {
            logger.error("Failed to localize message for key: $normalizedKey", e)
            return "!!$normalizedKey!!"
        }
    }

    private fun normalizeKey(key: String): String {
        return key
            .replace("_", ".")
            .replace(" ", ".")
            .split(".")
                .joinToString(".") { it.camelCase() }
    }
}
