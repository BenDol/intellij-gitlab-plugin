package com.bendol.intellij.gitlab

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object Images {
    val ProjectIcon: Icon = IconLoader.getIcon("/images/icon.png", Images::class.java)
    val SuccessIcon: Icon = IconLoader.getIcon("/images/success.png", Images::class.java)
    val FailedIcon: Icon = IconLoader.getIcon("/images/failed.png", Images::class.java)
    val RunningIcon: Icon = IconLoader.getIcon("/images/skipped.png", Images::class.java)
    val SkippedIcon: Icon = IconLoader.getIcon("/images/skipped.png", Images::class.java)
}