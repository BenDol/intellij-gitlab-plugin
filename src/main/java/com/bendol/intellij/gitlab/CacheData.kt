package com.bendol.intellij.gitlab

import javax.swing.tree.DefaultMutableTreeNode

data class CacheData(
    val treeData: DefaultMutableTreeNode,
    val timestamp: Long
)

