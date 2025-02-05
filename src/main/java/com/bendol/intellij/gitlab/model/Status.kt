package com.bendol.intellij.gitlab.model

import com.bendol.intellij.gitlab.locale.LocaleBundle

enum class Status(val displayName: String) {
    ANY("status.any"),
    CREATED("status.created"),
    WAITING_FOR_RESOURCE("status.waitingForResource"),
    PREPARING("status.preparing"),
    PENDING("status.pending"),
    RUNNING("status.running"),
    SUCCESS("status.success"),
    FAILED("status.failed"),
    CANCELED("status.canceled"),
    SKIPPED("status.skipped"),
    MANUAL("status.manual"),
    SCHEDULED("status.scheduled"),
    WAITING_FOR_MANUAL_ACTION("status.waitingForManualAction"),
    PREPARING_RESOURCES("status.preparingResources"),
    CREATED_RESOURCE("status.createdResource"),
    WAITING_FOR_PICK("status.waitingForPick"),
    BLOCKED("status.blocked"),
    UNBLOCKED("status.unblocked"),
    WAITING_FOR_RESOURCE_PICK("status.waitingForResourcePick"),
    ARCHIVED("status.archived"),
    UNKNOWN("status.unknown");

    override fun toString(): String {
        return LocaleBundle.localize(displayName)
    }
}