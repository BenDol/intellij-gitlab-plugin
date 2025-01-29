package com.bendol.intellij.gitlab

data class Pipeline(
    val id: Int,
    val status: Status,
    val ref: String,
    val web_url: String
)