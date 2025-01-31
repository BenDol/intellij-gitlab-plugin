package com.bendol.intellij.gitlab.ui.tree

import com.bendol.intellij.gitlab.model.Filter
import com.bendol.intellij.gitlab.model.TreeNodeData
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

class FilteredTreeModel(
    val unfilteredModel: DefaultTreeModel,
    private var filter: Predicate<DefaultMutableTreeNode>
) : DefaultTreeModel(unfilteredModel.root as DefaultMutableTreeNode) {

    var isFiltering: AtomicBoolean = AtomicBoolean(false)

    data class FilterResult(
        var filteredNode: DefaultMutableTreeNode? = null,
        var hasChanges: Boolean = false
    )

    companion object {
        private fun filterTree(
            node: DefaultMutableTreeNode,
            filter: Predicate<DefaultMutableTreeNode>
        ): FilterResult {
            val result = FilterResult()
            val filteredNode = DefaultMutableTreeNode(node.userObject)
            for (i in 0..<node.childCount) {
                val child = node.getChildAt(i) as DefaultMutableTreeNode
                val childResult = filterTree(child, filter)
                if (childResult.filteredNode != null) {
                    filteredNode.add(childResult.filteredNode)
                }
                if (childResult.filteredNode == null || childResult.hasChanges) {
                    result.hasChanges = true
                }
            }

            val test = filter.test(node)
            if (test || filteredNode.childCount > 0) {
                result.filteredNode = filteredNode
            }
            return result
        }
    }

    init {
        addTreeModelListener(object : TreeModelListener {
            override fun treeNodesChanged(e: javax.swing.event.TreeModelEvent?) {
                //filter()
            }

            override fun treeNodesInserted(e: javax.swing.event.TreeModelEvent?) {
                //filter()
            }

            override fun treeNodesRemoved(e: javax.swing.event.TreeModelEvent?) {
                //filter()
            }

            override fun treeStructureChanged(e: javax.swing.event.TreeModelEvent?) {
                //filter()
            }
        })
    }

    fun insertNode(newChild: MutableTreeNode, parent: DefaultMutableTreeNode): Boolean {
        if (filter.test(newChild as DefaultMutableTreeNode)) {
            super.insertNodeInto(newChild, parent, parent.childCount)
            return true
        }
        return false
    }

    override fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int) {
        if (filter.test(newChild as DefaultMutableTreeNode)) {
            super.insertNodeInto(newChild, parent, index)
        }
    }

    /**
     * Updates the filter and rebuilds the tree model.
     */
    fun filter(
        root: DefaultMutableTreeNode = this.unfilteredModel.root as DefaultMutableTreeNode,
        noResultsMessage: String = "No results found",
        reload: Boolean = false,
        filter: Filter = Filter.DEFAULT
    ): FilteredTreeModel? {
        val result = filter(root, noResultsMessage, reload) { node ->
            val data = node.userObject as? TreeNodeData
            data != null && filter.isMatch(data)
        }

        if (result == null) {
            return null
        }

        val newRoot = getRoot() as DefaultMutableTreeNode
        val data = newRoot.userObject as? TreeNodeData
        if (data != null) {
            data.filter = filter
        }

        return this
    }

    /**
     * Updates the filter and rebuilds the tree model.
     */
    fun filter(
        root: DefaultMutableTreeNode = this.unfilteredModel.root as DefaultMutableTreeNode,
        noResultsMessage: String = "No results found",
        reload: Boolean = false,
        filter: Predicate<DefaultMutableTreeNode>? = null
    ): FilteredTreeModel? {
        if (isFiltering.get()) {
            return null
        }
        isFiltering.set(true)
        val filterResult = filterTree(root) { node ->
            this.filter.test(node) && (filter == null || filter.test(node))
        }
        val newRoot = filterResult.filteredNode
        if (newRoot != null) {
            setRoot(newRoot)
        } else {
            setRoot(DefaultMutableTreeNode(noResultsMessage))
        }
        if (reload) {
            reload()
        }
        isFiltering.set(false)
        return this
    }
}
