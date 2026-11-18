package com.davidzhu.wantnote.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidzhu.wantnote.data.GraphPoint
import com.davidzhu.wantnote.data.NoteGraph
import com.davidzhu.wantnote.data.NoteGraphLayout
import com.davidzhu.wantnote.data.NoteLink
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import kotlin.math.hypot
import kotlin.math.min

/** Selection/neighbour highlight for the graph tab (single-tap, GREEN). */
private val GraphHighlightGreen = Color(0xFF2FA84F)

/** Zoom limits for both pinch gestures and the +/- buttons. */
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 3f

/**
 * 洞察 → 图谱 tab: a nebula-style view of the note link relation.
 *
 * Data comes from the SAME stable-ID `note_links` table the card footers
 * use, so edits in the editor, deletions and restores stay consistent
 * everywhere. Isolated notes (no links) are omitted by [NoteGraph.build];
 * an empty result shows an explanatory empty state instead of dots.
 *
 * Gestures: single tap selects a node and highlights it plus its direct
 * in/out neighbours and their connecting lines in green; double tap opens
 * the note. detectTapGestures arbitrates the two itself (single tap only
 * fires after the double-tap window), so they cannot conflict. Zoom via
 * pinch and via the bottom-right +/- buttons, clamped to 0.5x..3x.
 */
@Composable
fun GraphTab(
    items: List<NoteWithDetails>,
    links: List<NoteLink>,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = remember(items, links) {
        NoteGraph.build(
            notes = items.map { it.note.id to SearchText.displayTitle(it.note) },
            links = links.map { it.sourceNoteId to it.targetNoteId },
        )
    }
    if (graph.isEmpty) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("图谱还是空的", style = MaterialTheme.typography.titleMedium)
            Text(
                "在编辑器里输入 @ 或点工具栏「@」，把两篇笔记连起来，链接的笔记就会出现在这张图谱上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    val layout = remember(graph) { NoteGraphLayout.compute(graph) }
    var selectedId by remember(graph) { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }

    // Fresh values for gesture callbacks without restarting gesture
    // detection mid-pinch.
    val currentScale by rememberUpdatedState(scale)
    val currentLayout by rememberUpdatedState(layout)
    val currentGraph by rememberUpdatedState(graph)

    val normalNodeColor = MaterialTheme.colorScheme.primary
    val normalEdgeColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun hitTest(offset: Offset): String? {
                        var best: String? = null
                        var bestDistance = Float.MAX_VALUE
                        forEachNodePosition(currentLayout, size.width.toFloat(), size.height.toFloat(), currentScale) { id, center ->
                            val degree = currentGraph.node(id)?.degree ?: 0
                            val distance = hypot(offset.x - center.x, offset.y - center.y)
                            val hitRadius =
                                (8.dp.toPx() + minOf(degree, 6) * 2.dp.toPx()) * currentScale + 12.dp.toPx()
                            if (distance <= hitRadius && distance < bestDistance) {
                                bestDistance = distance
                                best = id
                            }
                        }
                        return best
                    }
                    detectTapGestures(
                        onTap = { offset -> selectedId = hitTest(offset) },
                        onDoubleTap = { offset -> hitTest(offset)?.let(onOpenNote) },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    }
                },
        ) {
            val highlighted = graph.highlighted(selectedId)
            graph.edges.forEach { edge ->
                val from = layout[edge.sourceId] ?: return@forEach
                val to = layout[edge.targetId] ?: return@forEach
                val isHot = graph.isHighlightedEdge(edge, selectedId)
                drawLine(
                    color = if (isHot) GraphHighlightGreen else normalEdgeColor.copy(alpha = 0.5f),
                    start = toScreen(from, size.width, size.height, scale),
                    end = toScreen(to, size.width, size.height, scale),
                    strokeWidth = if (isHot) 3.dp.toPx() else 1.5.dp.toPx(),
                )
            }
            graph.nodes.forEach { node ->
                val point = layout[node.id] ?: return@forEach
                val center = toScreen(point, size.width, size.height, scale)
                val radius = nodeRadiusPx(node.degree) * scale
                val hot = node.id in highlighted
                if (hot) {
                    drawCircle(
                        color = GraphHighlightGreen.copy(alpha = 0.25f),
                        radius = radius + 4.dp.toPx(),
                        center = center,
                    )
                }
                drawCircle(
                    color = if (hot) GraphHighlightGreen else normalNodeColor.copy(alpha = 0.85f),
                    radius = radius,
                    center = center,
                )
                val label = node.title.let { if (it.length > 8) it.take(8) + "…" else it }
                val measured = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        fontSize = (11 * scale).sp,
                        color = if (hot) GraphHighlightGreen else labelColor,
                    ),
                )
                drawText(
                    measured,
                    topLeft = Offset(
                        center.x - measured.size.width / 2f,
                        center.y + radius + 2.dp.toPx(),
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoomButton(Icons.Filled.Add, "放大") {
                scale = (scale + 0.25f).coerceAtMost(MAX_ZOOM)
            }
            ZoomButton(Icons.Outlined.Remove, "缩小") {
                scale = (scale - 0.25f).coerceAtLeast(MIN_ZOOM)
            }
        }
    }
}

/** Node dot radius in px before zoom, growing gently with the degree. */
private fun DrawScope.nodeRadiusPx(degree: Int): Float =
    8.dp.toPx() + minOf(degree, 6) * 2.dp.toPx()

/**
 * Map a normalised layout point to canvas pixels, scaled around the canvas
 * centre. [size] and the canvas centre are the same in draw and hit-test,
 * so taps map exactly onto what is drawn.
 */
private fun toScreen(point: GraphPoint, width: Float, height: Float, scale: Float): Offset {
    val side = min(width, height)
    val cx = width / 2f
    val cy = height / 2f
    return Offset(
        x = cx + (point.x * side - cx) * scale,
        y = cy + (point.y * side - cy) * scale,
    )
}

private inline fun forEachNodePosition(
    layout: Map<String, GraphPoint>,
    width: Float,
    height: Float,
    scale: Float,
    onNode: (String, Offset) -> Unit,
) {
    layout.forEach { (id, point) -> onNode(id, toScreen(point, width, height, scale)) }
}

@Composable
private fun ZoomButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(10.dp)
                .size(22.dp),
        )
    }
}
