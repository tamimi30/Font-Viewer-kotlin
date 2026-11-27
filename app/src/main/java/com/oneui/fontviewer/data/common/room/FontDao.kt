package com.oneui.fontviewer.data.common.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface FontDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(font: FontEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(fonts: List<FontEntity>): List<Long>

    @Update
    fun update(font: FontEntity): Int

    @Delete
    fun delete(font: FontEntity): Int

    // حذف مجموعة صفوف دفعة واحدة (تُستخدم لإزالة خطوط النظام التي لم تعد موجودة في النظام)
    @Delete
    fun deleteAll(fonts: List<FontEntity>): Int



    @Query("DELETE FROM fonts WHERE path = :path")
    fun deleteByPath(path: String): Int



    @Query("DELETE FROM fonts WHERE is_system_font = 1")
    fun deleteAllSystemFonts(): Int



    @Query("SELECT * FROM fonts WHERE path = :path LIMIT 1")
    fun getFontByPath(path: String): LiveData<FontEntity>



    @Query("SELECT * FROM fonts")
    fun getAllFonts(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    fun getSystemFonts(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    fun getSystemFontsSync(): List<FontEntity>

    @Query("SELECT * FROM fonts WHERE is_system_font = 0 AND is_trashed = 0 ORDER BY file_name ASC")
    fun getLocalFonts(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = 0 AND is_trashed = 0 ORDER BY file_name ASC")
    fun getLocalFontsSync(): List<FontEntity>

    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY file_name ASC")
    fun getFontsSortedByName(isSystem: Boolean): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY file_name DESC")
    fun getFontsSortedByNameDesc(isSystem: Boolean): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY last_modified ASC")
    fun getFontsSortedByDate(isSystem: Boolean): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY last_modified DESC")
    fun getFontsSortedByDateDesc(isSystem: Boolean): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY size ASC")
    fun getFontsSortedBySize(isSystem: Boolean): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY size DESC")
    fun getFontsSortedBySizeDesc(isSystem: Boolean): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    fun searchFonts(isSystem: Boolean, query: String): LiveData<List<FontEntity>>

    @Query("UPDATE fonts SET is_cached = :isCached, updated_at = :timestamp WHERE path = :path")
    fun updateCacheStatus(path: String, isCached: Boolean, timestamp: Long): Int

    @Query("UPDATE fonts SET last_access_time = :accessTime, " +
           "access_count = access_count + 1, updated_at = :timestamp " +
           "WHERE path = :path")
    fun recordAccess(path: String, accessTime: Long, timestamp: Long): Int

    @Query("SELECT * FROM fonts WHERE is_cached = 1 ORDER BY last_access_time DESC")
    fun getCachedFonts(): List<FontEntity>

    @Query("SELECT COUNT(*) FROM fonts")
    fun getTotalFontsCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 1")
    fun getSystemFontsCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 0 AND is_trashed = 0")
    fun getLocalFontsCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM fonts WHERE is_variable_font = 1")
    fun getVariableFontsCount(): Int

    @Query("UPDATE fonts SET real_name = :realName, updated_at = :timestamp WHERE path = :path")
    // NOTE(kotlin-migration): realName/label are nullable since Java callers may pass null (SQL NULL).
    fun updateRealName(path: String, realName: String?, timestamp: Long): Int

    @Query("SELECT * FROM fonts WHERE real_name IS NULL OR real_name = '' LIMIT :limit")
    fun getFontsWithoutRealName(limit: Int): List<FontEntity>


    @Query("UPDATE fonts SET path = :newPath, file_name = :newFileName, updated_at = :updatedAt WHERE path = :oldPath")
    fun updatePath(oldPath: String, newPath: String, newFileName: String, updatedAt: Long)


    @Query("UPDATE fonts SET weight_width_label = :label, updated_at = :timestamp WHERE path = :path")
    // NOTE(kotlin-migration): realName/label are nullable since Java callers may pass null (SQL NULL).
    fun updateWeightWidthLabel(path: String, label: String?, timestamp: Long): Int

    @Query("SELECT * FROM fonts WHERE weight_width_label IS NULL LIMIT :limit")
    fun getFontsWithoutWeightWidth(limit: Int): List<FontEntity>

    // ترحيل: أي نص مخزّن بالصيغة القديمة (اسم جاهز مثل "Bold • Condensed") يصبح NULL
    // ليُعاد استخراجه بالصيغة الجديدة "وزن|عرض|متغير"
    @Query("UPDATE fonts SET weight_width_label = NULL WHERE weight_width_label IS NOT NULL AND weight_width_label NOT LIKE '%|%|%'")
    fun resetLegacyWeightWidthLabels(): Int

    @Query("UPDATE fonts SET is_favorite = :isFavorite, updated_at = :timestamp WHERE path = :path")
    fun updateFavoriteStatus(path: String, isFavorite: Boolean, timestamp: Long): Int

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name ASC")
    fun getFavoriteFonts(): LiveData<List<FontEntity>>

    @Query("SELECT COUNT(*) FROM fonts WHERE is_favorite = 1 AND is_trashed = 0")
    fun getFavoriteFontsCount(): LiveData<Int>

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    fun searchFavoriteFonts(query: String): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name ASC")
    fun getFavoriteFontsSortedByName(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name DESC")
    fun getFavoriteFontsSortedByNameDesc(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY last_modified ASC")
    fun getFavoriteFontsSortedByDate(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY last_modified DESC")
    fun getFavoriteFontsSortedByDateDesc(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY size ASC")
    fun getFavoriteFontsSortedBySize(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY size DESC")
    fun getFavoriteFontsSortedBySizeDesc(): LiveData<List<FontEntity>>


    @Query("SELECT * FROM fonts WHERE is_trashed = 1 ORDER BY deleted_at DESC")
    fun getTrashFonts(): LiveData<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE is_trashed = 1 ORDER BY deleted_at DESC")
    fun getTrashFontsSync(): List<FontEntity>

    @Query("SELECT COUNT(*) FROM fonts WHERE is_trashed = 1")
    fun getTrashFontsCount(): LiveData<Int>

    @Query("UPDATE fonts SET is_trashed = 1, deleted_at = :deletedAt, " +
           "original_path = :originalPath, path = :path, updated_at = :timestamp " +
           "WHERE path = :originalPath")
    fun moveToTrash(originalPath: String, path: String, deletedAt: Long, timestamp: Long): Int

    @Query("UPDATE fonts SET is_trashed = 0, deleted_at = 0, original_path = NULL, " +
           "path = :restoredPath, file_name = :fileName, updated_at = :timestamp " +
           "WHERE path = :trashedPath")
    fun restoreFromTrash(trashedPath: String, restoredPath: String, fileName: String, timestamp: Long): Int

    @Query("DELETE FROM fonts WHERE path = :path AND is_trashed = 1")
    fun deleteFromTrash(path: String): Int

    @Query("SELECT * FROM fonts WHERE is_trashed = 1 AND deleted_at <= :expiryTimestamp")
    fun getExpiredTrashFonts(expiryTimestamp: Long): List<FontEntity>
}
