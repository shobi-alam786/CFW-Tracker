package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.AppNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("SELECT * FROM app_notification ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Query("SELECT * FROM app_notification WHERE targetRole = :role ORDER BY createdAt DESC")
    fun getNotificationsForRole(role: String): Flow<List<AppNotification>>

    /**
     * All notifications regardless of role. Used until the app has real per-device/per-user
     * login (see Batch 1 assumptions) — until then, everyone shares one device's notification
     * feed, so a single unified list is more useful than role-siloed ones nobody can see.
     */
    @Query("SELECT * FROM app_notification ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Query("SELECT COUNT(*) FROM app_notification WHERE targetRole = :role AND isRead = 0")
    fun getUnreadCountForRole(role: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM app_notification WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE app_notification SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE app_notification SET isRead = 1 WHERE targetRole = :role")
    suspend fun markAllAsReadForRole(role: String)

    @Query("UPDATE app_notification SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("UPDATE app_notification SET isRead = 1")
    suspend fun markAllAsRead()
}
