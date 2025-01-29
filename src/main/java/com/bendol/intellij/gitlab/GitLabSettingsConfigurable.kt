package com.bendol.intellij.gitlab

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPasswordField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
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
            panel = JPanel()
            panel?.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel?.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            panel?.add(JLabel("GitLab API URL:").also {
                it.foreground = JBColor.foreground()
            })
            apiUrlField = JTextField()
            apiUrlField?.maximumSize = Dimension(Integer.MAX_VALUE, apiUrlField?.preferredSize?.height ?: 25)
            panel?.add(apiUrlField)
            panel?.add(Box.createRigidArea(Dimension(0, 10)))

            // Group Name
            panel?.add(JLabel("Group Name:").also {
                it.foreground = JBColor.foreground()
            })
            groupNameField = JTextField()
            groupNameField?.maximumSize = Dimension(Integer.MAX_VALUE, groupNameField?.preferredSize?.height ?: 25)
            panel?.add(groupNameField)
            panel?.add(Box.createRigidArea(Dimension(0, 10)))

            // Token
            panel?.add(JLabel("GitLab Personal Access Token:").also {
                it.foreground = JBColor.foreground()
            })
            tokenField = JBPasswordField()
            tokenField?.maximumSize = Dimension(Integer.MAX_VALUE, tokenField?.preferredSize?.height ?: 25)
            panel?.add(tokenField)
            panel?.add(Box.createRigidArea(Dimension(0, 10)))

            panel?.add(Box.createRigidArea(Dimension(0, 10)))
            useEnvVarCheckBox = JCheckBox("Use Environment Variable for Token")
            panel?.add(useEnvVarCheckBox)

            // Debug Checkbox
            debugCheckBox = JCheckBox("Enable Debug Logging")
            panel?.add(debugCheckBox)

            // Add a note about the environment variable
            panel?.add(JLabel("<html><i>The GitLab token can also be set via the <b>GITLAB_TOKEN</b> environment variable.</i></html>").also {
                it.foreground = JBColor.GRAY
            })
            panel?.add(Box.createRigidArea(Dimension(0, 10)))
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
