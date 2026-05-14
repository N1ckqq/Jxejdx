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
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformVersion
import com.movtery.zalithlauncher.game.download.assets.platform.getVersions
import com.movtery.zalithlauncher.game.download.assets.platform.mcim.mapMCIMMirrorUrls
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
 * 通过主模组版本自动检测 Minecraft 版本和加载器，
 * 然后在各平台 API 中找到兼容的依赖版本进行下载
 *
 * @param mainVersion 主模组的版本信息（用于提取MC版本和加载器信息）
 * @param dependencies 用户选中的依赖列表
 * @param targetVersions 要安装到哪些游戏版本
 * @param folder 版本游戏目录下的相对路径
 */
fun downloadDependenciesForVersions(
    context: Context,
    mainVersion: PlatformVersion,
    dependencies: List<PlatformVersion.PlatformDependency>,
    targetVersions: List<Version>,
    folder: String,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    // 从主模组版本中获取MC版本和加载器信息
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
                        // 获取依赖项目的所有版本
                        val allVersions = getVersions(
                            projectID = dependency.projectId,
                            platform = dependency.platform
                        )

                        // 初始化所有版本
                        val initializedVersions = allVersions.initAll(dependency.projectId)

                        // 根据MC版本和加载器筛选兼容版本
                        val compatibleVersion = findCompatibleVersion(
                            versions = initializedVersions,
                            mcVersions = mcVersions,
                            loaders = loaders
                        )

                        if (compatibleVersion != null) {
                            lInfo("Found compatible dependency version: ${compatibleVersion.platformFileName()} for project ${dependency.projectId}")

                            // 下载兼容的依赖版本
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
                            if (args != null) {
                                context.getString(pair.first, *args)
                            } else {
                                context.getString(pair.first)
                            }
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
 * 优先匹配同时满足MC版本和加载器的版本，若找不到则只匹配MC版本
 */
private fun findCompatibleVersion(
    versions: List<PlatformVersion>,
    mcVersions: Array<String>,
    loaders: List<String>
): PlatformVersion? {
    // 优先：同时匹配MC版本和加载器
    if (loaders.isNotEmpty()) {
        val exactMatch = versions.firstOrNull { version ->
            val versionMcVersions = version.platformGameVersion()
            val versionLoaders = version.platformLoaders().map { it.getDisplayName().lowercase() }
            val mcMatch = mcVersions.any { mc -> versionMcVersions.contains(mc) }
            val loaderMatch = loaders.any { loader -> versionLoaders.contains(loader) }
            mcMatch && loaderMatch
        }
        if (exactMatch != null) return exactMatch
    }

    // 回退：只匹配MC版本
    return versions.firstOrNull { version ->
        val versionMcVersions = version.platformGameVersion()
        mcVersions.any { mc -> versionMcVersions.contains(mc) }
    }
}
