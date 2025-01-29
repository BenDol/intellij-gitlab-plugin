package com.bendol.intellij.gitlab.ui.tree

import com.amazon.ion.shaded_.do_not_use.kotlin.jvm.functions.Function1
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class FilteredTreeModel(
    private val originalModel: DefaultTreeModel,
    private var filter: Function1<DefaultMutableTreeNode, Boolean>
) : TreeModel {

    private val listeners = mutableListOf<TreeModelListener>()

    init {
        originalModel.addTreeModelListener(object : TreeModelListener {
            override fun treeNodesChanged(e: TreeModelEvent?)       { e?.let { notifyListeners(it) } }
            override fun treeNodesInserted(e: TreeModelEvent?)      { e?.let { notifyListeners(it) } }
            override fun treeNodesRemoved(e: TreeModelEvent?)       { e?.let { notifyListeners(it) } }
            override fun treeStructureChanged(e: TreeModelEvent?)   { e?.let { notifyListeners(it) } }
        })
    }

    fun setFilter(newFilter: Function1<DefaultMutableTreeNode, Boolean>) {
        filter = newFilter
        signalTreeStructureChanged()
    }

    fun signalTreeStructureChanged(node: DefaultMutableTreeNode? = getRoot() as DefaultMutableTreeNode) {
        val event = TreeModelEvent(this, arrayOf(node))
        listeners.forEach { it.treeStructureChanged(event) }
    }

    override fun getRoot(): Any = originalModel.root

    override fun getChild(parent: Any, index: Int): Any? {
        if (parent !is DefaultMutableTreeNode) return null
        val visibleChildren = getVisibleChildren(parent)
        return visibleChildren.getOrNull(index)
    }

    override fun getChildCount(parent: Any): Int {
        if (parent !is DefaultMutableTreeNode) return 0
        return getVisibleChildren(parent).size
    }

    override fun isLeaf(node: Any): Boolean = getChildCount(node) == 0

    override fun valueForPathChanged(path: TreePath?, newValue: Any?) {
        path?.let { originalModel.valueForPathChanged(it, newValue) }
    }

    override fun getIndexOfChild(parent: Any, child: Any): Int {
        if (parent !is DefaultMutableTreeNode || child !is DefaultMutableTreeNode) return -1
        val visibleChildren = getVisibleChildren(parent)
        return visibleChildren.indexOf(child)
    }

    override fun addTreeModelListener(l: TreeModelListener?) {
        l?.let { listeners.add(it) }
    }

    override fun removeTreeModelListener(l: TreeModelListener?) {
        l?.let { listeners.remove(it) }
    }

    fun reload(node: TreeNode = originalModel.root as TreeNode) {
        originalModel.reload(node)
    }

    fun nodeChanged(node: TreeNode) {
        originalModel.nodeChanged(node)
    }

    private fun getVisibleChildren(parent: DefaultMutableTreeNode): List<DefaultMutableTreeNode> {
        val count = originalModel.getChildCount(parent)
        val visible = mutableListOf<DefaultMutableTreeNode>()
        for (i in 0..<count) {
            val child = originalModel.getChild(parent, i)
            if (child is DefaultMutableTreeNode && filter.invoke(child)) {
                visible.add(child)
            }
        }
        return visible
    }

    private fun notifyListeners(e: TreeModelEvent) {
        listeners.forEach { listener ->
            listener.treeNodesChanged(e)
            listener.treeNodesInserted(e)
            listener.treeNodesRemoved(e)
            listener.treeStructureChanged(e)
        }
    }
}
