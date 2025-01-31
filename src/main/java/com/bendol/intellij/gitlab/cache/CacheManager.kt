package com.bendol.intellij.gitlab.cache

import com.bendol.intellij.gitlab.json.MutableTreeNodeDeserializer
import com.bendol.intellij.gitlab.json.MutableTreeNodeSerializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.tree.DefaultMutableTreeNode

class CacheManager(project: Project, cacheFileName: String = "cache.json") {
    private val logger: Logger = Logger.getInstance(CacheManager::class.java)

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(DefaultMutableTreeNode::class.java, MutableTreeNodeSerializer())
        .registerTypeAdapter(DefaultMutableTreeNode::class.java, MutableTreeNodeDeserializer())
        .setPrettyPrinting()
        .create()

    private val cacheFile: File = Paths.get(
        System.getProperty("user.home"),
        ".gitlab_pipelines_plugin",
        cacheFileName
    ).toFile()

    private val isSaving = AtomicBoolean(false)
    private val isLoading = AtomicBoolean(false)

    suspend fun saveCache(
        data: DefaultMutableTreeNode,
        throwOnError: Boolean = false
    ) = withContext(Dispatchers.Main) {
        if (isSaving.get()) {
            return@withContext
        }
        try {
            isSaving.set(true)
            val cacheData = CacheData(data, System.currentTimeMillis())
            cacheFile.parentFile.mkdirs()
            cacheFile.writeText(gson.toJson(cacheData))
        } catch (e: Exception) {
            logger.error("Failed to save cache", e)
            if (throwOnError) {
                throw e
            }
        } finally {
            isSaving.set(false)
        }
    }

    fun loadCache(): CacheData? {
        try {
            if (isLoading.get()) {
                throw IllegalStateException("Cache is already being loaded")
            }

            isLoading.set(true)
            return if (cacheFile.exists()) {
                gson.fromJson(cacheFile.readText(), CacheData::class.java)
            } else {
                null
            }
        } finally {
            isLoading.set(false)
        }
    }

    fun clearCache() {
        try {
            cacheFile.delete()
        } catch (e: Exception) {
            logger.error("Failed to clear cache", e)
        }
    }

    fun isCacheExpired(refreshSeconds: Int): Boolean {
        try {
            val cacheData = loadCache() ?: return true
            val age = (System.currentTimeMillis() - (cacheData.timestamp ?: System.currentTimeMillis())) / 1000
            return age >= refreshSeconds
        } catch (e: Exception) {
            logger.error("Failed to check cache expiration", e)
            return true
        }
    }
}
