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

package com.movtery.zalithlauncher.game.launch

import android.app.ActivityManager
import android.content.Context
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import java.io.File

/**
 * 智能内存推荐系统
 * 基于已安装的模组数量、MC版本、设备总内存来推荐最优RAM分配
 */
object SmartRamRecommendation {

    data class Recommendation(
        /** 推荐的RAM值（MB） */
        val recommendedMB: Int,
        /** 最低可接受RAM值（MB） */
        val minimumMB: Int,
        /** 推荐原因 */
        val reason: RecommendationReason,
        /** 模组数量（如果有） */
        val modCount: Int,
        /** 设备总RAM（MB） */
        val deviceTotalMB: Int
    )

    enum class RecommendationReason {
        /** 原版，无模组 */
        VANILLA,
        /** 少量模组（1-20） */
        LIGHT_MODDED,
        /** 中等模组（21-80） */
        MEDIUM_MODDED,
        /** 大量模组（81+） */
        HEAVY_MODDED,
        /** 设备内存不足 */
        LOW_DEVICE_RAM
    }

    /**
     * 获取设备总内存（MB）
     */
    fun getDeviceTotalRam(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem / (1024 * 1024)).toInt()
    }

    /**
     * 获取版本的模组文件数量
     */
    fun getModCount(version: Version): Int {
        val modsFolder = File(version.getGameDir(), VersionFolders.MOD.folderName)
        if (!modsFolder.exists() || !modsFolder.isDirectory) return 0
        return modsFolder.listFiles()?.count { file ->
            file.isFile && (file.extension == "jar" || file.extension == "disabled")
        } ?: 0
    }

    /**
     * 基于版本信息和设备状态计算推荐RAM
     */
    fun getRecommendation(context: Context, version: Version): Recommendation {
        val deviceTotalMB = getDeviceTotalRam(context)
        val modCount = getModCount(version)
        val mcVersion = version.getVersionInfo()?.minecraftVersion

        // 基础内存需求（根据MC版本）
        val baseMB = when {
            mcVersion == null -> 1024
            mcVersion >= "1.20" -> 1024  // 现代版本需要更多
            mcVersion >= "1.16" -> 768
            mcVersion >= "1.12" -> 512
            else -> 384
        }

        // 模组内存增量
        val modMemoryMB = when {
            modCount == 0 -> 0
            modCount <= 20 -> modCount * 8          // ~160MB for 20 mods
            modCount <= 50 -> 160 + (modCount - 20) * 12   // +360MB for next 30
            modCount <= 80 -> 520 + (modCount - 50) * 16   // +480MB for next 30
            else -> 1000 + (modCount - 80) * 20    // heavy modpacks
        }

        val rawRecommendedMB = baseMB + modMemoryMB

        // Ограничиваем не более 70% от доступной RAM устройства
        val maxAllowedMB = (deviceTotalMB * 0.7).toInt()
        val recommendedMB = rawRecommendedMB.coerceIn(512, maxAllowedMB)

        // Минимум — 60% от рекомендуемого
        val minimumMB = (recommendedMB * 0.6).toInt().coerceAtLeast(256)

        val reason = when {
            deviceTotalMB < 3072 -> RecommendationReason.LOW_DEVICE_RAM
            modCount == 0 -> RecommendationReason.VANILLA
            modCount <= 20 -> RecommendationReason.LIGHT_MODDED
            modCount <= 80 -> RecommendationReason.MEDIUM_MODDED
            else -> RecommendationReason.HEAVY_MODDED
        }

        return Recommendation(
            recommendedMB = roundTo64(recommendedMB),
            minimumMB = roundTo64(minimumMB),
            reason = reason,
            modCount = modCount,
            deviceTotalMB = deviceTotalMB
        )
    }

    /**
     * 获取推荐原因的描述文本 key
     */
    fun getReasonDescription(reason: RecommendationReason, modCount: Int): String {
        return when (reason) {
            RecommendationReason.VANILLA -> "Vanilla (no mods detected)"
            RecommendationReason.LIGHT_MODDED -> "$modCount mods (light)"
            RecommendationReason.MEDIUM_MODDED -> "$modCount mods (medium load)"
            RecommendationReason.HEAVY_MODDED -> "$modCount mods (heavy modpack)"
            RecommendationReason.LOW_DEVICE_RAM -> "Limited device RAM"
        }
    }

    /**
     * 将值四舍五入到最近的64MB
     */
    private fun roundTo64(mb: Int): Int {
        return ((mb + 32) / 64) * 64
    }
}
