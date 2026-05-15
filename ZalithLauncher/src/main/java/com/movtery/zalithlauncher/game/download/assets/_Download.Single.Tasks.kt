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

package com.movtery.zalithlauncher.game.download.assets

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.database.AppDatabase
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformVersion
import com.movtery.zalithlauncher.game.download.assets.platform.getVersions
import com.movtery.zalithlauncher.game.download.assets.platform.mcim.mapMCIMMirrorUrls
import com.movtery.zalithlauncher.game.download.history.DownloadRecord
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.screens.content.download.assets.elements.initAll
import com.movtery.zalithlauncher.utils.file.ensureParentDirectory
import com.movtery.zalithlauncher.utils.file.formatFileSize
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import com.movtery.zalithlauncher.utils.logging.Logger.lWarning
import com.movtery.zalithlauncher.utils.network.downloadFromMirrorListSuspend
import com.movtery.zalithlauncher.utils.network.withSpeedReport
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.IOException
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/**
 * 为一些版本下载单独的资源文件
 * @param version 要下载单独资源版本信息
 * @param versions 为哪些游戏版本下载
 * @param folder 版本游戏目录下的相对路径
 * @param onFileCopied 文件已成功复制到版本游戏目录后 单独回调
 * @param onFileCancelled 文件安装已取消 单独回调
 */
fun downloadSingleForVersions(
    context: Context,
    version: PlatformVersion,
    versions: List<Version>,
    folder: String,
    onFileCopied: suspend (zip: File, folder: File) -> Unit = { _, _ -> },
    onFileCancelled: (zip: File, folder: File) -> Unit = { _, _ -> },
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    val cacheFile = File(File(PathManager.DIR_CACHE, "assets"), version.platformSha1() ?: version.platformFileName())

    downloadSingleFile(
        version = version,
        file = cacheFile,
        onDownloaded = { task ->
            task.updateProgress(-1f, R.string.download_assets_install_progress_installing, version.platformFileName())
            versions.forEach { ver ->
                val targetFolder = File(ver.getGameDir(), folder)
                val targetFile = File(targetFolder, version.platformFileName())
                if (targetFile.exists() && !targetFile.delete()) throw IOException("Failed to properly delete the existing target file.")
                cacheFile.copyTo(targetFile)
                onFileCopied(targetFile, targetFolder) //文件已复制回调
            }
            // 记录下载历史
            runCatching {
                val db = AppDatabase.getInstance(context)
                val record = DownloadRecord(
                    fileName = version.platformFileName(),
                    platform = version.platform().name,
                    projectId = "",
                    fileSize = version.platformFileSize(),
                    installedVersions = versions.joinToString(",") { it.getVersionName() }
                )
                withContext(Dispatchers.IO) {
                    db.downloadRecordDao().insert(record)
                }
            }
        },
        onError = { e ->
            lWarning("An error occurred while downloading the resource files.", e)
            val message = mapExceptionToMessage(e).let { pair ->
                val args = pair.second
                if (args != null) {
                    context.getString(pair.first, *args)
                } else {
                    context.getString(pair.first)
                }
            }
            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = context.getString(R.string.download_assets_install_failed),
                    message = message
                )
            )
        },
        onCancel = {
            FileUtils.deleteQuietly(cacheFile)
            versions.forEach { ver ->
                val targetFolder = File(ver.getGameDir(), folder)
                val targetFile = File(targetFolder, version.platformFileName())
                if (targetFile.exists()) FileUtils.deleteQuietly(targetFile)
                onFileCancelled(targetFile, targetFolder) //文件已取消回调
            }
        },
        onFinally = {
            lInfo("Attempting to clear cached resource files.")
            FileUtils.deleteQuietly(cacheFile)
        }
    )
}

private fun downloadSingleFile(
    version: PlatformVersion,
    file: File,
    onDownloaded: suspend (Task) -> Unit,
    onError: (Throwable) -> Unit = {},
    onCancel: () -> Unit = {},
    onFinally: () -> Unit = {}
) {
    TaskSystem.submitTask(
        Task.runTask(
            id = version.platformSha1() ?: version.platformFileName(),
            task = { task ->
                val totalFileSize = version.platformFileSize()
                var downloadedSize = 0L

                //更新下载任务进度
                fun updateProgress() {
                    task.updateProgress(
                        (downloadedSize.toDouble() / totalFileSize.toDouble()).toFloat(),
                        R.string.download_assets_install_progress_downloading,
                        version.platformFileName(),
                        formatFileSize(downloadedSize),
                        formatFileSize(totalFileSize),
                    )
                }
                updateProgress()

                withSpeedReport(
                    onSpeedReport = { bytes ->
                        task.updateSpeed(bytes)
                    },
                    onClear = {
                        task.clearSpeed()
                    }
                ) { report ->
                    downloadFromMirrorListSuspend(
                        urls = version
                            .platformDownloadUrl()
                            .mapMCIMMirrorUrls(),
                        sha1 = version.platformSha1(),
                        outputFile = file.ensureParentDirectory(),
                        sizeCallback = { size ->
                            downloadedSize += size
                            updateProgress()
                            report(size)
                        }
                    )
                }

                onDownloaded(task)
            },
            onError = onError,
            onCancel = onCancel,
            onFinally = onFinally
        )
    )
}

fun mapExceptionToMessage(e: Throwable): Pair<Int, Array<Any>?> {
    return when (e) {
        is HttpRequestTimeoutException -> Pair(R.string.error_timeout, null)
        is UnknownHostException, is UnresolvedAddressException -> Pair(R.string.error_network_unreachable, null)
        is ConnectException -> Pair(R.string.error_connection_failed, null)
        is ResponseException -> {
            when (e.response.status) {
                HttpStatusCode.Unauthorized -> Pair(R.string.error_unauthorized, null)
                HttpStatusCode.NotFound -> Pair(R.string.error_notfound, null)
                else -> Pair(R.string.error_client_error, arrayOf(e.response.status))
            }
        }
        else -> {
            val errorMessage = e.localizedMessage ?: e::class.simpleName ?: "Unknown error"
            Pair(R.string.error_unknown, arrayOf(errorMessage))
        }
    }
}



/**
 * 为选中的依赖项目查找兼容版本并下载
 * Использует MC-версию и загрузчик из уже выбранного файла главного мода,
 * чтобы гарантировать скачивание зависимостей строго под ту же версию игры.
 *
 * @param mainVersion главный мод (источник MC-версии и загрузчика)
 * @param dependencies список зависимостей для скачивания
 * @param targetVersions список установленных версий игры, куда установить файлы
 * @param folder путь относительно игровой папки версии
 */
fun downloadDependenciesForVersions(
    context: Context,
    mainVersion: PlatformVersion,
    dependencies: List<PlatformVersion.PlatformDependency>,
    targetVersions: List<Version>,
    folder: String,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    // Берём MC-версии из уже выбранного файла главного мода (не из всех поддерживаемых версий проекта)
    // platformGameVersion() возвращает версии конкретного выбранного файла — это и есть целевая версия
    val mcVersions = mainVersion.platformGameVersion()
    val loaders = mainVersion.platformLoaders().map { it.getDisplayName().lowercase() }

    if (mcVersions.isEmpty()) {
        lWarning("Cannot determine Minecraft version from the main mod version, skipping dependency download.")
        return
    }

    TaskSystem.submitTask(
        Task.runTask(
            id = "deps_${mainVersion.platformSha1() ?: mainVersion.platformFileName()}",
            task = { task ->
                task.updateProgress(-1f, R.string.download_assets_deps_resolving)

                for (dependency in dependencies) {
                    try {
                        val allVersions = getVersions(
                            projectID = dependency.projectId,
                            platform = dependency.platform
                        )
                        val initializedVersions = allVersions.initAll(dependency.projectId)

                        val compatibleVersion = findCompatibleVersion(
                            versions = initializedVersions,
                            mcVersions = mcVersions,
                            loaders = loaders
                        )

                        if (compatibleVersion != null) {
                            lInfo("Found compatible dependency version: ${compatibleVersion.platformFileName()} for project ${dependency.projectId}")
                            downloadSingleForVersions(
                                context = context,
                                version = compatibleVersion,
                                versions = targetVersions,
                                folder = folder,
                                submitError = submitError
                            )
                        } else {
                            lWarning("No compatible version found for dependency project: ${dependency.projectId}")
                            submitError(
                                ErrorViewModel.ThrowableMessage(
                                    title = context.getString(R.string.download_assets_deps_not_found_title),
                                    message = context.getString(R.string.download_assets_deps_not_found_message, dependency.projectId)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        lWarning("Failed to resolve dependency: ${dependency.projectId}", e)
                        val message = mapExceptionToMessage(e).let { pair ->
                            val args = pair.second
                            if (args != null) context.getString(pair.first, *args)
                            else context.getString(pair.first)
                        }
                        submitError(
                            ErrorViewModel.ThrowableMessage(
                                title = context.getString(R.string.download_assets_deps_failed_title),
                                message = message
                            )
                        )
                    }
                }
            },
            onError = { e ->
                lWarning("An error occurred while resolving dependencies.", e)
            }
        )
    )
}

/**
 * 从版本列表中查找与目标MC版本和加载器兼容的最佳版本
 * 优先匹配同时满足MC版本（精确匹配）和加载器的版本，
 * 若找不到则只匹配MC版本，在匹配候选中选择发布日期最新的版本
 *
 * @param mcVersions 目标MC版本列表（精确匹配，如 ["1.21.1"]）
 * @param loaders 目标加载器列表（如 ["fabric"]）
 */
fun findCompatibleVersion(
    versions: List<PlatformVersion>,
    mcVersions: Array<String>,
    loaders: List<String>
): PlatformVersion? {
    if (mcVersions.isEmpty()) return null

    // 按发布时间降序排列（最新在前）
    val sortedVersions = versions.sortedByDescending { it.platformDatePublished() }

    // 优先：同时匹配MC版本（精确）和加载器
    if (loaders.isNotEmpty()) {
        val exactMatch = sortedVersions.firstOrNull { version ->
            val versionMcVersions = version.platformGameVersion()
            val versionLoaders = version.platformLoaders().map { it.getDisplayName().lowercase() }
            // Точное совпадение: версия должна поддерживать ТОЛЬКО те MC-версии, что в mcVersions
            // Используем contains — ищем версии у которых есть хотя бы одна из целевых MC-версий,
            // но при этом проверяем что среди mcVersions есть точное совпадение (без "any")
            val mcMatch = mcVersions.any { mc -> versionMcVersions.contains(mc) }
            val loaderMatch = loaders.any { loader -> versionLoaders.contains(loader) }
            mcMatch && loaderMatch
        }
        if (exactMatch != null) return exactMatch
    }

    // Второй приоритет: только совпадение по MC-версии (без учёта загрузчика)
    return sortedVersions.firstOrNull { version ->
        val versionMcVersions = version.platformGameVersion()
        mcVersions.any { mc -> versionMcVersions.contains(mc) }
    }
}



/**
 * Пакетная загрузка выбранных модов в указанные версии игры.
 * Для каждого мода автоматически находит совместимую версию файла,
 * ориентируясь на MC-версию и загрузчик из списка targetVersions.
 *
 * @param mods список выбранных модов (platform + projectId)
 * @param targetVersions список версий игры, куда нужно установить моды
 * @param folder путь относительно игровой папки версии
 */
fun downloadBatchMods(
    context: Context,
    mods: List<com.movtery.zalithlauncher.ui.screens.content.download.assets.elements.SelectedMod>,
    targetVersions: List<Version>,
    folder: String,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    if (mods.isEmpty() || targetVersions.isEmpty()) return

    // Собираем уникальные пары (mcVersion, loader) из выбранных игровых версий
    // Это гарантирует скачивание файла строго под нужную MC-версию и загрузчик
    val targetMcVersions: Array<String> = targetVersions
        .mapNotNull { it.getVersionInfo()?.minecraftVersion }
        .distinct()
        .toTypedArray()

    val targetLoaders: List<String> = targetVersions
        .mapNotNull { it.getVersionInfo()?.loaderInfo?.loader?.displayName?.lowercase() }
        .distinct()

    if (targetMcVersions.isEmpty()) {
        lWarning("Batch download: cannot determine MC versions from target game versions, aborting.")
        return
    }

    lInfo("Batch download: targeting MC versions=${targetMcVersions.toList()}, loaders=$targetLoaders")

    TaskSystem.submitTask(
        Task.runTask(
            id = "batch_mods_${System.currentTimeMillis()}",
            task = { task ->
                task.updateProgress(-1f, R.string.download_assets_deps_resolving)

                for (mod in mods) {
                    try {
                        val allVersions = getVersions(
                            projectID = mod.projectId,
                            platform = mod.platform
                        )
                        val initializedVersions = allVersions.initAll(mod.projectId)

                        // Ищем совместимую версию по MC-версии и загрузчику из игровых версий
                        val compatible = findCompatibleVersion(
                            versions = initializedVersions,
                            mcVersions = targetMcVersions,
                            loaders = targetLoaders
                        )

                        if (compatible != null) {
                            lInfo("Batch download: found compatible version ${compatible.platformFileName()} for ${mod.projectId}")
                            downloadSingleForVersions(
                                context = context,
                                version = compatible,
                                versions = targetVersions,
                                folder = folder,
                                submitError = submitError
                            )
                        } else {
                            lWarning("Batch download: no compatible version found for ${mod.projectId} (MC=${targetMcVersions.toList()}, loaders=$targetLoaders)")
                            submitError(
                                ErrorViewModel.ThrowableMessage(
                                    title = context.getString(R.string.download_assets_deps_not_found_title),
                                    message = context.getString(R.string.download_assets_deps_not_found_message, mod.projectId)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        lWarning("Batch download: failed for ${mod.projectId}", e)
                        val message = mapExceptionToMessage(e).let { pair ->
                            val args = pair.second
                            if (args != null) context.getString(pair.first, *args)
                            else context.getString(pair.first)
                        }
                        submitError(
                            ErrorViewModel.ThrowableMessage(
                                title = context.getString(R.string.download_assets_deps_failed_title),
                                message = message
                            )
                        )
                    }
                }
            },
            onError = { e ->
                lWarning("An error occurred during batch mod download.", e)
            }
        )
    )
}

/**
 * Пакетная загрузка выбранных ресурсов (ресурс-паки, миры) в указанные версии игры.
 * В отличие от модов, для ресурс-паков и миров проверка загрузчика не нужна,
 * только проверка MC-версии (если доступна).
 *
 * @param assets список выбранных ресурсов (platform + projectId)
 * @param targetVersions список версий игры, куда нужно установить ресурсы
 * @param folder путь относительно игровой папки версии
 * @param onFileCopied колбэк после копирования (например, для распаковки мира)
 * @param onFileCancelled колбэк при отмене установки
 */
fun downloadBatchAssets(
    context: Context,
    assets: List<com.movtery.zalithlauncher.ui.screens.content.download.assets.elements.SelectedMod>,
    targetVersions: List<Version>,
    folder: String,
    onFileCopied: suspend (zip: java.io.File, folder: java.io.File) -> Unit = { _, _ -> },
    onFileCancelled: (zip: java.io.File, folder: java.io.File) -> Unit = { _, _ -> },
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    if (assets.isEmpty() || targetVersions.isEmpty()) return

    // Для ресурс-паков и миров загрузчик не важен, но MC-версию учитываем если задана
    val targetMcVersions: Array<String> = targetVersions
        .mapNotNull { it.getVersionInfo()?.minecraftVersion }
        .distinct()
        .toTypedArray()

    TaskSystem.submitTask(
        Task.runTask(
            id = "batch_assets_${System.currentTimeMillis()}",
            task = { task ->
                task.updateProgress(-1f, R.string.download_assets_deps_resolving)

                for (asset in assets) {
                    try {
                        val allVersions = getVersions(
                            projectID = asset.projectId,
                            platform = asset.platform
                        )
                        val initializedVersions = allVersions.initAll(asset.projectId)

                        // Для ресурсов без загрузчика ищем по MC-версии, иначе берём последнюю
                        val targetVersion = if (targetMcVersions.isNotEmpty()) {
                            findCompatibleVersion(
                                versions = initializedVersions,
                                mcVersions = targetMcVersions,
                                loaders = emptyList() // загрузчик не нужен для ресурс-паков и миров
                            ) ?: initializedVersions.maxByOrNull { it.platformDatePublished() }
                        } else {
                            initializedVersions.maxByOrNull { it.platformDatePublished() }
                        }

                        if (targetVersion != null) {
                            lInfo("Batch assets download: found version ${targetVersion.platformFileName()} for ${asset.projectId}")
                            downloadSingleForVersions(
                                context = context,
                                version = targetVersion,
                                versions = targetVersions,
                                folder = folder,
                                onFileCopied = onFileCopied,
                                onFileCancelled = onFileCancelled,
                                submitError = submitError
                            )
                        } else {
                            lWarning("Batch assets download: no versions found for ${asset.projectId}")
                            submitError(
                                ErrorViewModel.ThrowableMessage(
                                    title = context.getString(R.string.download_assets_deps_not_found_title),
                                    message = context.getString(R.string.download_assets_deps_not_found_message, asset.projectId)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        lWarning("Batch assets download: failed for ${asset.projectId}", e)
                        val message = mapExceptionToMessage(e).let { pair ->
                            val args = pair.second
                            if (args != null) context.getString(pair.first, *args)
                            else context.getString(pair.first)
                        }
                        submitError(
                            ErrorViewModel.ThrowableMessage(
                                title = context.getString(R.string.download_assets_deps_failed_title),
                                message = message
                            )
                        )
                    }
                }
            },
            onError = { e ->
                lWarning("An error occurred during batch assets download.", e)
            }
        )
    )
}
