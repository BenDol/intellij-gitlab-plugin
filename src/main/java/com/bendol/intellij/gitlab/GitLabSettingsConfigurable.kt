package com.bendol.intellij.gitlab

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPasswordField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField

class GitLabSettingsConfigurable : Configurable {

    val logger: Logger = Logger.getInstance(GitLabSettingsConfigurable::class.java)

    private var panel: JPanel? = null
    private var tokenField: JBPasswordField? = null
    private var debugCheckBox: JCheckBox? = null
    private var apiUrlField: JTextField? = null
    private var groupNameField: JTextField? = null
    private var cacheRefreshField: JTextField? = null
    private var refreshRateField: JTextField? = null
    private var ignoredGroupsField: JTextField? = null
    private var branchesField: JTextArea? = null
    private var useEnvVarCheckBox: JCheckBox? = null

    private val project: Project?
        get() = ProjectManager.getInstance().openProjects.firstOrNull()

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    override fun getDisplayName(): String = "GitLab Pipelines"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                alignmentX = Component.LEFT_ALIGNMENT
            }

            // Helper function to create labeled fields
            fun addLabeledField(labelText: String, field: JComponent) {
                val label = JLabel(labelText).apply {
                    foreground = JBColor.foreground()
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                panel?.add(label)
                panel?.add(field.apply {
                    maximumSize = Dimension(Integer.MAX_VALUE, preferredSize.height)
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                panel?.add(Box.createRigidArea(Dimension(0, 10))) // Adds vertical spacing
            }

            // GitLab API URL
            apiUrlField = JTextField()
            addLabeledField("GitLab API URL:", apiUrlField!!)

            // Group Name
            groupNameField = JTextField()
            addLabeledField("Group Name:", groupNameField!!)

            tokenField = JBPasswordField()
            addLabeledField("GitLab Token:", tokenField!!)

            // Cache Refresh Seconds
            cacheRefreshField = JTextField()
            addLabeledField("Cache Refresh (seconds):", cacheRefreshField!!)

            // Refresh Rate Seconds
            refreshRateField = JTextField()
            addLabeledField("Refresh Rate (seconds):", refreshRateField!!)

            // Ignored Groups (comma-separated)
            ignoredGroupsField = JTextField()
            addLabeledField("Ignored Groups (comma-separated):", ignoredGroupsField!!)

            // Branches (JSON input)
            branchesField = JTextArea(9, 30).apply {
                lineWrap = true
                wrapStyleWord = true
            }
            val branchesScrollPane = JScrollPane(branchesField).apply {
                maximumSize = Dimension(Integer.MAX_VALUE, 300)
            }
            addLabeledField("Branches { \"Group Id\": [ <branches in order of preference> ] }:", branchesScrollPane)

            // Use Environment Variable Checkbox
            useEnvVarCheckBox = JCheckBox("Use Environment Variable for Token").apply {
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel?.add(useEnvVarCheckBox)
            panel?.add(Box.createRigidArea(Dimension(0, 10)))

            // Debug Checkbox
            debugCheckBox = JCheckBox("Enable Debug Logging").apply {
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel?.add(debugCheckBox)
            panel?.add(Box.createRigidArea(Dimension(0, 10)))

            // Info Label
            val infoLabel = JLabel(
                "<html><i>The GitLab token can also be set via the <b>GITLAB_TOKEN</b> environment variable.</i></html>"
            ).apply {
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel?.add(infoLabel)
            panel?.add(Box.createVerticalGlue()) // Pushes everything up

            // Load settings
            val settings = GitLabSettingsState.getInstance().state
            apiUrlField?.text = settings.gitlabApiUrl
            groupNameField?.text = settings.groupName
            cacheRefreshField?.text = settings.cacheRefreshSeconds.toString()
            refreshRateField?.text = settings.refreshRateSeconds.toString()
            ignoredGroupsField?.text = settings.ignoredGroups.joinToString(", ")
            branchesField?.text = gson.toJson(settings.branches) // Convert Map to JSON string
            useEnvVarCheckBox?.isSelected = settings.useEnvVarToken
            debugCheckBox?.isSelected = settings.debugEnabled
        }

        return panel!!
    }


    override fun isModified(): Boolean {
        val settings = GitLabSettingsState.getInstance().state
        val currentToken = GitLabTokenManager.getInstance().getToken()
        return apiUrlField?.text != settings.gitlabApiUrl ||
               groupNameField?.text != settings.groupName ||
               String(tokenField?.password ?: CharArray(0)) != currentToken ||
               debugCheckBox?.isSelected != settings.debugEnabled ||
               useEnvVarCheckBox?.isSelected != settings.useEnvVarToken ||
               cacheRefreshField?.text?.toIntOrNull() != settings.cacheRefreshSeconds ||
               refreshRateField?.text?.toIntOrNull() != settings.refreshRateSeconds ||
               ignoredGroupsField?.text != settings.ignoredGroups.joinToString(", ") ||
               gson.toJson(settings.branches) != branchesField?.text
    }

    override fun apply() {
        val settings = GitLabSettingsState.getInstance().state
        settings.gitlabApiUrl = apiUrlField?.text ?: settings.gitlabApiUrl
        settings.groupName = groupNameField?.text ?: settings.groupName
        settings.debugEnabled = debugCheckBox?.isSelected ?: false
        settings.cacheRefreshSeconds = cacheRefreshField?.text?.toIntOrNull() ?: settings.cacheRefreshSeconds
        settings.refreshRateSeconds = refreshRateField?.text?.toIntOrNull() ?: settings.refreshRateSeconds
        settings.useEnvVarToken = useEnvVarCheckBox?.isSelected ?: true

        if (ignoredGroupsField != null) {
            settings.ignoredGroups = ignoredGroupsField!!.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        try {
            settings.branches = gson.fromJson(branchesField?.text, object : TypeToken<Map<String, List<String>>>() {}.type)
        } catch (e: Exception) {
            Notifier.notifyError("Invalid JSON", "Branches must be in valid JSON format.")
        }

        if (settings.useEnvVarToken) {
            // Clear the stored token if using environment variable
            GitLabTokenManager.getInstance().clearToken()
        } else {
            val newToken = String(tokenField?.password ?: CharArray(0))
            if (newToken.isNotBlank()) {
                // Optional: Validate the token before saving
                CoroutineScope(Dispatchers.IO).launch {
                    val tokenManager = GitLabTokenManager.getInstance();
                    val client = GitLabClient(tokenManager, settings.gitlabApiUrl)
                    try {
                        val group = client.searchGroup(settings.groupName)
                        if (group != null) {
                            withContext(Dispatchers.Main) {
                                GitLabTokenManager.getInstance().setToken(newToken)
                                Notifier.notifyInfo("Token Valid", "GitLab token is valid and has been saved.")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Notifier.notifyError("Invalid Token", "Failed to validate the GitLab token.")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to validate GitLab token", e)
                        withContext(Dispatchers.Main) {
                            Notifier.notifyError("Token Validation Error", e.message ?: "Unknown error")
                        }
                    }
                }
            }
        }
    }

    override fun reset() {
        val settings = GitLabSettingsState.getInstance().state
        apiUrlField?.text = settings.gitlabApiUrl
        groupNameField?.text = settings.groupName
        tokenField?.text = GitLabTokenManager.getInstance().getToken() ?: ""
        debugCheckBox?.isSelected = settings.debugEnabled
        useEnvVarCheckBox?.isSelected = settings.useEnvVarToken
    }

    override fun disposeUIResources() {
        panel = null
        tokenField = null
        debugCheckBox = null
        apiUrlField = null
        groupNameField = null
        cacheRefreshField = null
        refreshRateField = null
        ignoredGroupsField = null
        branchesField = null
        useEnvVarCheckBox = null
    }

    override fun getHelpTopic(): String? = null
}
