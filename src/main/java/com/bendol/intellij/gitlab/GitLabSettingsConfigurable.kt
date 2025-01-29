package com.bendol.intellij.gitlab

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPasswordField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class GitLabSettingsConfigurable : Configurable {

    val logger: Logger = Logger.getInstance(GitLabSettingsConfigurable::class.java)

    private var panel: JPanel? = null
    private var tokenField: JBPasswordField? = null
    private var debugCheckBox: JCheckBox? = null
    private var apiUrlField: JTextField? = null
    private var groupNameField: JTextField? = null
    private var useEnvVarCheckBox: JCheckBox? = null

    override fun getDisplayName(): String = "GitLab Pipelines"

    override fun createComponent(): JComponent {
        if (panel == null) {
            val layout = GridBagLayout().apply {
                columnWidths = intArrayOf(150, 300)
            }
            panel = JPanel(layout).apply {
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                alignmentY = JComponent.TOP_ALIGNMENT
                alignmentX = JComponent.LEFT_ALIGNMENT
            }

            val gbc = GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(5, 5, 5, 5)
                weightx = 1.0
                weighty = 0.0
            }

            var row = 0

            // GitLab API URL
            gbc.gridx = 0
            gbc.gridy = row
            gbc.gridwidth = 1
            panel?.add(JLabel("GitLab API URL:"), gbc)

            gbc.gridx = 1
            apiUrlField = JTextField()
            apiUrlField?.preferredSize = Dimension(300, 25)
            panel?.add(apiUrlField, gbc)
            row++

            // Group Name
            gbc.gridx = 0
            gbc.gridy = row
            panel?.add(JLabel("Group Name:"), gbc)

            gbc.gridx = 1
            groupNameField = JTextField()
            panel?.add(groupNameField, gbc)
            row++

            // Token
            gbc.gridx = 0
            gbc.gridy = row
            panel?.add(JLabel("GitLab Personal Access Token:"), gbc)

            gbc.gridx = 1
            tokenField = JBPasswordField()
            panel?.add(tokenField, gbc)
            row++

            // Use Environment Variable Checkbox
            gbc.gridx = 0
            gbc.gridy = row
            gbc.gridwidth = 2 // Span both columns
            useEnvVarCheckBox = JCheckBox("Use Environment Variable for Token")
            useEnvVarCheckBox?.alignmentX = Component.LEFT_ALIGNMENT
            panel?.add(useEnvVarCheckBox, gbc)
            row++

            // Debug Checkbox
            gbc.gridy = row
            debugCheckBox = JCheckBox("Enable Debug Logging")
            debugCheckBox?.alignmentX = Component.LEFT_ALIGNMENT
            panel?.add(debugCheckBox, gbc)
            row++

            // Info Label
            gbc.gridy = row
            val infoLabel = JLabel(
                "<html><i>The GitLab token can also be set via the <b>GITLAB_TOKEN</b> environment variable.</i></html>"
            )
            infoLabel.foreground = JBColor.GRAY
            panel?.add(infoLabel, gbc)
            row++

            // Filler to push components to the top
            gbc.gridy = row
            gbc.weighty = 1.0 // Take up remaining vertical space
            gbc.fill = GridBagConstraints.VERTICAL
            panel?.add(Box.createVerticalGlue(), gbc)
        }

        // Load current settings
        val settings = GitLabSettingsState.getInstance().state
        apiUrlField?.text = settings.gitlabApiUrl
        groupNameField?.text = settings.groupName
        tokenField?.text = GitLabTokenManager.getInstance().getToken() ?: ""
        useEnvVarCheckBox?.isSelected = System.getenv("GITLAB_TOKEN").isNullOrEmpty().not()
        debugCheckBox?.isSelected = settings.debugEnabled

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = GitLabSettingsState.getInstance().state
        val currentToken = GitLabTokenManager.getInstance().getToken()
        val envVarSet = System.getenv("GITLAB_TOKEN").isNullOrEmpty().not()
        return apiUrlField?.text != settings.gitlabApiUrl ||
                groupNameField?.text != settings.groupName ||
                String(tokenField?.password ?: CharArray(0)) != currentToken ||
                debugCheckBox?.isSelected != settings.debugEnabled ||
                useEnvVarCheckBox?.isSelected != envVarSet
    }

    override fun apply() {
        val settings = GitLabSettingsState.getInstance().state
        settings.gitlabApiUrl = apiUrlField?.text ?: settings.gitlabApiUrl
        settings.groupName = groupNameField?.text ?: settings.groupName
        settings.debugEnabled = debugCheckBox?.isSelected ?: false

        val useEnvVar = useEnvVarCheckBox?.isSelected ?: false
        if (useEnvVar) {
            // Clear the stored token if using environment variable
            GitLabTokenManager.getInstance().clearToken()
        } else {
            val newToken = String(tokenField?.password ?: CharArray(0))
            if (newToken.isNotBlank()) {
                // Optional: Validate the token before saving
                CoroutineScope(Dispatchers.IO).launch {
                    val client = GitLabClient(newToken, settings.gitlabApiUrl)
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
    }

    override fun getHelpTopic(): String? = null
}
