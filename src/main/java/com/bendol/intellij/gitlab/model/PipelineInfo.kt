package com.bendol.intellij.gitlab.model

data class PipelineInfo(
    val projectId: String,
    val repositoryName: String,
    val oldStatus: Status,
    val newStatus: Status
)