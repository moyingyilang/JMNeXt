package com.jmnext.ui.screens.favorites

import androidx.compose.ui.res.stringResource
import com.jmnext.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmnext.data.remote.dto.FavoriteFolder
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Spacing

/**
 * 收藏夹相关的对话框状态。
 *
 * 用密封接口而不是几个布尔量：这些对话框**互斥**（同时弹两个是不可能的交互），
 * 用 `isCreateOpen`/`isRenameOpen`/... 表达会出现「两个都为 true」这种不该存在的状态。
 */
sealed interface FolderDialog {
    data object None : FolderDialog
    data object Create : FolderDialog
    data class Rename(val folder: FavoriteFolder) : FolderDialog
    data class Delete(val folder: FavoriteFolder) : FolderDialog
    /** 把某个作品移入收藏夹。 */
    data class Move(val comicId: String) : FolderDialog

    /** 管理列表：集中入口，用于重命名/删除已有收藏夹。 */
    data object Manage : FolderDialog
}

/** 新建 / 重命名收藏夹。两者只差标题与初值。 */
@Composable
fun FolderNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val c = JmTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.folder_ok), color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel), color = c.textSecondary) }
        },
    )
}

/** 删除收藏夹的二次确认。 */
@Composable
fun FolderDeleteDialog(
    folder: FavoriteFolder,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = JmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_delete_title)) },
        text = {
            Text(stringResource(R.string.folder_delete_confirm, folder.name ?: folder.folderId))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.folder_delete), color = c.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel), color = c.textSecondary) }
        },
    )
}

/**
 * 选择目标收藏夹。
 *
 * 用于两处：收藏后归类，以及在收藏列表里把作品移到别的夹。
 */
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FavoriteFolder>,
    onDismiss: () -> Unit,
    onPick: (FavoriteFolder) -> Unit,
    /** 可选的「不归类」出口。收藏后引导归类时用得上：归类应当是可选动作，不是必经步骤。 */
    onSkip: (() -> Unit)? = null,
    skipLabel: String? = null,
) {
    val c = JmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (folders.isEmpty()) {
                Text(stringResource(R.string.folder_none_in_move))
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onPick(folder) }) {
                                Text(
                                    text = folder.name ?: folder.folderId,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = c.text,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSkip ?: onDismiss) {
                Text(if (onSkip != null) skipLabel ?: stringResource(R.string.folder_skip_only_fav) else stringResource(R.string.folder_close), color = c.textSecondary)
            }
        },
    )
}

/**
 * 收藏夹管理：列出全部收藏夹，逐个提供改名与删除，并提供新建入口。
 */
@Composable
fun ManageFoldersDialog(
    folders: List<FavoriteFolder>,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onRename: (FavoriteFolder) -> Unit,
    onDelete: (FavoriteFolder) -> Unit,
) {
    val c = JmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_manage)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textTertiary,
                    )
                }
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = folder.name ?: folder.folderId,
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRename(folder) }) {
                            Text(stringResource(R.string.folder_rename_short), color = c.accent)
                        }
                        TextButton(onClick = { onDelete(folder) }) {
                            Text(stringResource(R.string.folder_delete), color = c.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) { Text("新建收藏夹", color = c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = c.textSecondary) }
        },
    )
}
