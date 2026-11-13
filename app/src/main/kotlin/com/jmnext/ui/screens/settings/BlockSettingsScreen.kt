package com.jmnext.ui.screens.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jmnext.R
import com.jmnext.data.prefs.BlockKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmnext.data.BlockRules
import com.jmnext.data.prefs.BlockStore
import com.jmnext.ui.LocalRepository
import com.jmnext.ui.components.GlassLevel
import com.jmnext.ui.components.GlassSurface
import com.jmnext.ui.components.GlassTopBar
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Radius
import com.jmnext.ui.theme.Spacing

/** 名单条目的长度约束：太短会误伤（一个字屏蔽掉半个站），太长基本用不上。 */
private const val MIN_LENGTH = 2
private const val MAX_LENGTH = 20

/**
 * 屏蔽设置。
 *
 * 三类名单。**页面上写清每条规则的生效范围**比「能加能删」更重要：
 * 用户需要知道列表里为什么少了东西，以及标签屏蔽为什么只在详情页提示。
 */
@Composable
fun BlockSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val repo = LocalRepository.current
    val store = repo.blockStore
    val rules by store?.state?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(BlockRules()) }
    val c = JmTheme.colors
    var dialog by remember { mutableStateOf<BlockKind?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = stringResource(R.string.block_title),
            subtitle = stringResource(R.string.block_subtitle),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.block_back),
                        tint = c.accent,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            notice?.let { text ->
                item(key = "notice") {
                    Text(text = text, style = MaterialTheme.typography.labelSmall, color = c.accent)
                }
            }

            item(key = "hint") {
                GlassSurface(level = GlassLevel.Card) {
                    Text(
                          text = stringResource(R.string.block_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            item(key = "words") {
                BlockSection(
                    title = stringResource(R.string.block_words_title),
                    hint = stringResource(R.string.block_words_hint, rules.words.size),
                    values = rules.words.toList(),
                    onAdd = { dialog = BlockKind.Word },
                    onRemove = { store?.removeWord(it) },
                )
            }

            item(key = "categories") {
                BlockSection(
                    title = stringResource(R.string.block_categories_title),
                    hint = stringResource(R.string.block_categories_hint, rules.categories.size),
                    values = rules.categories.toList(),
                    onAdd = { dialog = BlockKind.Category },
                    onRemove = { store?.removeCategory(it) },
                )
            }

            item(key = "tags") {
                BlockSection(
                    title = stringResource(R.string.block_tags_title),
                    hint = stringResource(R.string.block_tags_hint, rules.tags.size),
                    values = rules.tags.toList(),
                    onAdd = { dialog = BlockKind.Tag },
                    onRemove = { store?.removeTag(it) },
                )
            }
        }
    }

    dialog?.let { kind ->
        AddBlockDialog(
            kind = kind,
            onDismiss = { dialog = null },
            onConfirm = { value ->
                val trimmed = value.trim()
                notice = when {
                    trimmed.length < MIN_LENGTH || trimmed.length > MAX_LENGTH ->
                        ctx.getString(R.string.block_length_fmt, MIN_LENGTH, MAX_LENGTH)

                    store?.isBlocked(kind, trimmed) == true -> ctx.getString(R.string.block_exists_fmt, trimmed)

                    else -> {
                        when (kind) {
                            BlockKind.Word -> store?.addWord(trimmed)
                            BlockKind.Tag -> store?.addTag(trimmed)
                            BlockKind.Category -> store?.addCategory(trimmed)
                        }
                        ctx.getString(R.string.block_added_fmt, trimmed)
                    }
                }
                dialog = null
            },
        )
    }
}

/** 一份名单：标题 + 生效范围 + 已加条目（逐条可删）+ 添加按钮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockSection(
    title: String,
    hint: String,
    values: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val c = JmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.block_add_cd_fmt, title), tint = c.accent)
            }
        }

        if (values.isEmpty()) {
            Text(
                text = stringResource(R.string.block_empty),
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        } else {
            GlassSurface(level = GlassLevel.Card) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    values.sorted().forEach { value ->
                        Surface(shape = RoundedCornerShape(Radius.pill), color = c.accentSoft) {
                            Row(
                                modifier = Modifier.padding(
                                    start = Spacing.md,
                                    end = Spacing.xs,
                                    top = Spacing.xs,
                                    bottom = Spacing.xs,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.accent,
                                )
                                IconButton(
                                    onClick = { onRemove(value) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.block_remove_cd_fmt, value),
                                        tint = c.accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 添加一条：只负责收字，长度与重复校验由调用方处理（那里才知道名单是哪一份）。 */
@Composable
private fun AddBlockDialog(
    kind: BlockKind,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = JmTheme.colors
    var text by remember { mutableStateOf("") }
    val label = when (kind) {
        BlockKind.Word -> stringResource(R.string.block_kind_word)
        BlockKind.Tag -> stringResource(R.string.block_kind_tag)
        BlockKind.Category -> stringResource(R.string.block_kind_category)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_add_title_fmt, label)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = when (kind) {
                                BlockKind.Word -> stringResource(R.string.block_eg_word)
                                BlockKind.Tag -> stringResource(R.string.block_eg_tag)
                                BlockKind.Category -> stringResource(R.string.block_eg_category)
                            },
                            color = c.textTertiary,
                        )
                    },
                )
                Text(
                    text = stringResource(R.string.block_len_hint_fmt, MIN_LENGTH, MAX_LENGTH),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.block_add), color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.block_cancel), color = c.textSecondary) }
        },
    )
}
