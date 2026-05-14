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

package com.movtery.zalithlauncher.crashlogs

/**
 * 崩溃报告自动解析器
 * 分析崩溃日志，检测导致崩溃的模组、给出建议的修复方案
 */
object CrashReportParser {

    data class CrashAnalysis(
        /** 崩溃的主要异常 */
        val mainException: String?,
        /** 崩溃描述 */
        val description: String?,
        /** 疑似导致崩溃的模组列表 */
        val suspectedMods: List<SuspectedMod>,
        /** 建议的修复操作 */
        val suggestions: List<Suggestion>,
        /** 崩溃分类 */
        val category: CrashCategory
    )

    data class SuspectedMod(
        /** 模组ID或名称 */
        val modId: String,
        /** 置信度 0.0-1.0 */
        val confidence: Float,
        /** 在stacktrace中出现的位置 */
        val stackTraceHint: String?
    )

    data class Suggestion(
        val action: SuggestionAction,
        val details: String
    )

    enum class SuggestionAction {
        REMOVE_MOD,
        UPDATE_MOD,
        INCREASE_RAM,
        UPDATE_JAVA,
        CHECK_MOD_COMPATIBILITY,
        REINSTALL_GAME,
        REPORT_BUG
    }

    enum class CrashCategory {
        MOD_CONFLICT,
        OUT_OF_MEMORY,
        MISSING_DEPENDENCY,
        RENDERING_ERROR,
        MIXIN_ERROR,
        CLASS_NOT_FOUND,
        NATIVE_CRASH,
        UNKNOWN
    }

    // Шаблоны для определения модов из package names
    private val MOD_PACKAGE_PATTERNS = listOf(
        Regex("""at\s+(\w+\.\w+\.\w+)\..*"""), // top-level package extraction
        Regex("""Caused by:.*?(\w+Exception).*?at\s+([\w.]+)""", RegexOption.DOT_MATCHES_ALL),
    )

    // Известные пакеты Minecraft/системы (не моды)
    private val SYSTEM_PACKAGES = setOf(
        "net.minecraft", "com.mojang", "java.", "javax.", "sun.",
        "org.lwjgl", "com.google", "org.apache", "io.netty",
        "it.unimi", "com.sun", "jdk.", "org.objectweb.asm",
        "net.fabricmc.loader", "net.minecraftforge.fml",
        "cpw.mods", "org.spongepowered.asm"
    )

    /**
     * 解析崩溃日志
     * @param logContent 完整的崩溃日志内容
     * @return 崩溃分析结果
     */
    fun analyze(logContent: String): CrashAnalysis {
        val category = detectCategory(logContent)
        val mainException = extractMainException(logContent)
        val description = extractDescription(logContent)
        val suspectedMods = findSuspectedMods(logContent)
        val suggestions = generateSuggestions(category, suspectedMods, logContent)

        return CrashAnalysis(
            mainException = mainException,
            description = description,
            suspectedMods = suspectedMods,
            suggestions = suggestions,
            category = category
        )
    }

    private fun detectCategory(log: String): CrashCategory {
        return when {
            log.contains("OutOfMemoryError") || log.contains("out of memory", ignoreCase = true) ->
                CrashCategory.OUT_OF_MEMORY

            log.contains("mixin", ignoreCase = true) && (
                log.contains("MixinApplyError") ||
                log.contains("MixinTransformerError") ||
                log.contains("org.spongepowered.asm.mixin")
            ) -> CrashCategory.MIXIN_ERROR

            log.contains("NoClassDefFoundError") || log.contains("ClassNotFoundException") ->
                CrashCategory.CLASS_NOT_FOUND

            log.contains("Missing or unsatisfied dependency") ||
            log.contains("requires") && log.contains("which is missing") ->
                CrashCategory.MISSING_DEPENDENCY

            log.contains("GLException") || log.contains("OpenGL") ||
            log.contains("Failed to create GL context") || log.contains("RenderSystem") ->
                CrashCategory.RENDERING_ERROR

            log.contains("signal") && (log.contains("SIGSEGV") || log.contains("SIGABRT")) ->
                CrashCategory.NATIVE_CRASH

            findSuspectedMods(log).isNotEmpty() ->
                CrashCategory.MOD_CONFLICT

            else -> CrashCategory.UNKNOWN
        }
    }

    private fun extractMainException(log: String): String? {
        // Ищем основную ошибку
        val crashReportMatch = Regex("""Description:\s*(.+)""").find(log)
        if (crashReportMatch != null) return crashReportMatch.groupValues[1].trim()

        // Ищем первый Exception/Error
        val exceptionMatch = Regex("""([\w.]+(?:Exception|Error)):\s*(.+)""").find(log)
        return exceptionMatch?.let { "${it.groupValues[1]}: ${it.groupValues[2].take(100)}" }
    }

    private fun extractDescription(log: String): String? {
        val descMatch = Regex("""---- Minecraft Crash Report ----.*?Description:\s*(.+?)[\r\n]""", RegexOption.DOT_MATCHES_ALL).find(log)
        return descMatch?.groupValues?.get(1)?.trim()
    }

    private fun findSuspectedMods(log: String): List<SuspectedMod> {
        val modCounts = mutableMapOf<String, Int>()
        val modHints = mutableMapOf<String, String>()

        // 1. Ищем в секции "Suspected Mods" (если есть — Forge/Fabric сами определяют)
        val suspectedSection = Regex("""Suspected Mods?:\s*(.+?)(?:\n\n|\n\t\t|$)""", RegexOption.DOT_MATCHES_ALL).find(log)
        if (suspectedSection != null) {
            val modsText = suspectedSection.groupValues[1]
            val modMatches = Regex("""(\w[\w\s-]*?)\s*\(([^)]+)\)""").findAll(modsText)
            for (match in modMatches) {
                val modName = match.groupValues[1].trim()
                if (modName.isNotBlank() && modName != "Unknown" && modName != "Minecraft") {
                    modCounts[modName] = (modCounts[modName] ?: 0) + 5 // высокий приоритет
                    modHints[modName] = "Listed in Suspected Mods"
                }
            }
        }

        // 2. Анализируем stacktrace для определения модовых пакетов
        val stackLines = log.lines().filter { it.trimStart().startsWith("at ") }
        for (line in stackLines) {
            val packageMatch = Regex("""at\s+([\w.]+)\.""").find(line) ?: continue
            val fullPackage = packageMatch.groupValues[1]

            // Пропускаем системные пакеты
            if (SYSTEM_PACKAGES.any { fullPackage.startsWith(it) }) continue

            // Извлекаем имя мода из пакета (обычно 2-3 уровень)
            val parts = fullPackage.split(".")
            val modId = when {
                parts.size >= 3 -> parts.take(3).joinToString(".")
                parts.size >= 2 -> parts.take(2).joinToString(".")
                else -> continue
            }

            modCounts[modId] = (modCounts[modId] ?: 0) + 1
            if (!modHints.containsKey(modId)) {
                modHints[modId] = line.trim().take(120)
            }
        }

        // 3. Ищем упоминания модов в "Is Modded" или "Mod List" секциях
        val modListMatch = Regex("""Mod List:\s*\n((?:\s+.+\n)+)""").find(log)
        // Не добавляем их в suspected — это просто список установленных модов

        // Рейтинг и сортировка
        val maxCount = modCounts.values.maxOrNull() ?: 1
        return modCounts
            .filter { it.value >= 2 } // минимум 2 упоминания
            .map { (modId, count) ->
                SuspectedMod(
                    modId = modId,
                    confidence = (count.toFloat() / maxCount).coerceIn(0.1f, 1.0f),
                    stackTraceHint = modHints[modId]
                )
            }
            .sortedByDescending { it.confidence }
            .take(5) // максимум 5 подозрительных модов
    }

    private fun generateSuggestions(
        category: CrashCategory,
        suspectedMods: List<SuspectedMod>,
        log: String
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        when (category) {
            CrashCategory.OUT_OF_MEMORY -> {
                suggestions.add(Suggestion(
                    SuggestionAction.INCREASE_RAM,
                    "The game ran out of memory. Try increasing RAM allocation in settings."
                ))
            }

            CrashCategory.MISSING_DEPENDENCY -> {
                // Пытаемся извлечь название отсутствующей зависимости
                val depMatch = Regex("""requires\s+([\w-]+)""").find(log)
                val depName = depMatch?.groupValues?.get(1) ?: "unknown dependency"
                suggestions.add(Suggestion(
                    SuggestionAction.CHECK_MOD_COMPATIBILITY,
                    "Missing dependency: $depName. Install the required mod or update existing mods."
                ))
            }

            CrashCategory.MIXIN_ERROR -> {
                suggestions.add(Suggestion(
                    SuggestionAction.CHECK_MOD_COMPATIBILITY,
                    "Mixin conflict detected. Two or more mods are trying to modify the same game code."
                ))
                if (suspectedMods.isNotEmpty()) {
                    suggestions.add(Suggestion(
                        SuggestionAction.REMOVE_MOD,
                        "Try removing: ${suspectedMods.first().modId}"
                    ))
                }
            }

            CrashCategory.CLASS_NOT_FOUND -> {
                suggestions.add(Suggestion(
                    SuggestionAction.UPDATE_MOD,
                    "A class is missing, likely due to incompatible mod versions. Update your mods."
                ))
            }

            CrashCategory.RENDERING_ERROR -> {
                suggestions.add(Suggestion(
                    SuggestionAction.CHECK_MOD_COMPATIBILITY,
                    "A rendering error occurred. Try switching the renderer in launcher settings."
                ))
            }

            CrashCategory.NATIVE_CRASH -> {
                suggestions.add(Suggestion(
                    SuggestionAction.REINSTALL_GAME,
                    "Native crash detected. This may be a renderer or system issue. Try a different renderer."
                ))
            }

            CrashCategory.MOD_CONFLICT -> {
                if (suspectedMods.isNotEmpty()) {
                    val topMod = suspectedMods.first()
                    suggestions.add(Suggestion(
                        SuggestionAction.REMOVE_MOD,
                        "Suspected mod: ${topMod.modId} (confidence: ${(topMod.confidence * 100).toInt()}%)"
                    ))
                    suggestions.add(Suggestion(
                        SuggestionAction.UPDATE_MOD,
                        "Try updating the suspected mod to its latest version."
                    ))
                }
            }

            CrashCategory.UNKNOWN -> {
                suggestions.add(Suggestion(
                    SuggestionAction.REPORT_BUG,
                    "Unable to determine the exact cause. Share the crash log for further analysis."
                ))
            }
        }

        return suggestions
    }

    /**
     * 获取崩溃类别的本地化名称
     */
    fun getCategoryDisplayName(category: CrashCategory): String {
        return when (category) {
            CrashCategory.MOD_CONFLICT -> "Mod Conflict"
            CrashCategory.OUT_OF_MEMORY -> "Out of Memory"
            CrashCategory.MISSING_DEPENDENCY -> "Missing Dependency"
            CrashCategory.RENDERING_ERROR -> "Rendering Error"
            CrashCategory.MIXIN_ERROR -> "Mixin Conflict"
            CrashCategory.CLASS_NOT_FOUND -> "Missing Class"
            CrashCategory.NATIVE_CRASH -> "Native Crash"
            CrashCategory.UNKNOWN -> "Unknown"
        }
    }

    /**
     * 获取操作的图标描述
     */
    fun getActionDisplayName(action: SuggestionAction): String {
        return when (action) {
            SuggestionAction.REMOVE_MOD -> "Remove Mod"
            SuggestionAction.UPDATE_MOD -> "Update Mod"
            SuggestionAction.INCREASE_RAM -> "Increase RAM"
            SuggestionAction.UPDATE_JAVA -> "Update Java"
            SuggestionAction.CHECK_MOD_COMPATIBILITY -> "Check Compatibility"
            SuggestionAction.REINSTALL_GAME -> "Reinstall Game"
            SuggestionAction.REPORT_BUG -> "Report Bug"
        }
    }
}
