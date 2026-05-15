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

package com.movtery.zalithlauncher.ui.screens.content.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.assets.downloadBatchAssets
import com.movtery.zalithlauncher.game.download.assets.downloadSingleForVersions
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformClasses
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.download.assets.download.DownloadAssetsScreen
import com.movtery.zalithlauncher.ui.screens.content.download.assets.elements.BatchVersionSelectDialog
import com.movtery.zalithlauncher.ui.screens.content.download.assets.elements.DownloadSingleOperation
import com.movtery.zalithlauncher.ui.screens.content.download.assets.elements.rememberBatchDownloadState
import com.movtery.zalithlauncher.ui.screens.content.download.assets.search.SearchResourcePackScreen
import com.movtery.zalithlauncher.ui.screens.navigateTo
import com.movtery.zalithlauncher.ui.screens.onBack
import com.movtery.zalithlauncher.ui.screens.rememberTransitionSpec
import com.movtery.zalithlauncher.utils.network.isUsingMobileData
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.EventViewModel

@Composable
fun DownloadResourcePackScreen(
    key: NestedNavKey.DownloadResourcePack,
    mainScreenKey: TitledNavKey?,
    downloadScreenKey: TitledNavKey?,
    downloadResourcePackScreenKey: TitledNavKey?,
    onCurrentKeyChange: (TitledNavKey?) -> Unit,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    eventViewModel: EventViewModel
) {
    val backStack = key.backStack
    val stackTopKey = backStack.lastOrNull()
    LaunchedEffect(stackTopKey) {
        onCurrentKeyChange(stackTopKey)
    }

    val context = LocalContext.current

    // Batch download state
    val batchState = rememberBatchDownloadState()
    var showBatchDialog by remember { mutableStateOf(false) }

    if (showBatchDialog) {
        BatchVersionSelectDialog(
            modCount = batchState.selectedCount,
            titleRes = R.string.download_resource_pack_batch_install_for_versions,
            onDismiss = { showBatchDialog = false },
            onInstall = { gameVersions ->
                showBatchDialog = false
                val selected = batchState.getSelectedList()
                batchState.disableSelectionMode()
                downloadBatchAssets(
                    context = context,
                    assets = selected,
                    targetVersions = gameVersions,
                    folder = PlatformClasses.RESOURCE_PACK.versionFolder.folderName,
                    submitError = submitError
                )
            }
        )
    }

    // Single download operation state
    var operation by remember { mutableStateOf<DownloadSingleOperation>(DownloadSingleOperation.None) }
    DownloadSingleOperation(
        operation = operation,
        changeOperation = { operation = it },
        doInstall = { classes, version, versions, _ ->
            downloadSingleForVersions(
                context = context,
                version = version,
                versions = versions,
                folder = classes.versionFolder.folderName,
                submitError = submitError
            )
        },
        onDependencyClicked = { dep, classes ->
            backStack.navigateTo(
                NormalNavKey.DownloadAssets(dep.platform, dep.projectId, classes)
            )
        }
    )

    if (backStack.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = {
                    onBack(backStack)
                },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                transitionSpec = rememberTransitionSpec(),
                popTransitionSpec = rememberTransitionSpec(),
                entryProvider = entryProvider {
                    entry<NormalNavKey.SearchResourcePack> {
                        SearchResourcePackScreen(
                            mainScreenKey = mainScreenKey,
                            downloadScreenKey = downloadScreenKey,
                            downloadResourcePackScreenKey = key,
                            downloadResourcePackScreenCurrentKey = downloadResourcePackScreenKey,
                            batchState = batchState
                        ) { platform, projectId, _ ->
                            backStack.navigateTo(
                                NormalNavKey.DownloadAssets(platform, projectId, PlatformClasses.RESOURCE_PACK)
                            )
                        }
                    }
                    entry<NormalNavKey.DownloadAssets> { assetsKey ->
                        DownloadAssetsScreen(
                            mainScreenKey = mainScreenKey,
                            parentScreenKey = key,
                            parentCurrentKey = downloadScreenKey,
                            currentKey = downloadResourcePackScreenKey,
                            key = assetsKey,
                            eventViewModel = eventViewModel,
                            onItemClicked = { classes, version, _, deps ->
                                operation = if (isUsingMobileData(context)) {
                                    DownloadSingleOperation.WarningForMobileData(classes, version, deps)
                                } else {
                                    DownloadSingleOperation.SelectVersion(classes, version, deps)
                                }
                            }
                        )
                    }
                }
            )

            // FAB for batch download
            AnimatedVisibility(
                visible = batchState.isSelectionMode && batchState.hasSelection,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showBatchDialog = true },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(text = stringResource(R.string.download_resource_pack_batch_selected, batchState.selectedCount))
                    }
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize())
    }
}
