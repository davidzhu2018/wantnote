package com.davidzhu.wantnote.data

import kotlin.math.sqrt
import java.util.Random

data class GraphPoint(val x: Float, val y: Float)

data class NoteGraphNode(
    val id: String,
    val title: String,
    val degree: Int,
)

data class NoteGraphEdge(
    val sourceId: String,
    val targetId: String,
)

/**
 * Pure graph model used by the 洞察 → 图谱 tab. Kept free of Compose and
 * Android so the construction, neighbour lookup and layout maths can be unit
 * tested on the JVM.
 */
data class NoteGraph(
    val nodes: List<NoteGraphNode>,
    val edges: List<NoteGraphEdge>,
    val neighbors: Map<String, Set<String>>,
) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun node(id: String): NoteGraphNode? = nodes.firstOrNull { it.id == id }

    /** Selected node plus every direct incoming/outgoing neighbour. */
    fun highlighted(id: String?): Set<String> {
        if (id == null) return emptySet()
        if (nodes.none { it.id == id }) return emptySet()
        return setOf(id) + neighbors[id].orEmpty()
    }

    fun isHighlightedEdge(edge: NoteGraphEdge, selectedId: String?): Boolean {
        val id = selectedId ?: return false
        return edge.sourceId == id || edge.targetId == id
    }

    companion object {
        /**
         * Build a graph from `id to title` notes and `source to target` links.
         * Isolated notes are omitted so the tab can show a meaningful empty
         * state instead of a nebula of unrelated dots.
         */
        fun build(
            notes: List<Pair<String, String>>,
            links: List<Pair<String, String>>,
        ): NoteGraph {
            val titles = LinkedHashMap<String, String>()
            notes.forEach { (id, title) -> if (id.isNotBlank()) titles[id] = title }
            val edgeKeys = LinkedHashSet<Pair<String, String>>()
            links.forEach { (source, target) ->
                if (source != target && source in titles && target in titles) {
                    edgeKeys += source to target
                }
            }
            if (edgeKeys.isEmpty()) {
                return NoteGraph(emptyList(), emptyList(), emptyMap())
            }
            val degree = HashMap<String, Int>()
            edgeKeys.forEach { (source, target) ->
                degree[source] = (degree[source] ?: 0) + 1
                degree[target] = (degree[target] ?: 0) + 1
            }
            val nodes = titles.filterKeys { degree.containsKey(it) }
                .map { (id, title) -> NoteGraphNode(id, title, degree[id] ?: 0) }
                .sortedByDescending { it.degree }
            val nodeIds = nodes.map { it.id }.toSet()
            val edges = edgeKeys
                .filter { (source, target) -> source in nodeIds && target in nodeIds }
                .map { (source, target) -> NoteGraphEdge(source, target) }
            val neighbors = HashMap<String, MutableSet<String>>()
            nodeIds.forEach { neighbors[it] = linkedSetOf() }
            edges.forEach { edge ->
                neighbors.getValue(edge.sourceId) += edge.targetId
                neighbors.getValue(edge.targetId) += edge.sourceId
            }
            return NoteGraph(
                nodes = nodes,
                edges = edges,
                neighbors = neighbors.mapValues { it.value.toSet() },
            )
        }
    }
}

/**
 * Deterministic force-directed layout in normalised 0..1 coordinates.
 * It is intentionally small: a handful of iterations give a pleasant
 * nebula-like spread without doing heavy work on the UI thread for typical
 * note counts.
 */
object NoteGraphLayout {
    fun compute(
        graph: NoteGraph,
        iterations: Int = 90,
        seed: Long = 20240808L,
    ): Map<String, GraphPoint> {
        if (graph.isEmpty) return emptyMap()
        if (graph.nodes.size == 1) return mapOf(graph.nodes.first().id to GraphPoint(0.5f, 0.5f))
        val random = Random(seed)
        val ids = graph.nodes.map { it.id }
        val index = ids.withIndex().associate { it.value to it.index }
        val n = ids.size
        val px = FloatArray(n)
        val py = FloatArray(n)
        ids.forEachIndexed { i, _ ->
            val angle = (i.toDouble() / n) * Math.PI * 2.0
            val radius = 0.28 + random.nextDouble() * 0.08
            px[i] = (0.5 + kotlin.math.cos(angle) * radius).toFloat()
            py[i] = (0.5 + kotlin.math.sin(angle) * radius).toFloat()
        }
        val edgePairs = graph.edges.mapNotNull { edge ->
            val a = index[edge.sourceId] ?: return@mapNotNull null
            val b = index[edge.targetId] ?: return@mapNotNull null
            a to b
        }
        val iterationsSafe = iterations.coerceIn(1, 300)
        repeat(iterationsSafe) {
            val fx = FloatArray(n)
            val fy = FloatArray(n)
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var dx = px[i] - px[j]
                    var dy = py[i] - py[j]
                    var distSq = dx * dx + dy * dy
                    if (distSq < 0.0001f) {
                        dx = (random.nextFloat() - 0.5f) * 0.01f
                        dy = (random.nextFloat() - 0.5f) * 0.01f
                        distSq = dx * dx + dy * dy + 0.0001f
                    }
                    val dist = sqrt(distSq)
                    val force = 0.0016f / distSq
                    val ux = dx / dist
                    val uy = dy / dist
                    fx[i] += ux * force
                    fy[i] += uy * force
                    fx[j] -= ux * force
                    fy[j] -= uy * force
                }
            }
            edgePairs.forEach { (a, b) ->
                val dx = px[b] - px[a]
                val dy = py[b] - py[a]
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
                val force = (dist - 0.30f) * 0.09f
                val ux = dx / dist
                val uy = dy / dist
                fx[a] += ux * force
                fy[a] += uy * force
                fx[b] -= ux * force
                fy[b] -= uy * force
            }
            for (i in 0 until n) {
                // Gentle gravity to the centre keeps components on screen.
                fx[i] += (0.5f - px[i]) * 0.012f
                fy[i] += (0.5f - py[i]) * 0.012f
                px[i] = (px[i] + fx[i]).coerceIn(0.06f, 0.94f)
                py[i] = (py[i] + fy[i]).coerceIn(0.06f, 0.94f)
            }
        }
        return ids.mapIndexed { i, id -> id to GraphPoint(px[i], py[i]) }.toMap()
    }
}
