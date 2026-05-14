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

package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.database.AppDatabase
import com.movtery.zalithlauncher.game.download.history.DownloadRecord
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.theme.itemColor
import com.movtery.zalithlauncher.ui.theme.onItemColor
import com.movtery.zalithlauncher.utils.file.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadHistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf<List<DownloadRecord>?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun loadRecords() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).downloadRecordDao().getRecent(200)
            }
            records = loaded
        }
    }

    LaunchedEffect(Unit) { loadRecords() }

    if (showClearConfirm) {
        SimpleAlertDialog(
            title = stringResource(R.string.download_history_clear_title),
            text = stringResource(R.string.download_history_clear_confirm),
            onConfirm = {
                showClearConfirm = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(context).downloadRecordDao().deleteAll()
                    }
                    loadRecords()
                }
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row with clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.download_history_title),
                style = MaterialTheme.typography.titleMedium
            )
            FilledTonalButton(
                onClick = { showClearConfirm = true },
                enabled = records?.isNotEmpty() == true
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_outlined),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = stringResource(R.string.generic_clear)
                )
            }
        }

        val currentRecords = records
        when {
            currentRecords == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            currentRecords.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.download_history_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(currentRecords, key = { it.id }) { record ->
                        DownloadHistoryItem(
                            record = record,
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getInstance(context).downloadRecordDao().delete(record)
                                    }
                                    loadRecords()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadHistoryItem(
    record: DownloadRecord,
    onDelete: () -> Unit
) {
    val dateStr = remember(record.downloadedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.downloadedAt))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = itemColor(false),
        contentColor = onItemColor()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = "${record.platform} · ${formatFileSize(record.fileSize)} · $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.installedVersions.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.download_history_installed_for, record.installedVersions),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_outlined),
                    contentDescription = stringResource(R.string.generic_delete)
                )
            }
        }
    }
}
