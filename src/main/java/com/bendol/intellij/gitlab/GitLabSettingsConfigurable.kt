package com.bendol.intellij.gitlab

import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JPanel

class GitLabSettingsConfigurable : SearchableConfigurable {

    override fun getId(): String = GitLabSettingsConfigurable::class.java.name

    override fun getDisplayName(): String = "GitLab"

    override fun createComponent() = JPanel()

    override fun isModified() = false

    override fun apply() {}

    override fun reset() {}

    override fun disposeUIResources() {}
}