package com.bendol.intellij.gitlab.model

data class Group(
    val id: Int,
    val name: String,
    val path: String,
    val web_url: String,
    val full_name: String
)

