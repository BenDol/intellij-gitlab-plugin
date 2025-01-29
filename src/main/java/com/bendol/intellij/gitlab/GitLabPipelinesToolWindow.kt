package com.bendol.intellij.gitlab

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreePath


class GitLabPipelinesToolWindow(private val project: Project) : SimpleToolWindowPanel(true, true) {

    val logger: Logger = Logger.getInstance(GitLabPipelinesToolWindow::class.java)

    private val treeModel: DefaultTreeModel
    private val tree: Tree
    private val refreshButton: JButton = JButton("Refresh")
    private val resetGroupsButton: JButton = JButton("Reset Groups")
    private val loadingLabel: JLabel = JLabel("")
    private val lastRefreshLabel: JLabel = JLabel("")

    private lateinit var gitLabClient: GitLabClient
    private val cacheManager = CacheManager()
    private val settings = GitLabSettingsState.getInstance().state
    private var isLoaded = false
    private var isRefreshing = false


    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        treeModel = DefaultTreeModel(DefaultMutableTreeNode("Loading..."))
        tree = Tree(treeModel)
        setupUI()
        initialize()
    }

    private fun setupUI() {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        addTreeViewHandlers()

        // Input Panel
        /*val inputPanel = JPanel()
        inputPanel.layout = BoxLayout(inputPanel, BoxLayout.X_AXIS)
        inputPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        inputPanel.background = JBColor.background()

        inputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        inputPanel.add(resetGroupsButton)
        inputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        inputPanel.add(refreshButton)

        add(inputPanel)*/

        // Tree Panel
        val treePanel = JBScrollPane(tree)
        add(treePanel)

        // Status Panel
        val statusPanel = JPanel()
        statusPanel.layout = BoxLayout(statusPanel, BoxLayout.X_AXIS)
        statusPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        statusPanel.background = JBColor.background()
        statusPanel.add(loadingLabel)
        statusPanel.add(Box.createHorizontalGlue())
        statusPanel.add(lastRefreshLabel)

        add(statusPanel)

        // Button Actions
        refreshButton.addActionListener {
            refreshGroups(notify = true)
        }

        resetGroupsButton.addActionListener {
            loadRootGroup()
        }

        @Suppress("UseJBColor")
        val renderer = object : DefaultTreeCellRenderer() {
            private val scaledIconCache: MutableMap<Icon, Icon> = HashMap()

            override fun getTreeCellRendererComponent(
                tree: JTree?,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): java.awt.Component {
                val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
                val node = value as? DefaultMutableTreeNode ?: return component
                val nodeData = node.userObject as? TreeNodeData ?: return component
                backgroundNonSelectionColor = Color(0, 0, 0, 0)
                font = font.deriveFont(14.0f)

                when (nodeData.type) {
                    GroupType.GROUP -> {
                        foreground = JBColor.foreground()
                    }
                    GroupType.REPOSITORY -> {
                        when (nodeData.status) {
                            Status.SUCCESS, Status.MANUAL -> {
                                icon = Images.SuccessIcon
                                foreground = Color.decode("0x9cff9c")
                            }
                            Status.FAILED, Status.CANCELED -> {
                                icon = Images.FailedIcon
                                foreground = Color.decode("0xff8080")
                            }
                            Status.SKIPPED, Status.RUNNING, Status.PENDING -> {
                                icon = Images.SkippedIcon
                                foreground = Color.decode("0xcccccc")
                            }
                            else -> {
                                icon = null
                                foreground = JBColor.foreground()
                            }
                        }
                    }
                }

                val originalIcon = icon

                if (originalIcon != null) {
                    var rowHeight = tree!!.rowHeight
                    if (rowHeight <= 0) {
                        rowHeight = tree.font.size + 4
                    }

                    var scaledIcon: Icon? = scaledIconCache[originalIcon]
                    if (scaledIcon == null) {
                        scaledIcon = createScaledIcon(originalIcon, rowHeight - 4)
                        scaledIconCache[originalIcon] = scaledIcon
                    }

                    icon = scaledIcon
                }

                return component
            }

            /**
             * Scales the given icon to the specified height while maintaining aspect ratio.
             *
             * @param icon   The original icon to scale.
             * @param desiredHeight The desired height.
             * @return The scaled icon.
             */
            private fun createScaledIcon(icon: Icon, desiredHeight: Int): Icon {
                val originalWidth = icon.iconWidth
                val originalHeight = icon.iconHeight

                val scaleFactor = desiredHeight.toDouble() / originalHeight
                val newWidth = (originalWidth * scaleFactor).toInt()
                val newHeight: Int = desiredHeight

                val bufferedImage = BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_ARGB)
                val g2d = bufferedImage.createGraphics()

                g2d.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )
                g2d.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
                )
                g2d.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )

                icon.paintIcon(null, g2d, 0, 0)
                g2d.dispose()

                return ImageIcon(bufferedImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH))
            }
        }

        tree.cellRenderer = renderer
    }

    private fun addTreeViewHandlers() {
        if (isLoaded)
            return

        tree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                if (!isLoaded)
                    return
                if (e?.clickCount == 2) {
                    handleTreeDoubleClick()
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    handleTreeRightClick(e!!)
                }
            }
        })

        tree.addTreeExpansionListener(object : javax.swing.event.TreeExpansionListener {
            override fun treeExpanded(event: javax.swing.event.TreeExpansionEvent?) {
                if (!isLoaded)
                    return
                val node = event?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
                val nodeData = node.userObject as? TreeNodeData ?: return

                if (nodeData.isGroup()) {
                    nodeData.isExpanded = true

                    if (nodeData.isStatusUnknown()) {
                        node.removeAllChildren()
                        CoroutineScope(Dispatchers.IO).launch {
                            loadSubgroupsAndRepositories(node, nodeData.id.toInt())
                        }
                    } else {
                        if (!isRefreshing) {
                            cacheManager.saveCache(treeModel.root as DefaultMutableTreeNode)
                        }
                    }
                }
            }

            override fun treeCollapsed(event: javax.swing.event.TreeExpansionEvent?) {
                val node = event?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
                val nodeData = node.userObject as? TreeNodeData ?: return
                if (nodeData.isGroup()) {
                    nodeData.isExpanded = false
                }

                if (!isRefreshing) {
                    cacheManager.saveCache(treeModel.root as DefaultMutableTreeNode)
                }
            }
        })
    }

    private fun initialize() {
        Utils.debugEnabled = settings.debugEnabled

        // Initialize GitLab Client
        val token = GitLabTokenManager.getInstance().getToken() ?: run {
            Notifier.notifyWarning(
                "GitLab Token Missing",
                "Please provide a GitLab Personal Access Token in the settings.",
                project)
            ""
        }
        gitLabClient = GitLabClient(token, settings.gitlabApiUrl)

        // Notify user to restart if environment variable is set
        val envToken = System.getenv("GITLAB_TOKEN")
        if (!envToken.isNullOrEmpty()) {
            Notifier.notifyInfo("Environment Variable Detected",
                "GitLab token loaded from environment variable. Restart the IDE if you update the environment variable.", project)
        }

        // Load tree from cache or fetch from API
        //cacheManager.clearCache() // TODO: DEBUG (remove)
        if (cacheManager.loadCache() != null && !cacheManager.isCacheExpired(settings.cacheRefreshSeconds)) {
            loadTreeFromCache()
        } else {
            loadRootGroup()
        }

        // Schedule periodic refresh
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(settings.refreshRateSeconds * 1000L)
                withContext(Dispatchers.Main) {
                    refreshGroups()
                }
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            EventBus.events.collect { event ->
                when (event.name) {
                    "pipeline_status_changed" -> {
                        val info = event.data as? PipelineInfo
                        info?.let {
                            if (it.oldStatus == Status.UNKNOWN) {
                                return@collect
                            }
                            Notifier.notifyInfo(
                                "Pipeline Status Changed",
                                "Project '${it.repositoryName}' pipeline status: ${it.oldStatus} → ${it.newStatus}",
                                project
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadIcon(path: String): Icon? {
        return try {
            ImageIcon(javaClass.getResource(path))
        } catch (e: Exception) {
            logger.error("Failed to load icon at $path", e)
            null
        }
    }

    private fun loadTreeFromCache() {
        loadingLabel.text = "Loading from cache..."
        isRefreshing = false
        isLoaded = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cachedData = cacheManager.loadCache()
                if (cachedData != null) {
                    withContext(Dispatchers.Main) {
                        setTreeRootNode(cachedData.treeData)
                        loadingLabel.text = ""
                        //Notifier.notifyInfo("GitLab Pipelines", "Loaded data from cache.", project)
                        isLoaded = true
                    }
                }
            } catch (e: Exception) {
                logger.error("Error loading cache", e)
                withContext(Dispatchers.Main) {
                    loadingLabel.text = ""
                    Notifier.notifyError("GitLab Pipelines", "Failed to load cache.", project)
                }
            }
        }
    }

    private fun setTreeRootNode(node: MutableTreeNode) {
        logger.debug("Populating tree from cache...")
        treeModel.setRoot(node)
        treeModel.reload()
        refreshNodes(node as DefaultMutableTreeNode)
    }

    private fun refreshNodes(node: DefaultMutableTreeNode, resetStatus: Boolean = false, saveCache: Boolean = true) {
        val nodeData = node.userObject as? TreeNodeData
        if (node.childCount < 1 && nodeData != null && nodeData.isGroup()) {
            nodeData.status = Status.UNKNOWN
            node.add(DefaultMutableTreeNode("Loading..."))
            return
        }

        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            val childData = child.userObject as? TreeNodeData
            if (childData != null) {
                if (childData.isGroup()) {
                    if (childData.isExpanded) {
                        tree.expandPath(TreePath(child.path))
                    }
                    refreshNodes(child, saveCache)
                } else if (childData.isRepository()) {
                    if (resetStatus) {
                        childData.status = Status.UNKNOWN
                    }
                    if (childData.status == Status.UNKNOWN) {
                        refreshRepository(child, saveCache)
                    }
                }
            }
        }
    }

    private fun loadRootGroup() {
        loadingLabel.text = "Loading root group..."
        isRefreshing = false
        isLoaded = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val group = gitLabClient.searchGroup(settings.groupName)
                withContext(Dispatchers.Main) {
                    if (group != null) {
                        populateRootGroup(group)
                        loadingLabel.text = ""
                        Notifier.notifyInfo("GitLab Pipelines", "Loaded group: ${group.name}", project)
                    } else {
                        loadingLabel.text = ""
                        Notifier.notifyError("GitLab Pipelines", "Group not found: ${settings.groupName}", project)
                    }

                    isLoaded = true
                }
            } catch (e: Exception) {
                logger.error("Error loading root group", e)
                withContext(Dispatchers.Main) {
                    loadingLabel.text = ""
                    Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
                }
            }
        }
    }

    private fun populateRootGroup(group: Group) {
        val root = DefaultMutableTreeNode(TreeNodeData(
            id = group.id.toString(),
            type = GroupType.GROUP,
            status = Status.UNKNOWN,
            webUrl = group.web_url,
            name = group.name,
            displayName = getGroupDisplayName(group.name)
        ))
        root.add(DefaultMutableTreeNode("Loading..."))
        setTreeRootNode(root)
        tree.collapsePath(TreePath(root.path))
    }

    private fun loadSubgroupsAndRepositories(
        node: DefaultMutableTreeNode,
        groupId: Int,
        saveCache: Boolean = true,
        types: Set<GroupType> = setOf(GroupType.GROUP, GroupType.REPOSITORY)
    ) {
        loadingLabel.text = "Loading subgroups and repositories..."
        try {
            if (types.contains(GroupType.GROUP)) {
                val subgroups = gitLabClient.getSubgroups(groupId)
                subgroups.filter { it.id.toString() !in settings.ignoredGroups }
                    .forEach { subgroup ->
                        val subgroupNode = DefaultMutableTreeNode(
                            TreeNodeData(
                                id = subgroup.id.toString(),
                                type = GroupType.GROUP,
                                status = Status.UNKNOWN,
                                webUrl = subgroup.web_url,
                                name = subgroup.name,
                                displayName = getGroupDisplayName(subgroup.name)
                            )
                        )
                        //CoroutineScope(Dispatchers.Main).launch {
                        subgroupNode.add(DefaultMutableTreeNode("Loading..."))
                        node.add(subgroupNode)
                        //}
                    }
            }

            if (types.contains(GroupType.REPOSITORY)) {
                val repositories = gitLabClient.getGroupRepositories(groupId)
                repositories.forEach { repository ->
                    val pipeline = gitLabClient.getLatestPipeline(repository.id)
                    if (pipeline?.id == null) {
                        logger.debug("No pipeline found for repository ${repository.name}")
                        return@forEach
                    }
                    val status = pipeline.status

                    //CoroutineScope(Dispatchers.Main).launch {
                    val repoNode = DefaultMutableTreeNode(
                        TreeNodeData(
                            id = repository.id.toString(),
                            type = GroupType.REPOSITORY,
                            status = status,
                            webUrl = repository.web_url,
                            parentGroup = extractGroupName(node),
                            pipelineId = pipeline.id.toString(),
                            name = repository.name,
                            displayName = getRepositoryDisplayName(repository.name, status)
                        )
                    )
                    node.add(repoNode)
                    //}
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                treeModel.reload(node)
                loadingLabel.text = ""

                val data = node.userObject as TreeNodeData
                data.status = Status.SUCCESS
                if (data.isExpanded) {
                    tree.expandPath(TreePath(node.path))
                }

                if (saveCache) {
                    // Cache the updated tree
                    cacheManager.saveCache(treeModel.root as DefaultMutableTreeNode)
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading subgroups/repositories", e)
            //CoroutineScope(Dispatchers.Main).launch {
                loadingLabel.text = ""
                Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
            //}
        }
    }

    private fun getGroupDisplayName(groupName: String): String {
        return /*"Group: */"$groupName"
    }

    private fun extractGroupUrl(node: DefaultMutableTreeNode): String? {
        val nodeData = node.userObject as? TreeNodeData ?: run {
            Notifier.notifyError("Extract Group URL", "Invalid node data.", project)
            return null
        }

        if (!nodeData.isGroup()) {
            Notifier.notifyWarning("Extract Group URL", "Selected node is not a group.", project)
            return null
        }

        val groupUrl = nodeData.webUrl
        if (groupUrl.isNullOrEmpty()) {
            Notifier.notifyWarning("Extract Group URL", "Group '${nodeData.name}' does not have a valid URL.", project)
            return null
        }

        return groupUrl
    }

    private fun refreshGroups(saveCache: Boolean = true, notify: Boolean = false) {
        loadingLabel.text = "Refreshing groups..."
        if (isRefreshing) {
            Notifier.notifyWarning("GitLab Pipelines", "Already refreshing groups.", project)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rootNode = tree.model.root as DefaultMutableTreeNode
                refreshGroupNode(rootNode, false)
                withContext(Dispatchers.Main) {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    val lastRefreshTime = dateFormat.format(java.util.Date())
                    lastRefreshLabel.text = "Last refresh: $lastRefreshTime"
                    if (notify)
                        Notifier.notifyInfo("GitLab Pipelines", "Groups refreshed.", project)
                }
                if (saveCache) {
                    cacheManager.saveCache(rootNode)
                }
            } catch (e: Exception) {
                logger.error("Error refreshing groups", e)
                withContext(Dispatchers.Main) {
                    loadingLabel.text = ""
                    Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
                }
            }
        }
    }

    private suspend fun refreshGroupNode(node: DefaultMutableTreeNode, saveCache: Boolean = true) {
        isRefreshing = true

        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            if (child.userObject !is TreeNodeData) {
                logger.warn("refreshGroupNode: Invalid node data, skipping ${child}.")
                continue
            }

            val data = child.userObject as TreeNodeData
            if (data.isGroup() && data.isExpanded) {
                val groupName = data.name ?: extractGroupName(child)
                val group = gitLabClient.searchGroup(groupName)
                if (group != null) {
                    removeRepositories(child)
                    loadSubgroupsAndRepositories(child, group.id, false, setOf(GroupType.REPOSITORY))
                    refreshGroupNode(child, saveCache = saveCache)
                }
            }
        }

        isRefreshing = false
    }

    private fun removeRepositories(node: DefaultMutableTreeNode) {
        val toRemove = mutableListOf<DefaultMutableTreeNode>()
        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            if (child.userObject is TreeNodeData && (child.userObject as TreeNodeData).isRepository()) {
                toRemove.add(child)
            }
        }
        toRemove.forEach { node.remove(it) }
    }

    private fun getRepositoryDisplayName(repoName: String, status: Status): String {
        return /*"Repository: */"$repoName (${status.name.lowercase()})"
    }

    private fun handleTreeDoubleClick() {
        val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        if (selectedNode.userObject !is TreeNodeData) {
            logger.warn("handleTreeDoubleClick: Invalid node data.")
            return
        }
        val data = selectedNode.userObject as TreeNodeData
        if (data.isRepository()) {
            val repoUrl = extractRepositoryUrl(selectedNode)
            repoUrl?.let {
                java.awt.Desktop.getDesktop().browse(java.net.URI(it))
            }
        }
    }

    private fun extractRepositoryUrl(node: DefaultMutableTreeNode): String? {
        if (node.userObject !is TreeNodeData) {
            logger.warn("extractRepositoryUrl: Invalid node data.")
            return null
        }
        val data = node.userObject as TreeNodeData
        return data.webUrl
    }

    private fun handleTreeRightClick(e: java.awt.event.MouseEvent) {
        val selectedNode = tree.getClosestPathForLocation(e.x, e.y)?.lastPathComponent as? DefaultMutableTreeNode ?: return
        val nodeData = selectedNode.userObject as? TreeNodeData ?: return

        val popupMenu = JPopupMenu()

        when (nodeData.type) {
            GroupType.GROUP -> {
                popupMenu.add(JMenuItem("Refresh").apply {
                    addActionListener {
                        CoroutineScope(Dispatchers.IO).launch {
                            refreshGroupNode(selectedNode)
                        }
                    }
                })
                popupMenu.add(JMenuItem("Open in Browser").apply {
                    addActionListener {
                        val groupUrl = extractGroupUrl(selectedNode)
                        groupUrl?.let { url ->
                            try {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                            } catch (e: Exception) {
                                Notifier.notifyError("Open in Browser", "Failed to open URL: $url", project)
                            }
                        }
                    }
                })
            }
            GroupType.REPOSITORY -> {
                popupMenu.add(JMenuItem("Refresh").apply {
                    addActionListener {
                        CoroutineScope(Dispatchers.IO).launch {
                            refreshRepository(selectedNode)
                        }
                    }
                })
                popupMenu.add(JMenuItem("Open in Browser").apply {
                    addActionListener {
                        nodeData.webUrl?.let { url ->
                            try {
                                java.awt.Desktop.getDesktop().browse(java.net.URI("$url/-/pipelines/${nodeData.pipelineId}"))
                            } catch (e: Exception) {
                                Notifier.notifyError("Open in Browser", "Failed to open URL: $url", project)
                            }
                        } ?: run {
                            Notifier.notifyWarning("Open in Browser", "No URL available for this project.", project)
                        }
                    }
                })
                popupMenu.addSeparator()
                popupMenu.add(JMenuItem("Retry Pipeline").apply {
                    addActionListener {
                        CoroutineScope(Dispatchers.IO).launch {
                            retryPipeline(selectedNode)
                        }
                    }
                })
                popupMenu.add(JMenuItem("Create Pipeline").apply {
                    addActionListener {
                        CoroutineScope(Dispatchers.IO).launch {
                            createPipeline(selectedNode)
                        }
                    }
                })
            }
        }

        popupMenu.show(e.component, e.x, e.y)
    }

    private fun retryPipeline(node: DefaultMutableTreeNode) {
        val data = node.userObject as? TreeNodeData
        if (data == null) {
            Notifier.notifyError("Retry Pipeline", "Invalid node data.", project)
            return
        }

        val projectId = data.id.toInt()
        val pipeline = gitLabClient.getLatestPipeline(projectId)
        if (pipeline == null) {
            Notifier.notifyError("Retry Pipeline", "No pipeline found to retry.", project)
            return
        }

        val project = this.project
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retriedPipeline = gitLabClient.retryPipeline(projectId, pipeline.id)
                withContext(Dispatchers.Main) {
                    Notifier.notifyInfo("Retrying Pipeline", "Pipeline ${data.name}:${retriedPipeline?.id} retried successfully.", project)
                    Utils.executeAfterDelay(3) {
                        refreshRepository(node)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error retrying pipeline", e)
                withContext(Dispatchers.Main) {
                    Notifier.notifyError("Retry Pipeline", e.message ?: "Unknown error", project)
                }
            }
        }
    }

    private fun createPipeline(node: DefaultMutableTreeNode) {
        val data = node.userObject as? TreeNodeData
        if (data == null) {
            Notifier.notifyError("Retry Pipeline", "Invalid node data.", project)
            return
        }

        val projectId = data.id.toInt()

        val project = this.project
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newPipeline = gitLabClient.createPipeline(projectId, "development") // or prompt for ref
                withContext(Dispatchers.Main) {
                    Notifier.notifyInfo("Creating Pipeline", "Pipeline ${data.name}:${newPipeline?.id} created successfully.", project)
                    refreshRepository(node)
                }
            } catch (e: Exception) {
                logger.error("Error creating pipeline", e)
                withContext(Dispatchers.Main) {
                    Notifier.notifyError("Create Pipeline", e.message ?: "Unknown error", project)
                }
            }
        }
    }

    private fun extractRepositoryName(node: DefaultMutableTreeNode): String {
        val text = node.userObject.toString()
        return text.removePrefix("Repository: ").split(" (")[0].trim()
    }

    private fun extractGroupName(node: DefaultMutableTreeNode): String {
        val text = node.userObject.toString()
        return text.removePrefix("Group: ").trim()
    }

    private fun refreshRepository(node: DefaultMutableTreeNode, saveCache: Boolean = true) {
        val nodeData = node.userObject as? TreeNodeData ?: run {
            Notifier.notifyError("Refresh Project", "Invalid node data.", project)
            return
        }

        if (!nodeData.isRepository()) {
            Notifier.notifyWarning("Refresh Repository", "Selected node is not a repository.", project)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pipeline = gitLabClient.getLatestPipeline(nodeData.id.toInt())

                withContext(Dispatchers.Main) {
                    if (pipeline != null) {
                        val oldStatus = nodeData.status
                        nodeData.status = pipeline.status
                        nodeData.pipelineId = pipeline.id.toString()
                        nodeData.displayName = getRepositoryDisplayName(nodeData.name ?: "", pipeline.status)

                        // Update the node's display text
                        node.userObject = nodeData
                        treeModel.nodeChanged(node)

                        if (oldStatus != pipeline.status) {
                            EventBus.publish(EventBus.Event("pipeline_status_changed", PipelineInfo(
                                projectId = nodeData.id,
                                repositoryName = nodeData.name ?: "",
                                oldStatus = oldStatus,
                                newStatus = pipeline.status
                            )))
                        }

                        if (saveCache) {
                            // Update the cache with the new tree state
                            cacheManager.saveCache(treeModel.root as DefaultMutableTreeNode)
                        }
                    } else {
                        Notifier.notifyWarning("No Pipeline", "No pipeline found for project '${nodeData.name}'.", project)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error refreshing project", e)
                withContext(Dispatchers.Main) {
                    Notifier.notifyError("Refresh Project", e.message ?: "Unknown error", project)
                }
            }
        }
    }

    fun dispose() {
        Utils.cancelAllTimers()
    }

    class Factory : ToolWindowFactory {

        override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
            val toolWindowPanel = GitLabPipelinesToolWindow(project)
            val contentFactory = service<ContentFactory>()
            val content = contentFactory.createContent(toolWindowPanel, "", false)
            toolWindow.contentManager.addContent(content)
        }
    }

}
