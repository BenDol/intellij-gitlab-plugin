package com.bendol.intellij.gitlab

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class GitLabTokenManager {

    companion object {
        fun getInstance(): GitLabTokenManager = service()
    }

    /**
     * Retrieves the GitLab token from the Credential Store.
     * First, attempts to load from environment variable.
     * If not present, retrieves from the secure store.
     */
    fun getToken(): String? {
        // Attempt to load from environment variable
        val envToken = System.getenv("GITLAB_TOKEN")
        if (!envToken.isNullOrEmpty()) {
            return envToken
        }

        // If not in environment, retrieve from Credential Store
        val attributes = CredentialAttributes("GitLabPipelinesPlugin", "GitLabToken")
        val credentials = PasswordSafe.instance.get(attributes)
        return credentials?.getPasswordAsString()
    }

    /**
     * Saves the GitLab token to the Credential Store.
     * @param token The GitLab personal access token to store.
     */
    fun setToken(token: String) {
        val attributes = CredentialAttributes("GitLabPipelinesPlugin", "GitLabToken")
        val credentials = Credentials("GitLabToken", token)
        PasswordSafe.instance.set(attributes, credentials)
    }

    /**
     * Removes the GitLab token from the Credential Store.
     */
    fun clearToken() {
        val attributes = CredentialAttributes("GitLabPipelinesPlugin", "GitLabToken")
        PasswordSafe.instance.setPassword(attributes, "")
    }
}
