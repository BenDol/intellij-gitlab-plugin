package com.bendol.intellij.gitlab

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

object Notifier {
    private val NOTIFICATION_GROUP = NotificationGroupManager.getInstance()
        .getNotificationGroup("GitLab Pipelines Notifications")

    /**
     * Sends an informational notification.
     * @param title The title of the notification.
     * @param message The message content of the notification.
     */
    fun notifyInfo(title: String, message: String, project: Project? = null, actions: Map<String, (() -> Unit)>? = null) {
        val notification = NOTIFICATION_GROUP.createNotification(title, message, NotificationType.INFORMATION)
        addActions(notification, actions)
        notification.notify(project)
    }

    /**
     * Sends a warning notification.
     * @param title The title of the notification.
     * @param message The message content of the notification.
     */
    fun notifyWarning(title: String, message: String, project: Project? = null, actions: Map<String, (() -> Unit)>? = null) {
        val notification = NOTIFICATION_GROUP.createNotification(title, message, NotificationType.WARNING)
        addActions(notification, actions)
        notification.notify(project)
    }

    /**
     * Sends an error notification.
     * @param title The title of the notification.
     * @param message The message content of the notification.
     */
    fun notifyError(title: String, message: String, project: Project? = null, actions: Map<String, (() -> Unit)>? = null) {
        val notification = NOTIFICATION_GROUP.createNotification(title, message, NotificationType.ERROR)
        addActions(notification, actions)
        notification.notify(project)
    }

    private fun addActions(notification: Notification, actions: Map<String, (() -> Unit)>? = null) {
        actions?.forEach { action ->
            notification.addAction(object : AnAction(action.key) {
                override fun actionPerformed(e: AnActionEvent) {
                    action.value.invoke()
                }
            })
        }
    }
}
