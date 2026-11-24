package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 0.11.0：本地持久化的 Dinox 卡片盒名称缓存。
 *
 * - 冷启动预加载与每两小时后台刷新结果写入本表；
 * - 所有 Dinox 卡片盒选择器（导入、发送到Dinox、卡片盒同步规则）优先直接读本表；
 * - 刷新失败绝不清空本表，保留上次成功的卡片盒列表。
 */
@Entity(tableName = "dinox_cached_boxes")
data class DinoxCachedBox(
    @PrimaryKey val name: String,
    val fetchedAt: Long,
)

@Dao
interface DinoxBoxDao {
    @Query("SELECT * FROM dinox_cached_boxes ORDER BY name ASC")
    fun observeAll(): Flow<List<DinoxCachedBox>>

    @Query("SELECT * FROM dinox_cached_boxes ORDER BY name ASC")
    suspend fun getAll(): List<DinoxCachedBox>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(boxes: List<DinoxCachedBox>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(boxes: List<DinoxCachedBox>)

    @Query("DELETE FROM dinox_cached_boxes")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(boxes: List<DinoxCachedBox>) {
        deleteAll()
        if (boxes.isNotEmpty()) {
            upsertAll(boxes)
        }
    }
}
