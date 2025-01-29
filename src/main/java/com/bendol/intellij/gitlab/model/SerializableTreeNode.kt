package com.bendol.intellij.gitlab.model

data class SerializableTreeNode(
    val userObject: TreeNodeData?,
    val children: List<SerializableTreeNode> = emptyList()
)