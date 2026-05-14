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

package com.movtery.zalithlauncher.ui.screens.content.settings

/**
 * 交互式变更日志数据
 * 记录每个版本的改动历史
 */
object ChangelogData {

    data class ChangelogEntry(
        val version: String,
        val date: String,
        val changes: List<ChangeItem>
    )

    data class ChangeItem(
        val type: ChangeType,
        val description: String
    )

    enum class ChangeType {
        FEATURE,    // 新功能
        FIX,        // 修复
        IMPROVEMENT,// 优化/改进
        REMOVED     // 移除
    }

    /**
     * 变更日志（从新到旧排序）
     * 在每次发版时在这里添加新条目
     */
    val changelog: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "2.4.3-fork.4",
            date = "2025-05-14",
            changes = listOf(
                ChangeItem(ChangeType.FEATURE, "Dependency checkboxes in download dialog — select which dependencies to auto-download"),
                ChangeItem(ChangeType.FEATURE, "Auto-resolve compatible dependency versions based on MC version and mod loader"),
                ChangeItem(ChangeType.FEATURE, "Game time tracker — track play time per version with daily/weekly stats"),
                ChangeItem(ChangeType.FEATURE, "Smart RAM recommendation — suggests optimal memory based on mod count"),
                ChangeItem(ChangeType.FEATURE, "Crash report auto-parser — detects guilty mods and suggests fixes"),
                ChangeItem(ChangeType.FEATURE, "Renderer benchmark — test GPU performance for renderer selection"),
                ChangeItem(ChangeType.FEATURE, "Interactive changelog screen"),
                ChangeItem(ChangeType.FEATURE, "Background mod update notifications")
            )
        ),
        ChangelogEntry(
            version = "2.4.3-fork.3",
            date = "2025-05-12",
            changes = listOf(
                ChangeItem(ChangeType.FEATURE, "Batch mod download — select multiple mods with checkboxes"),
                ChangeItem(ChangeType.FEATURE, "Full offline login support (local accounts without Microsoft)"),
                ChangeItem(ChangeType.FEATURE, "Complete login menu with Microsoft, offline, and third-party auth servers"),
                ChangeItem(ChangeType.IMPROVEMENT, "Build APK workflow via GitHub Actions")
            )
        ),
        ChangelogEntry(
            version = "2.4.3",
            date = "2025-05-01",
            changes = listOf(
                ChangeItem(ChangeType.FEATURE, "Based on ZalithLauncher 2 (upstream)"),
                ChangeItem(ChangeType.FEATURE, "Modrinth and CurseForge mod search"),
                ChangeItem(ChangeType.FEATURE, "Mod loader installation (Forge, Fabric, Quilt, NeoForge, etc.)"),
                ChangeItem(ChangeType.FEATURE, "Version isolation and management"),
                ChangeItem(ChangeType.FEATURE, "Multiple renderers (GL4ES, Zink, VirGL, Freedreno, Panfrost)"),
                ChangeItem(ChangeType.FEATURE, "Custom control layouts with visual editor"),
                ChangeItem(ChangeType.FEATURE, "Gamepad and gyroscope support"),
                ChangeItem(ChangeType.FEATURE, "P2P multiplayer via Terracotta"),
                ChangeItem(ChangeType.FEATURE, "Modpack export (Modrinth, CurseForge, MultiMC, MCBBS)")
            )
        )
    )

    /**
     * 获取变更类型的显示名称
     */
    fun getTypeLabel(type: ChangeType): String {
        return when (type) {
            ChangeType.FEATURE -> "NEW"
            ChangeType.FIX -> "FIX"
            ChangeType.IMPROVEMENT -> "IMPROVED"
            ChangeType.REMOVED -> "REMOVED"
        }
    }

    /**
     * 获取变更类型的颜色标识（十六进制）
     */
    fun getTypeColorHex(type: ChangeType): Long {
        return when (type) {
            ChangeType.FEATURE -> 0xFF4CAF50      // зелёный
            ChangeType.FIX -> 0xFFFF9800          // оранжевый
            ChangeType.IMPROVEMENT -> 0xFF2196F3  // синий
            ChangeType.REMOVED -> 0xFFF44336      // красный
        }
    }
}
