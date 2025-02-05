package com.bendol.intellij.gitlab

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service
@State(name = "GitLabSettings", storages = [Storage("GitLabPipelinesSettings.xml")])
class GitLabSettingsState : PersistentStateComponent<GitLabSettingsState.State> {
    data class State(
        var gitlabApiUrl: String = "https://gitlab.com/api/v4",
        var groupName: String = "insurance-insight",
        var cacheRefreshSeconds: Int = 10 * 60, // 10 minutes
        var refreshRateSeconds: Int = 5 * 60,   // 5 minutes
        var ignoredGroups: List<String> = listOf("10926345", "6622675"),
        var branches: Map<String, List<String>> = mapOf(
            // Group id as the key and branches in order of preference
            "4241428" to listOf("2.0-SNAPSHOT", "2.0.0-SNAPSHOT", "1.0-SNAPSHOT", "1.0.0-SNAPSHOT")
        ),
        var useEnvVarToken: Boolean = true,
        var foundEnvVarWarned: Boolean = false,
        var debugEnabled: Boolean = false
    )

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): GitLabSettingsState = service<GitLabSettingsState>()
    }
}
