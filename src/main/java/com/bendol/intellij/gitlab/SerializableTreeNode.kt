package com.bendol.intellij.gitlab

data class SerializableTreeNode(
    val userObject: TreeNodeData?,
    val children: List<SerializableTreeNode> = emptyList()
)