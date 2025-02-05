package com.bendol.intellij.gitlab.util

fun String.camelCase(splitToken: String = "_"): String {
    val result = this.split(splitToken).joinToString("") { it.capitalize() }
    return result.substring(0, 1).lowercase() + result.substring(1)
}