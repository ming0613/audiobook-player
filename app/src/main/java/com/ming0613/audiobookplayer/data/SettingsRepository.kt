package com.ming0613.audiobookplayer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 简单配置的存取（目前只有书库根目录）。
 * DataStore 是 SharedPreferences 的现代替代品，基于协程，读写不卡 UI。
 */
class SettingsRepository(private val context: Context) {

    private val keyRootTreeUri = stringPreferencesKey("root_tree_uri")

    /** 书库根目录的 SAF Uri；null 表示用户还没选过 */
    val rootTreeUri: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[keyRootTreeUri] }

    suspend fun saveRootTreeUri(uri: String) {
        context.dataStore.edit { prefs -> prefs[keyRootTreeUri] = uri }
    }
}
