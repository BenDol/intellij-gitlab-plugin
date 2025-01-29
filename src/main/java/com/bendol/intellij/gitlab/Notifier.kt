package com.bendol.intellij.gitlab

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object Notifier {
    private val NOTIFICATION_GROUP = NotificationGroupManager.getInstance()
        .getNotificationGroup("GitLab Pipelines Notifications")

    /**
     * Sends an informational notification.
     * @param title The title of the notification.
     * @param message The message content of the notification.
     */
    fun notifyInfo(title: String, message: String, project: Project? = null) {
        val notification = NOTIFICATION_GROUP.createNotification(title, message, NotificationType.INFORMATION)
        notification.notify(project)
    }

    /**
     * Sends a warning notification.
     * @param title The title of the notification.
     * @param message The message content of the notification.
     */
    fun notifyWarning(title: String, message: String, project: Project? = null) {
        val notification = NOTIFICATION_GROUP.createNotification(title, message, NotificationType.WARNING)
        notification.notify(project)
    }

    /**
     * Sends an error notification.
     * @param title The title of the notification.
     * @param message The message content of the notification.
     */
    fun notifyError(title: String, message: String, project: Project? = null) {
        val notification = NOTIFICATION_GROUP.createNotification(title, message, NotificationType.ERROR)
        notification.notify(project)
    }
}
