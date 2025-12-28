package com.lmstudio.mobile.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SERVER_PORT = intPreferencesKey("server_port")
        val API_KEY = stringPreferencesKey("api_key")
        val AUTO_LOAD_MODEL = booleanPreferencesKey("auto_load_model")
        val N_THREADS = intPreferencesKey("n_threads")
        val N_GPU_LAYERS = intPreferencesKey("n_gpu_layers")
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val AUTO_SAVE_CHATS = booleanPreferencesKey("auto_save_chats")
        val LAST_USED_MODEL_PATH = stringPreferencesKey("last_used_model_path")
    }

    val darkMode: Flow<Boolean> = dataStore.data.map { it[DARK_MODE] ?: false }
    val serverPort: Flow<Int> = dataStore.data.map { it[SERVER_PORT] ?: 8080 }
    val apiKey: Flow<String?> = dataStore.data.map { it[API_KEY] }
    val autoLoadModel: Flow<Boolean> = dataStore.data.map { it[AUTO_LOAD_MODEL] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DARK_MODE] = enabled }
    }

    suspend fun setServerPort(port: Int) {
        dataStore.edit { preferences -> preferences[SERVER_PORT] = port }
    }

    suspend fun setApiKey(key: String?) {
        dataStore.edit { preferences ->
            if (key != null) {
                preferences[API_KEY] = key
            } else {
                preferences.remove(API_KEY)
            }
        }
    }

    suspend fun setAutoLoadModel(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_LOAD_MODEL] = enabled }
    }

    suspend fun getNThreads(): Int {
        return dataStore.data.first()[N_THREADS] ?: 4
    }

    suspend fun setNThreads(threads: Int) {
        dataStore.edit { preferences -> preferences[N_THREADS] = threads }
    }

    suspend fun getNGpuLayers(): Int {
        return dataStore.data.first()[N_GPU_LAYERS] ?: 0
    }

    suspend fun setNGpuLayers(layers: Int) {
        dataStore.edit { preferences -> preferences[N_GPU_LAYERS] = layers }
    }

    suspend fun getContextSize(): Int {
        return dataStore.data.first()[CONTEXT_SIZE] ?: 2048
    }

    suspend fun setContextSize(size: Int) {
        dataStore.edit { preferences -> preferences[CONTEXT_SIZE] = size }
    }

    suspend fun isDarkTheme(): Boolean {
        return dataStore.data.first()[DARK_MODE] ?: false
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DARK_MODE] = enabled }
    }

    suspend fun isAutoSaveChats(): Boolean {
        return dataStore.data.first()[AUTO_SAVE_CHATS] ?: true
    }

    suspend fun setAutoSaveChats(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_SAVE_CHATS] = enabled }
    }

    suspend fun getLastUsedModelPath(): String? {
        return dataStore.data.first()[LAST_USED_MODEL_PATH]
    }

    suspend fun setLastUsedModelPath(path: String) {
        dataStore.edit { preferences -> preferences[LAST_USED_MODEL_PATH] = path }
    }

    suspend fun hasInferenceSettingsSet(): Boolean {
        val prefs = dataStore.data.first()
        return prefs.contains(N_THREADS) || prefs.contains(N_GPU_LAYERS) || prefs.contains(CONTEXT_SIZE)
    }
}
