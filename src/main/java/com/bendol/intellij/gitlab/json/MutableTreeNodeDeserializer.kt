package com.bendol.intellij.gitlab.json

import com.bendol.intellij.gitlab.model.SerializableTreeNode
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import javax.swing.tree.DefaultMutableTreeNode

class MutableTreeNodeDeserializer : JsonDeserializer<DefaultMutableTreeNode> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DefaultMutableTreeNode {
        val serializableNode = context.deserialize<SerializableTreeNode>(json, SerializableTreeNode::class.java)
        return convertFromSerializable(serializableNode)
    }

    private fun convertFromSerializable(node: SerializableTreeNode): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(node.userObject)
        for (child in node.children) {
            treeNode.add(convertFromSerializable(child))
        }
        return treeNode
    }
}