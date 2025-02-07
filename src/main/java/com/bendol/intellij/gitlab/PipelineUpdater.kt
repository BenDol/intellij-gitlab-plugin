package com.bendol.intellij.gitlab

import com.bendol.intellij.gitlab.model.Pipeline
import com.bendol.intellij.gitlab.model.PipelineInfo
import com.bendol.intellij.gitlab.model.Repository
import com.bendol.intellij.gitlab.model.Status
import com.bendol.intellij.gitlab.model.TreeNodeData
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*

class PipelineUpdater(private val project: Project) {

    private val logger = Logger.getInstance(PipelineUpdater::class.java)

    private val statuses = Collections.synchronizedMap(mutableMapOf<String, Status>())
    private val mutex = Mutex()

    fun getStatus(id: String): Status? {
        return statuses[id]
    }

    suspend fun updateStatus(data: TreeNodeData) = withContext(Dispatchers.IO) {
        updateStatus(data, Pipeline(data.pipelineId ?: -1, data.status))
    }

    suspend fun updateStatus(data: TreeNodeData, pipeline: Pipeline) = withContext(Dispatchers.IO) {
        updateStatus(
            Repository(data.id.toInt(), data.name ?: "", data.webUrl ?: ""),
            pipeline)
    }

    suspend fun updateStatus(
        repository: Repository,
        pipeline: Pipeline,
        publishEvent: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val repoId = repository.id.toString()
        val newStatus = pipeline.status

        var oldStatus: Status?
        var shouldPublish = false

        mutex.withLock {
            oldStatus = statuses[repoId]
            if (oldStatus != newStatus) {
                statuses[repoId] = newStatus
                shouldPublish = publishEvent && oldStatus != null
            }
        }

        if (shouldPublish) {
            logger.warn("Pipeline status changed for ${repository.name}: $oldStatus -> $newStatus", Exception())

            EventBus.publish(EventBus.Event("pipeline_status_changed",
                project.basePath!!,
                PipelineInfo(
                    projectId = repoId,
                    repositoryName = repository.name,
                    oldStatus = oldStatus!!,
                    newStatus = newStatus
                )
            ))
        }
    }
}
