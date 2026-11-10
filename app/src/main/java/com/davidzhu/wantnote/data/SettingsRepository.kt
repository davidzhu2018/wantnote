package com.davidzhu.wantnote.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: SettingsDao) {
    fun observe(): Flow<AppSettings> = dao.observe().map { it ?: AppSettings.DEFAULT }

    suspend fun get(): AppSettings = dao.get() ?: AppSettings.DEFAULT

    suspend fun ensureDefault() {
        if (dao.get() == null) {
            dao.upsert(AppSettings.DEFAULT)
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = dao.get() ?: AppSettings.DEFAULT
        dao.upsert(transform(current).copy(id = 1))
    }
}
