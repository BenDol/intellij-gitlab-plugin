package com.bendol.intellij.gitlab

import com.bendol.intellij.gitlab.locale.LocaleBundle.localize
import com.bendol.intellij.gitlab.util.Notifier
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPasswordField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

class GitLabPipelinesConfigurable : SearchableConfigurable {

    private val logger: Logger = Logger.getInstance(GitLabPipelinesConfigurable::class.java)

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

    override fun getId(): String = GitLabPipelinesConfigurable::class.java.name

    override fun getDisplayName(): String {
        return localize("gitlab.pipeline.settings.displayName")
    }

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                alignmentX = Component.LEFT_ALIGNMENT
            }

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
            addLabeledField(localize("settings.pipelines.gitlabApiUrl.label"), apiUrlField!!)

            // Group Name
            groupNameField = JTextField()
            addLabeledField(localize("settings.pipelines.gitlabGroupName.label"), groupNameField!!)

            tokenField = JBPasswordField()
            addLabeledField(localize("settings.pipelines.gitlabToken.label"), tokenField!!)

            // Cache Refresh Seconds
            cacheRefreshField = JTextField()
            addLabeledField(localize("settings.pipelines.cacheRefresh.label"), cacheRefreshField!!)

            // Refresh Rate Seconds
            refreshRateField = JTextField()
            addLabeledField(localize("settings.pipelines.refreshRate.label"), refreshRateField!!)

            // Ignored Groups (comma-separated)
            ignoredGroupsField = JTextField()
            addLabeledField(localize("settings.pipelines.ignoredGroups.label"), ignoredGroupsField!!)

            // Branches (JSON input)
            branchesField = JTextArea(9, 30).apply {
                lineWrap = true
                wrapStyleWord = true
            }
            val branchesScrollPane = JScrollPane(branchesField).apply {
                maximumSize = Dimension(Integer.MAX_VALUE, 300)
            }
            addLabeledField(localize("settings.pipelines.branches.label"), branchesScrollPane)

            // Use Environment Variable Checkbox
            useEnvVarCheckBox = JCheckBox(localize("settings.pipelines.useEnvVar.label")).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel?.add(useEnvVarCheckBox)
            panel?.add(Box.createRigidArea(Dimension(0, 10)))

            // Debug Checkbox
            debugCheckBox = JCheckBox(localize("settings.pipelines.enableDebug.label")).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel?.add(debugCheckBox)
            panel?.add(Box.createRigidArea(Dimension(0, 10)))

            val infoLabel = JLabel(
                "<html><i>${localize("settings.pipelines.useEnvVar.help", "<b>GITLAB_TOKEN</b>")}</i></html>"
            ).apply {
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel?.add(infoLabel)
            panel?.add(Box.createVerticalGlue()) // Pushes everything up

            // Load settings
            reset()
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
            Notifier.notifyError(
                localize("settings.pipelines.branches.invalidJson.title"),
                localize("settings.pipelines.branches.invalidJson.message"))
        }

        if (settings.useEnvVarToken) {
            GitLabTokenManager.getInstance().clearToken()
        } else {
            settings.foundEnvVarWarned = false
            val newToken = String(tokenField?.password ?: CharArray(0))
            if (newToken.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val tokenManager = GitLabTokenManager.getInstance();
                    val client = GitLabClient(tokenManager, settings.gitlabApiUrl)
                    try {
                        val group = client.searchGroup(settings.groupName)
                        if (group != null) {
                            GitLabTokenManager.getInstance().setToken(newToken)
                            Notifier.notifyInfo(
                                localize("settings.pipelines.gitlabToken.valid.title"),
                                localize("settings.pipelines.gitlabToken.valid.message"))
                        } else {
                            Notifier.notifyError(
                                localize("settings.pipelines.gitlabToken.invalid.title"),
                                localize("settings.pipelines.gitlabToken.invalid.message"))
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to validate GitLab token", e)
                        Notifier.notifyError(
                            localize("settings.pipelines.gitlabToken.invalid.title"),
                            e.message ?: localize("error.unknown"))
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
        cacheRefreshField?.text = settings.cacheRefreshSeconds.toString()
        refreshRateField?.text = settings.refreshRateSeconds.toString()
        ignoredGroupsField?.text = settings.ignoredGroups.joinToString(", ")
        branchesField?.text = gson.toJson(settings.branches)
        useEnvVarCheckBox?.isSelected = settings.useEnvVarToken
        debugCheckBox?.isSelected = settings.debugEnabled
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
