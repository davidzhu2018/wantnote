package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.HeatmapCell
import com.davidzhu.wantnote.data.HeatmapGranularity
import com.davidzhu.wantnote.data.StatBucket
import com.davidzhu.wantnote.data.WritingStats
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.WantFilterChip
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 0.17.2：数据统计页面（总笔记/卡片盒统计、多维度标签/卡片盒分布、创作时段统计、
 * 支持日/周/月/年颗粒度切换与自定义颜色的创作热力图）。
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val notesRepo = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val items by notesRepo.observeAllWithDetails().collectAsStateWithLifecycle(initialValue = emptyList())
    val boxes by notesRepo.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val tags by notesRepo.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()

    var granularity by remember { mutableStateOf(HeatmapGranularity.DAY) }
    var selectedCell by remember { mutableStateOf<HeatmapCell?>(null) }

    val summary = remember(items, boxes, tags) {
        WritingStats.summarize(items, boxes, tags)
    }
    val timestamps = remember(items) {
        items.map { if (it.note.createdAt > 0L) it.note.createdAt else it.note.updatedAt }
    }
    val heatmapCells = remember(timestamps, granularity) {
        WritingStats.buildHeatmap(timestamps, granularity)
    }
    val baseHeatmapColor = remember(settings.heatmapColorHex) {
        Color(WritingStats.colorArgbForHex(settings.heatmapColorHex))
    }

    SettingsSubScaffold(title = "数据统计", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 总览卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatKpiCard("总笔记数", "${summary.totalNotes}", Modifier.weight(1f))
                StatKpiCard("总卡片盒数", "${summary.totalBoxes}", Modifier.weight(1f))
                StatKpiCard("总标签数", "${summary.totalTags}", Modifier.weight(1f))
            }

            // 2. 创作热力图
            StatSectionCard(title = "创作热力图") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeatmapGranularity.entries.forEach { g ->
                        WantFilterChip(
                            selected = granularity == g,
                            onClick = {
                                granularity = g
                                selectedCell = null
                            },
                            label = { Text(g.label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "热力图颜色：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WritingStats.HEATMAP_COLORS.forEach { preset ->
                        val isCurrent = settings.heatmapColorHex.equals(preset.hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(preset.argb))
                                .border(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    scope.launch {
                                        settingsRepo.update { it.copy(heatmapColorHex = preset.hex) }
                                    }
                                },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HeatmapGrid(
                    cells = heatmapCells,
                    granularity = granularity,
                    baseColor = baseHeatmapColor,
                    selectedKey = selectedCell?.key,
                    onSelect = { selectedCell = it },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedCell?.let { "${it.label}：${it.count} 条笔记" }
                            ?: "点击色块查看对应时间段笔记数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        (0..4).forEach { lvl ->
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(heatmapCellColor(baseHeatmapColor, lvl)),
                            )
                        }
                        Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 3. 记录笔记时段分布
            StatSectionCard(title = "记录笔记时段") {
                Text(
                    text = "最常创作时段：${summary.peakPeriodLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                StatBucketBars(buckets = summary.byPeriod, barColor = baseHeatmapColor)
            }

            // 4. 按卡片盒统计
            StatSectionCard(title = "按卡片盒统计") {
                if (summary.byBox.isEmpty()) {
                    Text("暂无卡片盒数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    StatBucketBars(buckets = summary.byBox, barColor = MaterialTheme.colorScheme.primary)
                }
            }

            // 5. 按标签统计
            StatSectionCard(title = "按标签统计") {
                if (summary.byTag.isEmpty()) {
                    Text("暂无标签数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    StatBucketBars(buckets = summary.byTag, barColor = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatKpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun StatBucketBars(buckets: List<StatBucket>, barColor: Color) {
    val maxVal = buckets.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        buckets.forEach { bucket ->
            val ratio = (bucket.count.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${bucket.count} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                ) {
                    if (ratio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio.coerceAtLeast(0.03f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor.copy(alpha = 0.85f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(
    cells: List<HeatmapCell>,
    granularity: HeatmapGranularity,
    baseColor: Color,
    selectedKey: String?,
    onSelect: (HeatmapCell) -> Unit,
) {
    when (granularity) {
        HeatmapGranularity.DAY -> {
            // 12 列 × 7 行（GitHub 风格日历热力图）
            val columns = cells.chunked(7)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                columns.forEach { col ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        col.forEach { cell ->
                            HeatmapSquare(
                                cell = cell,
                                baseColor = baseColor,
                                selected = cell.key == selectedKey,
                                sizeDp = 20,
                                onClick = { onSelect(cell) },
                            )
                        }
                    }
                }
            }
        }
        HeatmapGranularity.WEEK -> {
            // 24 周：每行 6 周 × 4 行
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cells.chunked(6).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { cell ->
                            HeatmapLabeledTile(
                                cell = cell,
                                shortLabel = cell.key.substringAfter('-'),
                                baseColor = baseColor,
                                selected = cell.key == selectedKey,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(cell) },
                            )
                        }
                    }
                }
            }
        }
        HeatmapGranularity.MONTH -> {
            // 12 个月：每行 4 月 × 3 行
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cells.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { cell ->
                            HeatmapLabeledTile(
                                cell = cell,
                                shortLabel = cell.key.substringAfter('-') + "月",
                                baseColor = baseColor,
                                selected = cell.key == selectedKey,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(cell) },
                            )
                        }
                    }
                }
            }
        }
        HeatmapGranularity.YEAR -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cells.forEach { cell ->
                    HeatmapLabeledTile(
                        cell = cell,
                        shortLabel = cell.label,
                        baseColor = baseColor,
                        selected = cell.key == selectedKey,
                        modifier = Modifier.width(68.dp),
                        onClick = { onSelect(cell) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapSquare(
    cell: HeatmapCell,
    baseColor: Color,
    selected: Boolean,
    sizeDp: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(heatmapCellColor(baseColor, cell.level))
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun HeatmapLabeledTile(
    cell: HeatmapCell,
    shortLabel: String,
    baseColor: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(heatmapCellColor(baseColor, cell.level))
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (cell.level >= 3) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = "${cell.count}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (cell.level >= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun heatmapCellColor(baseColor: Color, level: Int): Color {
    return when (level) {
        0 -> MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        1 -> baseColor.copy(alpha = 0.26f)
        2 -> baseColor.copy(alpha = 0.50f)
        3 -> baseColor.copy(alpha = 0.76f)
        else -> baseColor.copy(alpha = 1.0f)
    }
}
