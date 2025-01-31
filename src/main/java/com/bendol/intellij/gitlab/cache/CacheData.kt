package com.bendol.intellij.gitlab.cache

import javax.swing.tree.DefaultMutableTreeNode

data class CacheData(
    val treeData: DefaultMutableTreeNode?,
    val timestamp: Long?
)

