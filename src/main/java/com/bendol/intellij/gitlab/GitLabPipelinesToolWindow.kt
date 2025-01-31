package com.bendol.intellij.gitlab

import com.bendol.intellij.gitlab.cache.CacheData
import com.bendol.intellij.gitlab.cache.CacheManager
import com.bendol.intellij.gitlab.model.Filter
import com.bendol.intellij.gitlab.model.Group
import com.bendol.intellij.gitlab.model.GroupType
import com.bendol.intellij.gitlab.model.Images
import com.bendol.intellij.gitlab.model.PipelineInfo
import com.bendol.intellij.gitlab.model.Status
import com.bendol.intellij.gitlab.model.TreeNodeData
import com.bendol.intellij.gitlab.ui.tree.FilteredTreeModel
import com.bendol.intellij.gitlab.util.Notifier
import com.bendol.intellij.gitlab.util.Utils
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.ImageIcon
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

    private val logger: Logger = Logger.getInstance(GitLabPipelinesToolWindow::class.java)

    private val tree: Tree
    private val loadingLabel: JLabel = JLabel("")
    private val lastRefreshLabel: JLabel = JLabel("")
    private val statusComboBox = ComboBox(arrayOf("All", "Success", "Failed", "Skipped", "Running"))

    private lateinit var gitLabClient: GitLabClient
    private val cacheManager = CacheManager(project)
    private val settings = GitLabSettingsState.getInstance().state
    private var isRefreshing = AtomicBoolean(false)
    private var lockLoadingLabel = AtomicBoolean(false)
    private var baseLoadingText = ""
    private val hiddenNodes: MutableSet<String> = ConcurrentSet()
    private val expandedNodes: MutableSet<String> = ConcurrentSet()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        tree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Loading...")))
        setupUI()
        initialize()
    }

    private fun setupUI() {
        tree.isRootVisible = true
        tree.showsRootHandles = true

        // Input Panel
        val inputPanel = JPanel()
        inputPanel.layout = BoxLayout(inputPanel, BoxLayout.X_AXIS)
        inputPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        inputPanel.background = JBColor.background()

        statusComboBox.maximumSize = Dimension(Int.MAX_VALUE, 30)
        inputPanel.add(Box.createRigidArea(Dimension(5, 0)))
        inputPanel.add(statusComboBox)

        add(inputPanel)

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
                                foreground = Color.white//Color.decode("0xcccccc")
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

    private fun loadTreeViewHandlers() {
        tree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                if (e?.clickCount == 2) {
                    handleTreeDoubleClick()
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    handleTreeRightClick(e!!)
                }
            }
        })

        tree.addTreeExpansionListener(object : javax.swing.event.TreeExpansionListener {
            override fun treeExpanded(event: javax.swing.event.TreeExpansionEvent?) {
                val node = event?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
                val nodeData = node.userObject as? TreeNodeData ?: return

                if (nodeData.isGroup()) {
                    setExpanded(nodeData, true)

                    if (!isRefreshing.get() && nodeData.isStatusUnknown()) {
                        setLoadingText("Loading group '${nodeData.name}'...")
                        node.removeAllChildren()
                        CoroutineScope(Dispatchers.IO).launch {
                            loadSubgroupsAndRepositories(node, nodeData.id.toInt())

                            CoroutineScope(Dispatchers.Main).launch {
                                setLoadingText("")
                            }
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            if (!isRefreshing.get()) {
                                cacheManager.saveCache(getTreeModel().root as DefaultMutableTreeNode)
                            }
                        }
                    }
                }
            }

            override fun treeCollapsed(event: javax.swing.event.TreeExpansionEvent?) {
                val node = event?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
                val nodeData = node.userObject as? TreeNodeData ?: return
                if (nodeData.isGroup()) {
                    setExpanded(nodeData, false)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    if (!isRefreshing.get()) {
                        cacheManager.saveCache(getTreeModel().root as DefaultMutableTreeNode)
                    }
                }
            }
        })
    }

    private fun loadTreeModelHandlers() {
        val treeModel = getTreeModel()
        treeModel.addTreeModelListener(object : javax.swing.event.TreeModelListener {
            override fun treeNodesChanged(e: javax.swing.event.TreeModelEvent?) {
            }

            override fun treeNodesInserted(e: javax.swing.event.TreeModelEvent?) {
            }

            override fun treeNodesRemoved(e: javax.swing.event.TreeModelEvent?) {
            }

            override fun treeStructureChanged(e: javax.swing.event.TreeModelEvent?) {
            }
        })
    }

    private fun initialize(retry: Boolean = false) {
        val tokenManager = GitLabTokenManager.getInstance();
        val token = tokenManager.getToken()
        if (token.isNullOrEmpty()) {
            if (!retry) {
                Notifier.notifyWarning(
                    title = "GitLab Token Missing",
                    message = "Please provide a GitLab Personal Access Token in the settings",
                    project = project,
                    actions = mapOf(Pair("Settings") {
                        ShowSettingsUtil.getInstance().showSettingsDialog(
                            project,
                            "GitLab Pipelines"
                        )
                    })
                )
            }

            Utils.executeAfterDelay(5) {
                initialize(true)
            }
            return
        }

        if (!::gitLabClient.isInitialized) {
            gitLabClient = GitLabClient(tokenManager, settings.gitlabApiUrl)
        }

        if (settings.useEnvVarToken) {
            val envToken = System.getenv("GITLAB_TOKEN")
            if (!retry && !envToken.isNullOrEmpty()) {
                Notifier.notifyInfo(
                    "Environment Variable Detected",
                    "GitLab token loaded from environment variable. Restart the IDE if you update the environment variable.",
                    project)
            }
        }

        val cacheData: CacheData?
        try {
            cacheData = cacheManager.loadCache()
        } catch (e: Exception) {
            logger.error("Error loading cache", e)
            Utils.executeAfterDelay(2) {
                initialize(true)
            }
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (cacheData != null) {
                try {
                    CoroutineScope(Dispatchers.IO).launch {
                        loadTreeFromCache(cacheData)

                        if (cacheManager.isCacheExpired(settings.cacheRefreshSeconds)) {
                            refreshRootNode(saveCache = true, notify = false)
                        }

                        onLoaded()
                    }
                } catch (e: Exception) {
                    logger.error("Error loading cache", e)
                    Notifier.notifyError("GitLab Pipelines", "Failed to load cache.", project)
                    loadRootGroup()
                    onLoaded()
                }
            } else {
                loadRootGroup()
                onLoaded()
            }

            startScheduledEvents()
            loadEventHandlers()
        }
    }

    private fun startScheduledEvents() {
        // Periodic refresh
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(settings.refreshRateSeconds * 1000L)
                withContext(Dispatchers.IO) {
                    refreshRootNode()
                }
            }
        }
    }

    private fun loadEventHandlers() {
        CoroutineScope(Dispatchers.Main).launch {
            EventBus.events.collect { event -> when (event.name) {
                "pipeline_status_changed" -> {
                    val info = event.data as? PipelineInfo
                    info?.let {
                        if (it.oldStatus == Status.UNKNOWN || it.newStatus == Status.UNKNOWN)
                            return@collect

                        Notifier.notifyInfo(
                            "Pipeline Status Changed",
                            "Project '${it.repositoryName}' pipeline status: ${it.oldStatus} → ${it.newStatus}",
                            project
                        )
                    }
                }
            }}
        }
    }

    private suspend fun loadTreeFromCache(cacheData: CacheData? = null) = withContext(Dispatchers.Main) {
        setLoadingText("Loading from cache...")
        isRefreshing.set(false)

        try {
            val cachedData = cacheData ?: cacheManager.loadCache()
            if (cachedData?.treeData != null) {
                setTreeRootNode(cachedData.treeData, reload = false)
                refreshExpandedFromNode(getTreeModel().root as DefaultMutableTreeNode)
                val filter = getFilter(getTreeModel().root as DefaultMutableTreeNode)
                if (!filter.isDefault()) {
                    statusComboBox.selectedItem = filter.status!!.name.lowercase().capitalize()
                    filterTree(filter.status, refreshNodes = false)
                }
                refreshNode(getTreeModel().root as DefaultMutableTreeNode)

                setLoadingText("")
            } else {
                setLoadingText("")
                throw Exception("Invalid cache data.")
            }
        } catch (e: Exception) {
            logger.error("Error loading cache", e)
            setLoadingText("")
            Notifier.notifyError("GitLab Pipelines", "Failed to load cache.", project)
        }
    }

    private fun getFilter(node: DefaultMutableTreeNode): Filter {
        val data = node.userObject as? TreeNodeData
        if (data != null && data.isGroup()) {
            return data.filter
        }
        return Filter.DEFAULT
    }

    private fun loadUiHandlers() {
        statusComboBox.addActionListener {
            val selectedStatus = (statusComboBox.selectedItem as String).uppercase()
            try {
                val status = Status.valueOf(selectedStatus)
                statusComboBox.isEnabled = false
                CoroutineScope(Dispatchers.Main).launch {
                    filterTree(status, refreshNodes = false)
                    refreshNode(getTreeModel().root as DefaultMutableTreeNode, ignoreStatus = true)
                    statusComboBox.isEnabled = true
                }
            } catch (e: IllegalArgumentException) {
                if (selectedStatus != "ALL") {
                    logger.error("Invalid status: $selectedStatus")
                } else {
                    statusComboBox.isEnabled = false
                    CoroutineScope(Dispatchers.Main).launch {
                        removeTreeFilter()
                        refreshNode(getTreeModel().root as DefaultMutableTreeNode, ignoreStatus = true)
                        statusComboBox.isEnabled = true
                    }
                }
                return@addActionListener
            }
        }
    }

    private suspend fun onLoaded() = withContext(Dispatchers.Main) {
        loadTreeViewHandlers()
        loadTreeModelHandlers()
        loadUiHandlers()
    }

    private fun getTreeModel(): DefaultTreeModel {
        return tree.model as DefaultTreeModel
    }

    private fun setTreeRootNode(node: MutableTreeNode, reload: Boolean = true) {
        logger.debug("Populating tree from cache...")
        val treeModel = getTreeModel()
        treeModel.setRoot(node)

        if (reload) {
            treeModel.reload()
        }
    }

    private suspend fun setTreeModel(
        model: DefaultTreeModel,
        loadHandlers: Boolean = true,
        syncExpanded: Boolean = true,
        refreshNodes: Boolean = true
    ) {
        tree.model = model
        if (loadHandlers) {
            loadTreeModelHandlers()
        }

        if (syncExpanded) {
            refreshExpandedFromSet(model.root as DefaultMutableTreeNode)
        }

        if (refreshNodes) {
            refreshNode(model.root as DefaultMutableTreeNode)
        }
    }

    private fun refreshExpandedFromNode(node: DefaultMutableTreeNode) {
        val data = node.userObject as? TreeNodeData
        if (data != null && data.isGroup()) {
            setExpanded(data, data.isExpanded)
        }

        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            refreshExpandedFromNode(child)
        }
    }

    private fun refreshExpandedFromSet(node: DefaultMutableTreeNode) {
        val data = node.userObject as? TreeNodeData
        if (data != null && data.isGroup()) {
            setExpanded(data, expandedNodes.contains(data.id))
        }

        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            refreshExpandedFromSet(child)
        }
    }

    private suspend fun loadRootGroup() {
        setLoadingText("Loading root group...")

        withContext(Dispatchers.IO) {
            if (isRefreshing.get()) {
                Notifier.notifyWarning("GitLab Pipelines", "Already refreshing groups.", project)
                return@withContext
            }

            try {
                val group = gitLabClient.searchGroup(settings.groupName)
                withContext(Dispatchers.Main) {
                    if (group != null) {
                        populateRootGroup(group)
                        Notifier.notifyInfo("GitLab Pipelines", "Loaded group: ${group.name}", project)
                    } else {
                        Notifier.notifyError("GitLab Pipelines", "Group not found: ${settings.groupName}", project)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error loading root group", e)
                Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
            } finally {
                setLoadingText("")
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
    }

    private suspend fun clearCacheAndReload() {
        cacheManager.clearCache()
        loadRootGroup()
        refreshRootNode()
        Notifier.notifyInfo("GitLab Pipelines", "Cache cleared and reloaded.", project)
    }

    private fun isGroupNode(node: DefaultMutableTreeNode): Boolean {
        val data = node.userObject as? TreeNodeData
        return data != null && data.isGroup()
    }

    private fun isRepositoryNode(node: DefaultMutableTreeNode): Boolean {
        val data = node.userObject as? TreeNodeData
        return data != null && data.isRepository()
    }

    /**
     * Refreshes the subgroups of a given group node.
     *
     * @param node The repository node.
     * @param groupId The group ID.
     * @param saveCache Whether to save the cache after refreshing. Will save the cache after all subgroups are loaded.
     * @param reload Whether to reload the tree model. Will wait for all subgroups to be loaded before reloading.
     */
    private suspend fun loadSubgroups(
        node: DefaultMutableTreeNode,
        groupId: Int,
        saveCache: Boolean = true,
        reload: Boolean = true
    ) = withContext(Dispatchers.IO) {
        try {
            if (!isGroupNode(node)) {
                logger.warn("loadSubgroups: Not a group node. ${node.userObject}")
                return@withContext
            }

            val subgroups = gitLabClient.getSubgroups(groupId)
                .filter { it.id.toString() !in settings.ignoredGroups }

            val insertJobs = subgroups.map { subgroup ->
                async {
                    val subgroupNode = DefaultMutableTreeNode(TreeNodeData(
                        id = subgroup.id.toString(),
                        type = GroupType.GROUP,
                        status = Status.UNKNOWN,
                        webUrl = subgroup.web_url,
                        name = subgroup.name,
                        displayName = getGroupDisplayName(subgroup.name)
                    ))
                    subgroupNode.add(DefaultMutableTreeNode("Loading..."))

                    withContext(Dispatchers.Main) {
                        getTreeModel().insertNodeInto(subgroupNode, node, 0)
                    }
                }
            }

            insertJobs.awaitAll()

            if (reload) {
                getTreeModel().reload(node)
            }

            if (saveCache) {
                cacheManager.saveCache(getTreeModel().root as DefaultMutableTreeNode)
            }
        } catch (e: Exception) {
            logger.error("Error loading subgroups", e)
            Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
        }
    }

    /**
     * Refreshes the repositories of a given group node.
     *
     * @param node The group node.
     * @param repositoryId The group ID.
     * @param saveCache Whether to save the cache after refreshing. Will save the cache after all repositories are loaded.
     * @param reload Whether to reload the tree model. Will wait for all repositories to be loaded before reloading.
     * @return Whether the repositories were loaded asynchronously.
     */
    private suspend fun loadRepositories(
        node: DefaultMutableTreeNode,
        repositoryId: Int,
        saveCache: Boolean = true,
        reload: Boolean = true
    ) = withContext(Dispatchers.IO) {
        try {
            if (!isGroupNode(node)) {
                logger.warn("loadRepositories: Not a group node ${node.userObject}")
                return@withContext
            }

            val repositories = gitLabClient.getGroupRepositories(repositoryId)

            val insertJobs = repositories.map { repository -> async {
                val pipeline = gitLabClient.getLatestPipeline(repository.id)
                if (pipeline?.id == null) {
                    logger.debug("No pipeline found for repository ${repository.name}")
                    return@async
                }
                val status = pipeline.status

                val repoNode = DefaultMutableTreeNode(TreeNodeData(
                    id = repository.id.toString(),
                    type = GroupType.REPOSITORY,
                    status = status,
                    webUrl = repository.web_url,
                    parentGroup = extractGroupName(node),
                    pipelineId = pipeline.id.toString(),
                    name = repository.name,
                    displayName = getRepositoryDisplayName(repository.name, status)
                ))

                withContext(Dispatchers.Main) {
                    getTreeModel().insertNodeInto(repoNode, node, node.childCount)
                }
            }}

            insertJobs.awaitAll()

            if (reload) {
                getTreeModel().reload(node)
            }

            if (saveCache) {
                cacheManager.saveCache(getTreeModel().root as DefaultMutableTreeNode)
            }
        } catch (e: Exception) {
            logger.error("Error loading repositories", e)
            Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
        }
    }

    private suspend fun loadSubgroupsAndRepositories(
        node: DefaultMutableTreeNode,
        groupId: Int,
        saveCache: Boolean = true,
        loadTypes: Set<GroupType> = setOf(GroupType.GROUP, GroupType.REPOSITORY)
    ) = withContext(Dispatchers.Main) {
        try {
            val jobs = mutableListOf<Deferred<Unit>>()

            if (loadTypes.contains(GroupType.GROUP)) {
                jobs.add(async { loadSubgroups(node, groupId, saveCache = false, reload = false) })
            }

            if (loadTypes.contains(GroupType.REPOSITORY)) {
                jobs.add(async { loadRepositories(node, groupId, saveCache = false, reload = false) })
            }

            jobs.awaitAll()

            getTreeModel().reload(node)

            val data = node.userObject as TreeNodeData
            if (data.isGroup()) {
                data.status = Status.SUCCESS
                if (isExpanded(data)) {
                    if (!tree.isExpanded(TreePath(node.path))) {
                        tree.expandPath(TreePath(node.path))
                    }
                } else {
                    if (tree.isExpanded(TreePath(node.path))) {
                        tree.collapsePath(TreePath(node.path))
                    }
                }
            }

            if (saveCache) {
                cacheManager.saveCache(getTreeModel().root as DefaultMutableTreeNode)
            }
        } catch (e: Exception) {
            logger.error("Error loading subgroups/repositories", e)
            Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
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

    private suspend fun refreshRootNode(saveCache: Boolean = true, notify: Boolean = false) {
        setLoadingText("Refreshing groups...", makeBaseText = true)
        if (isRefreshing.get()) {
            Notifier.notifyWarning("GitLab Pipelines", "Already refreshing groups.", project)
            return
        }

        try {
            val rootNode = getTreeModel().root as DefaultMutableTreeNode
            refreshNode(rootNode, false, notify, true)

            if (saveCache) {
                cacheManager.saveCache(rootNode)
            }
        } catch (e: Exception) {
            logger.error("Error refreshing groups", e)
            Notifier.notifyError("GitLab Pipelines", e.message ?: "Unknown error", project)
        } finally {
            setLoadingText("", makeBaseText = true)
        }
    }
    
    private fun setLoadingText(text: String, makeBaseText: Boolean = false) {
        if (lockLoadingLabel.get())
            return
        if (makeBaseText)
            baseLoadingText = text

        loadingLabel.text = text.ifEmpty { baseLoadingText }
    }

    private suspend fun filterTree(
        status: Status,
        node: DefaultMutableTreeNode = getTreeModel().root as DefaultMutableTreeNode,
        syncExpanded: Boolean = false,
        loadHandlers: Boolean = true,
        refreshNodes: Boolean = true
    ) = withContext(Dispatchers.Main) {
        val filteredModel = FilteredTreeModel(getTreeModel()) { n ->
            val data = n.userObject as? TreeNodeData
            if (data == null || !data.isRepository()) {
                true
            } else {
                !hiddenNodes.contains(data.id)
            }
        }.filter(node, filter = Filter(status))

        if (filteredModel != null) {
            setTreeModel(filteredModel,
                syncExpanded = syncExpanded,
                loadHandlers = loadHandlers,
                refreshNodes = refreshNodes)
        }
    }

    private suspend fun removeTreeFilter() {
        val treeModel = tree.model
        if (treeModel is FilteredTreeModel) {
            val unfilteredModel = treeModel.unfilteredModel
            val root = unfilteredModel.root as DefaultMutableTreeNode
            val data = root.userObject as? TreeNodeData
            if (data != null && data.isGroup()) {
                data.filter = Filter.DEFAULT
            }
            setTreeModel(treeModel.unfilteredModel, loadHandlers = false)
        }
    }

    private suspend fun refreshNode(
        node: DefaultMutableTreeNode,
        saveCache: Boolean = true,
        notify: Boolean = false,
        ignoreStatus: Boolean = false,
        force: Boolean = false
    ) {
        if (!force && isRefreshing.get()) {
            logger.warn("refreshGroupNode: Already refreshing, skipping.")
            return
        }
        isRefreshing.set(true)

        if (node.userObject !is TreeNodeData) {
            return
        }

        val treeModel = getTreeModel()
        val isRoot = node == treeModel.root
        val data = node.userObject as? TreeNodeData

        try {
            if (isRoot && notify) {
                setLoadingText("Refreshing group '${data?.name?:node.toString()}'...", makeBaseText = true)
            }

            if (data != null) {
                if (node.childCount < 1 && data.isGroup()) {
                    data.status = Status.UNKNOWN
                    getTreeModel().insertNodeInto(DefaultMutableTreeNode("Loading..."), node, node.childCount)

                    if (isExpanded(data)) {
                        if (!tree.isExpanded(TreePath(node.path))) {
                            tree.expandPath(TreePath(node.path))
                        }
                    } else {
                        if (tree.isExpanded(TreePath(node.path))) {
                            tree.collapsePath(TreePath(node.path))
                        }
                    }
                } else {
                    if (isExpanded(data)) {
                        if (!tree.isExpanded(TreePath(node.path))) {
                            tree.expandPath(TreePath(node.path))
                        }

                        if (ignoreStatus || data.status == Status.UNKNOWN) {
                            val groupName = data.name ?: extractGroupName(node)
                            val group = gitLabClient.searchGroup(groupName)
                            if (group != null) {
                                withContext(Dispatchers.Main) {
                                    node.removeAllChildren()
                                }
                                loadSubgroupsAndRepositories(node, group.id, saveCache = saveCache)
                            }
                        }
                    } else {
                        if (data.isGroup()) {
                            if (tree.isExpanded(TreePath(node.path))) {
                                tree.collapsePath(TreePath(node.path))
                            }
                        }
                        else if (data.isRepository() && (ignoreStatus || data.status == Status.UNKNOWN)) {
                            refreshRepository(node, saveCache)
                        }
                    }
                }
            }

            for (i in 0..<node.childCount) {
                val child = node.getChildAt(i) as DefaultMutableTreeNode
                if (child.userObject !is TreeNodeData) {
                    logger.debug("refreshGroupNode: Invalid node data, skipping '${child}'.")
                    continue
                }

                refreshNode(child,
                    saveCache = saveCache,
                    notify = false,
                    ignoreStatus = ignoreStatus,
                    force = true)
            }

            if (isRoot) {
                withContext(Dispatchers.Main) {
                    updateLastRefreshLabel(notify)
                }
            }
        } finally {
            isRefreshing.set(false)

            withContext(Dispatchers.Main) {
                setLoadingText("", makeBaseText = notify)
            }
        }
    }

    private fun updateLastRefreshLabel(notify: Boolean = false) {
        val dateFormat = java.text.SimpleDateFormat("MM/dd HH:mm")
        val lastRefreshTime = dateFormat.format(java.util.Date())
        lastRefreshLabel.text = "Last refresh: $lastRefreshTime"
        if (notify) {
            Notifier.notifyInfo("GitLab Pipelines", "Groups refreshed.", project)
        }
    }

    private fun removeRepositories(node: DefaultMutableTreeNode) {
        val toRemove = mutableListOf<DefaultMutableTreeNode>()
        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            if (child.userObject is TreeNodeData && (child.userObject as TreeNodeData).isRepository()) {
                toRemove.add(child)
            }
        }

        toRemove.forEach { getTreeModel().removeNodeFromParent(it) }
    }

    private fun removeGroups(node: DefaultMutableTreeNode) {
        val toRemove = mutableListOf<DefaultMutableTreeNode>()
        for (i in 0..<node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            if (child.userObject is TreeNodeData && (child.userObject as TreeNodeData).isGroup()) {
                toRemove.add(child)
            }
        }

        toRemove.forEach { getTreeModel().removeNodeFromParent(it) }
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
                java.awt.Desktop.getDesktop().browse(java.net.URI("$it/-/pipelines/${data.pipelineId}"))
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
                        CoroutineScope(Dispatchers.Main).launch {
                            refreshNode(selectedNode, notify = true, ignoreStatus = true)
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
                popupMenu.addSeparator()
                popupMenu.add(JMenuItem("Clear Cache").apply {
                    addActionListener {
                        CoroutineScope(Dispatchers.Main).launch {
                            clearCacheAndReload()
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
                    Utils.executeAfterDelay(this, 3, Dispatchers.IO) {
                        refreshRepository(node)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error retrying pipeline", e)
                Notifier.notifyError("Retry Pipeline", e.message ?: "Unknown error", project)
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
                val newPipeline = gitLabClient.createPipeline(projectId, "development") // TODO prompt for ref
                withContext(Dispatchers.Main) {
                    Notifier.notifyInfo("Creating Pipeline", "Pipeline ${data.name}:${newPipeline?.id} created successfully.", project)
                    refreshRepository(node)
                }
            } catch (e: Exception) {
                logger.error("Error creating pipeline", e)
                Notifier.notifyError("Create Pipeline", e.message ?: "Unknown error", project)
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

    private suspend fun refreshRepository(
        node: DefaultMutableTreeNode,
        saveCache: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val nodeData = node.userObject as? TreeNodeData ?: run {
            Notifier.notifyError("Refresh Project", "Invalid node data.", project)
            return@withContext
        }

        if (!nodeData.isRepository()) {
            Notifier.notifyWarning("Refresh Repository", "Selected node is not a repository.", project)
            return@withContext
        }

        if (hiddenNodes.contains(nodeData.id)) {
            logger.debug("Skipping refresh for hidden node: ${nodeData.name}")
            return@withContext
        }

        withContext(Dispatchers.Main) {
            setLoadingText("Refreshing project '${nodeData.name}'...")
        }

        try {
            val pipeline = gitLabClient.getLatestPipeline(nodeData.id.toInt())
            if (pipeline != null) {
                val oldStatus = nodeData.status
                nodeData.status = pipeline.status
                nodeData.pipelineId = pipeline.id.toString()
                nodeData.displayName = getRepositoryDisplayName(nodeData.name ?: "", pipeline.status)

                withContext(Dispatchers.Main) {
                    // Update the node's display text
                    node.userObject = nodeData
                    getTreeModel().nodeChanged(node)

                    if (oldStatus != pipeline.status) {
                        EventBus.publish(
                            EventBus.Event(
                                "pipeline_status_changed", PipelineInfo(
                                    projectId = nodeData.id,
                                    repositoryName = nodeData.name ?: "",
                                    oldStatus = oldStatus,
                                    newStatus = pipeline.status
                                )
                            )
                        )
                    }

                    if (saveCache) {
                        cacheManager.saveCache(getTreeModel().root as DefaultMutableTreeNode)
                    }

                    setLoadingText("")
                }
            } else {
                Notifier.notifyWarning("No Pipeline",
                    "No pipeline found for project '${nodeData.name}'.", project)
            }
        } catch (e: Exception) {
            logger.error("Error refreshing project", e)
            withContext(Dispatchers.Main) {
                setLoadingText("")
                Notifier.notifyError("Refresh Project", e.message ?: "Unknown error", project)
            }
        }
    }

    private fun setExpanded(node: DefaultMutableTreeNode, expanded: Boolean) {
        val data = node.userObject as? TreeNodeData
        return data?.let { setExpanded(it, expanded) } ?: Unit
    }

    private fun setExpanded(data: TreeNodeData, expanded: Boolean) {
        if (expanded) {
            data.isExpanded = true
            expandedNodes.add(data.id)
        } else {
            data.isExpanded = false
            expandedNodes.remove(data.id)
        }
    }

    private fun isExpanded(node: DefaultMutableTreeNode): Boolean {
        val data = node.userObject as? TreeNodeData
        return data != null && isExpanded(data)
    }

    private fun isExpanded(data: TreeNodeData): Boolean {
        return data.isGroup() && expandedNodes.contains(data.id)
    }

    class Factory : ToolWindowFactory {

        override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
            val toolWindowPanel = GitLabPipelinesToolWindow(project)
            val contentFactory = service<ContentFactory>()
            val content = contentFactory.createContent(toolWindowPanel, "", false)
            toolWindow.setIcon(Images.ProjectIcon)
            toolWindow.contentManager.addContent(content)
        }
    }

}
