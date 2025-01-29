package com.bendol.intellij.gitlab.json

import com.bendol.intellij.gitlab.SerializableTreeNode
import com.bendol.intellij.gitlab.TreeNodeData
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.intellij.openapi.diagnostic.Logger
import java.lang.reflect.Type
import javax.swing.tree.DefaultMutableTreeNode

class MutableTreeNodeSerializer : JsonSerializer<DefaultMutableTreeNode> {
    val logger: Logger = Logger.getInstance(MutableTreeNodeSerializer::class.java)

    override fun serialize(
        src: DefaultMutableTreeNode,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val serializableNode = convertToSerializable(src)
        return context.serialize(serializableNode)
    }

    private fun convertToSerializable(node: DefaultMutableTreeNode): SerializableTreeNode? {
        if (node.userObject !is TreeNodeData) {
            logger.debug("Node user object is not TreeNodeData: ${node.userObject}, skipping")
            return null
        }
        val userObject = node.userObject as TreeNodeData
        val children = mutableListOf<SerializableTreeNode>()
        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            val childSerializable = convertToSerializable(child)
            if (childSerializable != null) {
                children.add(childSerializable)
            }
        }
        return SerializableTreeNode(userObject, children)
    }
}