package com.bendol.intellij.gitlab

import com.bendol.intellij.gitlab.json.MutableTreeNodeDeserializer
import com.bendol.intellij.gitlab.json.MutableTreeNodeSerializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Paths
import javax.swing.tree.DefaultMutableTreeNode

class CacheManager(cacheFileName: String = "cache.json") {
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

    fun saveCache(data: DefaultMutableTreeNode) {
        val cacheData = CacheData(data, System.currentTimeMillis())
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText(gson.toJson(cacheData))
    }

    fun loadCache(): CacheData? {
        return if (cacheFile.exists()) {
            gson.fromJson(cacheFile.readText(), CacheData::class.java)
        } else {
            null
        }
    }

    fun clearCache() {
        cacheFile.delete()
    }

    fun isCacheExpired(refreshSeconds: Int): Boolean {
        val cacheData = loadCache() ?: return true
        val age = (System.currentTimeMillis() - cacheData.timestamp) / 1000
        return age >= refreshSeconds
    }
}
