package com.davidzhu.wantnote.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.davidzhu.wantnote.ui.components.WantFilterChip
import com.davidzhu.wantnote.ui.components.WantSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Selection/neighbour highlight for the graph tab (single-tap, GREEN). */
private val GraphHighlightGreen = Color(0xFF2FA84F)
private val NebulaHighlightCyan = Color(0xFF4ADE80)

/** Zoom limits for both pinch gestures and the +/- buttons. */
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 3f

private enum class GraphViewMode(val label: String) {
    FLAT_2D("点线视图 (2D)"),
    NEBULA_3D("星云视图 (3D)"),
}

/** 3D 球面星云坐标。 */
internal data class GraphPoint3D(val x: Float, val y: Float, val z: Float)

/** 投影到屏幕后的 3D 节点信息。 */
private data class ProjectedNode(
    val id: String,
    val title: String,
    val degree: Int,
    val screen: Offset,
    val depth: Float, // 旋转后的 Z，越大越近
    val persp: Float, // 透视缩放系数
)

/**
 * 基于黄金角斐波那契球面分布生成确定性的 3D 星云坐标：
 * 连接度越高的枢纽笔记更靠近星云核心，其余节点按星云旋臂立体分布。
 */
internal fun computeNebula3DLayout(graph: NoteGraph): Map<String, GraphPoint3D> {
    val nodes = graph.nodes.sortedByDescending { it.degree }
    val n = nodes.size
    if (n == 0) return emptyMap()
    val goldenAngle = (PI * (3.0 - sqrt(5.0))).toFloat()
    val out = LinkedHashMap<String, GraphPoint3D>(n)
    nodes.forEachIndexed { idx, node ->
        val t = if (n == 1) 0f else (idx.toFloat() / (n - 1).toFloat())
        val y = 1f - 2f * ((idx + 0.5f) / n.toFloat())
        val rPlane = sqrt((1f - y * y).coerceAtLeast(0f))
        val theta = goldenAngle * idx
        // 枢纽节点靠近星云内核（0.32..0.95），形成层次分明的立体星云
        val radial = (0.32f + 0.65f * t).coerceIn(0.28f, 0.98f)
        out[node.id] = GraphPoint3D(
            x = cos(theta) * rPlane * radial,
            y = y * radial,
            z = sin(theta) * rPlane * radial,
        )
    }
    return out
}

/**
 * 洞察 → 图谱 tab：支持「点线视图 (2D)」与「星云视图 (3D)」两种结构切换。
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

    var mode by remember { mutableStateOf(GraphViewMode.FLAT_2D) }
    var selectedId by remember(graph) { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (mode) {
            GraphViewMode.FLAT_2D -> Flat2DGraphCanvas(
                graph = graph,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onOpenNote = onOpenNote,
                modifier = Modifier.fillMaxSize(),
            )
            GraphViewMode.NEBULA_3D -> Nebula3DGraphCanvas(
                graph = graph,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onOpenNote = onOpenNote,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 顶部悬浮模式切换（2D 点线 / 3D 星云）
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GraphViewMode.entries.forEach { candidate ->
                WantFilterChip(
                    selected = mode == candidate,
                    onClick = { mode = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }
    }
}

@Composable
private fun Flat2DGraphCanvas(
    graph: NoteGraph,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(graph) { NoteGraphLayout.compute(graph) }
    var scale by remember { mutableFloatStateOf(1f) }
    val currentScale by rememberUpdatedState(scale)
    val currentLayout by rememberUpdatedState(layout)
    val currentGraph by rememberUpdatedState(graph)

    val normalNodeColor = MaterialTheme.colorScheme.primary
    val normalEdgeColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier) {
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
                        onTap = { offset -> onSelect(hitTest(offset)) },
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

/**
 * 0.20.0：3D 立体星云视图。
 * - 支持单指拖动旋转 3D 视角（yaw / pitch）、双指缩放与 +/- 按钮缩放；
 * - 按透视投影与 Z 深度排序绘制（近大远小、多层星云光晕、深空星尘背景）；
 * - 单击选中节点并高亮三维邻居星团，双击直接打开笔记。
 */
@Composable
private fun Nebula3DGraphCanvas(
    graph: NoteGraph,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout3D = remember(graph) { computeNebula3DLayout(graph) }
    var yaw by remember { mutableFloatStateOf(0.45f) }
    var pitch by remember { mutableFloatStateOf(0.28f) }
    var scale by remember { mutableFloatStateOf(1f) }

    val currentYaw by rememberUpdatedState(yaw)
    val currentPitch by rememberUpdatedState(pitch)
    val currentScale by rememberUpdatedState(scale)
    val currentLayout by rememberUpdatedState(layout3D)
    val currentGraph by rememberUpdatedState(graph)

    val textMeasurer = rememberTextMeasurer()

    // 背景深空星尘（确定性伪随机坐标，避免重绘闪烁）
    val bgStars = remember {
        List(72) { i ->
            val fx = ((i * 73 + 19) % 100) / 100f
            val fy = ((i * 41 + 37) % 100) / 100f
            val alpha = 0.16f + (i % 5) * 0.12f
            val r = 0.8f + (i % 3) * 0.65f
            Triple(Offset(fx, fy), alpha, r)
        }
    }

    fun projectAll(width: Float, height: Float, yAngle: Float, pAngle: Float, zoom: Float): List<ProjectedNode> {
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) * 0.36f * zoom
        val cosY = cos(yAngle)
        val sinY = sin(yAngle)
        val cosP = cos(pAngle)
        val sinP = sin(pAngle)
        val cameraDist = 2.6f

        return currentGraph.nodes.mapNotNull { node ->
            val p = currentLayout[node.id] ?: return@mapNotNull null
            // 绕 Y 轴旋转（水平拖动）
            val x1 = p.x * cosY + p.z * sinY
            val z1 = -p.x * sinY + p.z * cosY
            // 绕 X 轴旋转（上下俯仰）
            val y2 = p.y * cosP - z1 * sinP
            val z2 = p.y * sinP + z1 * cosP
            val persp = (cameraDist / (cameraDist - z2)).coerceIn(0.45f, 2.2f)
            ProjectedNode(
                id = node.id,
                title = node.title,
                degree = node.degree,
                screen = Offset(cx + x1 * baseRadius * persp, cy + y2 * baseRadius * persp),
                depth = z2,
                persp = persp,
            )
        }.sortedBy { it.depth } // 由远及近（画家算法）
    }

    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1E3D), Color(0xFF0E1228), Color(0xFF070914)),
                ),
            ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun hitTest(offset: Offset): String? {
                        val projected = projectAll(
                            size.width.toFloat(),
                            size.height.toFloat(),
                            currentYaw,
                            currentPitch,
                            currentScale,
                        )
                        // 从最近到最远检测命中
                        for (node in projected.asReversed()) {
                            val hitR = ((8.dp.toPx() + minOf(node.degree, 6) * 2.dp.toPx()) * node.persp * currentScale) + 14.dp.toPx()
                            if (hypot(offset.x - node.screen.x, offset.y - node.screen.y) <= hitR) {
                                return node.id
                            }
                        }
                        return null
                    }
                    detectTapGestures(
                        onTap = { offset -> onSelect(hitTest(offset)) },
                        onDoubleTap = { offset -> hitTest(offset)?.let(onOpenNote) },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        yaw += pan.x * 0.0085f
                        pitch = (pitch - pan.y * 0.0085f).coerceIn(-1.35f, 1.35f)
                        scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    }
                },
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val nebulaR = min(size.width, size.height) * 0.45f

            // 1. 星云光晕背景云团
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x386366F1), Color(0x188B5CF6), Color.Transparent),
                    center = Offset(cx - nebulaR * 0.22f, cy - nebulaR * 0.14f),
                    radius = nebulaR * 0.95f,
                ),
                radius = nebulaR * 0.95f,
                center = Offset(cx - nebulaR * 0.22f, cy - nebulaR * 0.14f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3006B6D4), Color(0x1238BDF8), Color.Transparent),
                    center = Offset(cx + nebulaR * 0.24f, cy + nebulaR * 0.18f),
                    radius = nebulaR * 0.85f,
                ),
                radius = nebulaR * 0.85f,
                center = Offset(cx + nebulaR * 0.24f, cy + nebulaR * 0.18f),
            )

            // 2. 深空背景星点
            bgStars.forEach { (norm, alpha, r) ->
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = r.dp.toPx(),
                    center = Offset(norm.x * size.width, norm.y * size.height),
                )
            }

            // 3. 3D 透视投影
            val projected = projectAll(size.width, size.height, yaw, pitch, scale)
            val byId = projected.associateBy { it.id }
            val highlighted = graph.highlighted(selectedId)

            // 4. 绘制立体星云连线（随深度衰减透明度）
            graph.edges.forEach { edge ->
                val a = byId[edge.sourceId] ?: return@forEach
                val b = byId[edge.targetId] ?: return@forEach
                val avgDepth = ((a.depth + b.depth) / 2f + 1f) / 2f
                val isHot = graph.isHighlightedEdge(edge, selectedId)
                val edgeAlpha = if (isHot) 0.92f else (0.14f + 0.34f * avgDepth).coerceIn(0.10f, 0.55f)
                drawLine(
                    color = if (isHot) NebulaHighlightCyan.copy(alpha = edgeAlpha) else Color(0xFF93C5FD).copy(alpha = edgeAlpha),
                    start = a.screen,
                    end = b.screen,
                    strokeWidth = if (isHot) 2.6.dp.toPx() else (0.9f + 0.9f * avgDepth).dp.toPx(),
                )
            }

            // 5. 由远及近绘制 3D 星团节点与发光星核
            val starPalette = listOf(
                Color(0xFF60A5FA),
                Color(0xFFA78BFA),
                Color(0xFF38BDF8),
                Color(0xFFF472B6),
                Color(0xFF818CF8),
            )
            projected.forEachIndexed { idx, node ->
                val depthNorm = ((node.depth + 1f) / 2f).coerceIn(0f, 1f)
                val hot = node.id in highlighted
                val baseColor = if (hot) NebulaHighlightCyan else starPalette[idx % starPalette.size]
                val radius = (6.dp.toPx() + minOf(node.degree, 6) * 1.8.dp.toPx()) * node.persp * scale.coerceIn(0.75f, 1.8f)
                val alpha = (0.38f + 0.62f * depthNorm).coerceIn(0.35f, 1f)

                // 外层星云光晕
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(baseColor.copy(alpha = alpha * 0.55f), Color.Transparent),
                        center = node.screen,
                        radius = radius * 2.6f,
                    ),
                    radius = radius * 2.6f,
                    center = node.screen,
                )
                // 星体主体
                drawCircle(
                    color = baseColor.copy(alpha = alpha),
                    radius = radius,
                    center = node.screen,
                )
                // 星核高光
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.85f),
                    radius = radius * 0.38f,
                    center = Offset(node.screen.x - radius * 0.18f, node.screen.y - radius * 0.18f),
                )

                // 近景或高亮节点显示标题（避免远景文字拥挤）
                if (hot || depthNorm > 0.32f || projected.size <= 16) {
                    val label = node.title.let { if (it.length > 8) it.take(8) + "…" else it }
                    val fontSp = (9.5f + 2.5f * depthNorm).coerceIn(9f, 14f)
                    val measured = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            fontSize = fontSp.sp,
                            color = if (hot) NebulaHighlightCyan else Color.White.copy(alpha = alpha * 0.92f),
                        ),
                    )
                    drawText(
                        measured,
                        topLeft = Offset(
                            node.screen.x - measured.size.width / 2f,
                            node.screen.y + radius + 3.dp.toPx(),
                        ),
                    )
                }
            }
        }

        Text(
            text = "拖动旋转 3D 星云 · 双指缩放 · 双击打开笔记",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )

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
    WantSurface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
