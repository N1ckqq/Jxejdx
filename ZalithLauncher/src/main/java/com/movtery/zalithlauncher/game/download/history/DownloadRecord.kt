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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 下载历史记录
 */
@Entity(tableName = "downloadHistory")
data class DownloadRecord(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 文件名称 */
    val fileName: String,
    /** 所属平台 (Modrinth / CurseForge / ...) */
    val platform: String,
    /** 项目 ID */
    val projectId: String,
    /** 文件大小（字节） */
    val fileSize: Long,
    /** 安装到哪些游戏版本（逗号分隔） */
    val installedVersions: String,
    /** 下载时间戳 (Unix ms) */
    val downloadedAt: Long = System.currentTimeMillis()
)
