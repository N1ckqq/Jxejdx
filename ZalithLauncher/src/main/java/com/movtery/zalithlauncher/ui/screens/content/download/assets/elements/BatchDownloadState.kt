/*
 * Batch asset download state management (mods, resource packs, worlds)
 */

package com.movtery.zalithlauncher.ui.screens.content.download.assets.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.movtery.zalithlauncher.game.download.assets.platform.Platform
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformSearchData

/**
 * Represents a selected asset item (mod / resource pack / world) for batch download.
 * Renamed conceptually to cover all asset types, but kept as SelectedMod for API compatibility.
 */
@Stable
data class SelectedMod(
    val platform: Platform,
    val projectId: String,
    val title: String,
    val iconUrl: String? = null
)

/**
 * State holder for batch asset selection and download.
 * Works for any asset type: mods, resource packs, worlds.
 */
@Stable
class BatchDownloadState {
    /** Whether selection mode is active */
    var isSelectionMode by mutableStateOf(false)
        private set

    /** Map of projectId -> SelectedMod for currently selected assets */
    private val _selectedMods = mutableStateMapOf<String, SelectedMod>()
    val selectedMods: Map<String, SelectedMod> get() = _selectedMods

    /** Number of currently selected assets */
    val selectedCount: Int get() = _selectedMods.size

    /** Whether any assets are selected */
    val hasSelection: Boolean get() = _selectedMods.isNotEmpty()

    /** Toggle selection mode on/off */
    fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        if (!isSelectionMode) {
            _selectedMods.clear()
        }
    }

    /** Enable selection mode */
    fun enableSelectionMode() {
        isSelectionMode = true
    }

    /** Disable selection mode and clear selections */
    fun disableSelectionMode() {
        isSelectionMode = false
        _selectedMods.clear()
    }

    /** Check if an asset is selected */
    fun isSelected(projectId: String): Boolean = _selectedMods.containsKey(projectId)

    /** Toggle selection state of an asset */
    fun toggleSelection(item: PlatformSearchData) {
        val projectId = item.platformId()
        if (_selectedMods.containsKey(projectId)) {
            _selectedMods.remove(projectId)
        } else {
            _selectedMods[projectId] = SelectedMod(
                platform = item.platform(),
                projectId = projectId,
                title = item.platformTitle(),
                iconUrl = item.platformIconUrl()
            )
        }
    }

    /** Select all assets from the current page */
    fun selectAll(items: List<PlatformSearchData>) {
        items.forEach { item ->
            val projectId = item.platformId()
            if (!_selectedMods.containsKey(projectId)) {
                _selectedMods[projectId] = SelectedMod(
                    platform = item.platform(),
                    projectId = projectId,
                    title = item.platformTitle(),
                    iconUrl = item.platformIconUrl()
                )
            }
        }
    }

    /** Deselect all assets */
    fun deselectAll() {
        _selectedMods.clear()
    }

    /** Get list of all selected assets */
    fun getSelectedList(): List<SelectedMod> = _selectedMods.values.toList()
}

@Composable
fun rememberBatchDownloadState(): BatchDownloadState {
    return remember { BatchDownloadState() }
}
