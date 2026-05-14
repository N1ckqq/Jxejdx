/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.download.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM downloadHistory ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<DownloadRecord>

    @Query("SELECT * FROM downloadHistory ORDER BY downloadedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<DownloadRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)

    @Delete
    suspend fun delete(record: DownloadRecord)

    @Query("DELETE FROM downloadHistory")
    suspend fun deleteAll()
}
