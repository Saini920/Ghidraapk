package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val GITHUB_TOKEN = stringPreferencesKey("github_token")
    private val GITHUB_REPO = stringPreferencesKey("github_repo")
    private val GITHUB_EVENT = stringPreferencesKey("github_event")
    private val API_SERVER = stringPreferencesKey("api_server")

    val githubTokenFlow: Flow<String> = context.dataStore.data.map { it[GITHUB_TOKEN] ?: "" }
    val githubRepoFlow: Flow<String> = context.dataStore.data.map { it[GITHUB_REPO] ?: "Saini920/Bottestgidra" }
    val githubEventFlow: Flow<String> = context.dataStore.data.map { it[GITHUB_EVENT] ?: "decompile-job" }
    val apiServerFlow: Flow<String> = context.dataStore.data.map { it[API_SERVER] ?: "" }

    suspend fun saveSettings(token: String, repo: String, event: String, server: String) {
        context.dataStore.edit { preferences ->
            preferences[GITHUB_TOKEN] = token
            preferences[GITHUB_REPO] = repo
            preferences[GITHUB_EVENT] = event
            preferences[API_SERVER] = server
        }
    }
}
