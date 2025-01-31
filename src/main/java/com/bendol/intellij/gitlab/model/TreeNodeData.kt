package com.bendol.intellij.gitlab.model

data class TreeNodeData(
    val id: String,                      // Group ID or Repository ID
    val type: GroupType,                 // "group" or "repository"
    var status: Status = Status.UNKNOWN, // Pipeline status for repositories
    val webUrl: String? = null,          // URL to the group/repository in GitLab
    val parentGroup: String? = null,     // Parent group name or ID
    var pipelineId: String? = null,      // Pipeline ID for repositories
    val name: String? = null,            // Name of the group/repository
    var displayName: String,             // Display name of the group/repository
    var isExpanded: Boolean = false,     // Whether the node is expanded
    var filter: Filter = Filter.DEFAULT, // Filter for pipeline status
) {
    override fun toString(): String = displayName

    fun isRepository(): Boolean {
        return type == GroupType.REPOSITORY
    }

    fun isGroup(): Boolean {
        return type == GroupType.GROUP
    }

    fun isStatusUnknown(): Boolean {
        return status == Status.UNKNOWN
    }
}