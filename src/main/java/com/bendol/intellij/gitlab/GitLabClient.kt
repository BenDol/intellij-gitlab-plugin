package com.bendol.intellij.gitlab

import com.bendol.intellij.gitlab.json.StatusDeserializer
import com.bendol.intellij.gitlab.model.Group
import com.bendol.intellij.gitlab.model.Pipeline
import com.bendol.intellij.gitlab.model.Repository
import com.bendol.intellij.gitlab.model.Status
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GitLabClient(
    private val tokenManager: GitLabTokenManager,
    private val apiUrl: String = "https://gitlab.com/api/v4"
) {
    private val client = OkHttpClient()
    private val gson = GsonBuilder()
        .registerTypeAdapter(Status::class.java, StatusDeserializer())
        .create()

    private fun buildRequest(url: String, params: Map<String, String> = emptyMap()): Request {
        val urlBuilder = url.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid URL: $url")

        params.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        return Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Private-Token", tokenManager.getToken()!!)
            .build()
    }

    fun searchGroup(groupName: String): Group? {
        val url = "$apiUrl/groups"
        val request = buildRequest(url, mapOf("search" to groupName))
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw Exception("Unexpected code $response")

            val listType = object : TypeToken<List<Group>>() {}.type
            val groups: List<Group> = gson.fromJson(response.body?.charStream(), listType)
            return groups.find {
                it.name.equals(groupName, ignoreCase = true) || it.path.equals(groupName, ignoreCase = true)
            }
        }
    }

    fun getSubgroups(groupId: Int): List<Group> {
        val url = "$apiUrl/groups/$groupId/subgroups"
        val request = buildRequest(url)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected code $response")
            val listType = object : TypeToken<List<Group>>() {}.type
            return gson.fromJson(response.body?.charStream(), listType)
        }
    }

    fun getGroupRepositories(groupId: Int): List<Repository> {
        val url = "$apiUrl/groups/$groupId/projects"
        val request = buildRequest(url, mapOf("include_subgroups" to "false"))
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected code $response")
            val listType = object : TypeToken<List<Repository>>() {}.type
            return gson.fromJson(response.body?.charStream(), listType)
        }
    }

    fun getLatestPipeline(projectId: Int, branch: String? = null): Pipeline? {
        var url = "$apiUrl/projects/$projectId/pipelines"
        val params = mutableMapOf<String, String>()
        if (branch != null) {
            params["ref"] = branch
        }
        url += "?order_by=id&sort=desc&per_page=1"
        val request = buildRequest(url, params)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404 || response.code == 403) return null
                throw Exception("Unexpected code $response")
            }
            val pipelines: List<Pipeline> = gson.fromJson(response.body?.charStream(), object : TypeToken<List<Pipeline>>() {}.type)
            return pipelines.firstOrNull()
        }
    }

    fun retryPipeline(projectId: Int, pipelineId: Int): Pipeline? {
        val url = "$apiUrl/projects/$projectId/pipelines/$pipelineId/retry"
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Private-Token", tokenManager.getToken()!!)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to retry pipeline: $response")
            return gson.fromJson(response.body?.charStream(), Pipeline::class.java)
        }
    }

    fun createPipeline(projectId: Int, ref: String = "development"): Pipeline? {
        val url = "$apiUrl/projects/$projectId/pipeline"
        val json = gson.toJson(mapOf("ref" to ref))
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Private-Token", tokenManager.getToken()!!)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to create pipeline: $response")
            return gson.fromJson(response.body?.charStream(), Pipeline::class.java)
        }
    }

    fun getRepositoryByName(groupName: String, repoName: String): Repository? {
        val group = searchGroup(groupName) ?: return null
        val repositories = getGroupRepositories(group.id)
        return repositories.find { it.name.equals(repoName, ignoreCase = true) }
    }
}
