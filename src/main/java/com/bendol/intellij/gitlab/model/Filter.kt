package com.bendol.intellij.gitlab.model

data class Filter(val status: Status?) {

    fun isMatch(data: TreeNodeData): Boolean {
        return status == null
            || data.status == status
    }

    fun isDefault(): Boolean {
        return this == DEFAULT || status == null
    }

    companion object {
        val DEFAULT = Filter(null)
        val RUNNING = Filter(Status.RUNNING)
        val SUCCESS = Filter(Status.SUCCESS)
        val FAILED = Filter(Status.FAILED)
        val CANCELED = Filter(Status.CANCELED)
        val SKIPPED = Filter(Status.SKIPPED)
        val MANUAL = Filter(Status.MANUAL)
        val SCHEDULED = Filter(Status.SCHEDULED)
        val PENDING = Filter(Status.PENDING)
        val WAITING_FOR_MANUAL_ACTION = Filter(Status.WAITING_FOR_MANUAL_ACTION)
        val WAITING_FOR_RESOURCE = Filter(Status.WAITING_FOR_RESOURCE)
        val WAITING_FOR_RESOURCE_PICK = Filter(Status.WAITING_FOR_RESOURCE_PICK)
        val WAITING_FOR_PICK = Filter(Status.WAITING_FOR_PICK)
        val BLOCKED = Filter(Status.BLOCKED)
        val UNBLOCKED = Filter(Status.UNBLOCKED)
        val ARCHIVED = Filter(Status.ARCHIVED)
        val UNKNOWN = Filter(Status.UNKNOWN)
    }
}
