package com.davidzhu.wantnote.transfer

/**
 * 0.16.5：轻量 Mermaid 流程图解析器（纯 Kotlin，JVM 可单测，离线零依赖）。
 *
 * 支持 `graph TD / TB / LR / RL / BT` 与 `flowchart ...` 语法，
 * 识别常见节点形状（`[矩形]`、`(圆角)`、`{菱形}`、`((圆形))`）及连线标签
 * （`-->`、`-->|标签|`、`-- 标签 -->`、`---`、`-.->`、`==>`），
 * 并按拓扑层级分组供 Compose 原生流程图卡片渲染。
 */
data class MermaidNode(
    val id: String,
    val label: String,
    val shape: Shape = Shape.RECT,
) {
    enum class Shape { RECT, ROUND, DIAMOND, CIRCLE }
}

data class MermaidEdge(
    val from: String,
    val to: String,
    val label: String = "",
)

data class MermaidDiagram(
    val direction: Direction,
    val nodes: List<MermaidNode>,
    val edges: List<MermaidEdge>,
    val layers: List<List<MermaidNode>>,
) {
    enum class Direction { TOP_DOWN, LEFT_RIGHT }
}

object MermaidParser {
    private val headerRegex = Regex("""^(?:graph|flowchart)\s+(TD|TB|BT|LR|RL)\b""", RegexOption.IGNORE_CASE)
    private val edgeRegex = Regex(
        """^(.+?)\s*(?:-->\|([^|]*)\||--\s+([^->\n]+?)\s+-->|-->|==>|-.->|---)\s*(.+?)\s*$""",
    )
    private val nodeTokenRegex = Regex(
        """^([A-Za-z0-9_\u4E00-\u9FA5-]+)\s*(?:\(\((.+?)\)\)|\(\[(.+?)\]\)|\[(.+?)\]|\((.+?)\)|\{(.+?)\})?$""",
    )

    fun isMermaidCodeBlock(rawCodeText: String): Boolean {
        val firstLine = rawCodeText.lineSequence().firstOrNull()?.trim().orEmpty().lowercase()
        return firstLine == "mermaid" || firstLine.startsWith("mermaid ") ||
            headerRegex.containsMatchIn(firstLine)
    }

    fun stripLanguageHeader(rawCodeText: String): String {
        val lines = rawCodeText.replace("\r\n", "\n").split('\n')
        return if (lines.firstOrNull()?.trim()?.lowercase()?.startsWith("mermaid") == true) {
            lines.drop(1).joinToString("\n").trim()
        } else {
            rawCodeText.trim()
        }
    }

    fun parse(rawCodeText: String): MermaidDiagram? {
        val body = stripLanguageHeader(rawCodeText)
        if (body.isBlank()) return null

        val lines = body.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("%%") }
        if (lines.isEmpty()) return null

        var direction = MermaidDiagram.Direction.TOP_DOWN
        var startIdx = 0
        headerRegex.find(lines[0])?.let { m ->
            val dir = m.groupValues[1].uppercase()
            direction = if (dir == "LR" || dir == "RL") {
                MermaidDiagram.Direction.LEFT_RIGHT
            } else {
                MermaidDiagram.Direction.TOP_DOWN
            }
            startIdx = 1
        }

        val nodeMap = LinkedHashMap<String, MermaidNode>()
        val edges = ArrayList<MermaidEdge>()

        fun registerNode(rawToken: String): MermaidNode? {
            val cleaned = rawToken.trim().trimEnd(';')
            if (cleaned.isEmpty()) return null
            val m = nodeTokenRegex.matchEntire(cleaned)
            val node = if (m != null) {
                val id = m.groupValues[1]
                val circle = m.groupValues[2]
                val stadium = m.groupValues[3]
                val rect = m.groupValues[4]
                val round = m.groupValues[5]
                val diamond = m.groupValues[6]
                val (label, shape) = when {
                    circle.isNotEmpty() -> stripQuotes(circle) to MermaidNode.Shape.CIRCLE
                    stadium.isNotEmpty() -> stripQuotes(stadium) to MermaidNode.Shape.ROUND
                    rect.isNotEmpty() -> stripQuotes(rect) to MermaidNode.Shape.RECT
                    round.isNotEmpty() -> stripQuotes(round) to MermaidNode.Shape.ROUND
                    diamond.isNotEmpty() -> stripQuotes(diamond) to MermaidNode.Shape.DIAMOND
                    else -> id to MermaidNode.Shape.RECT
                }
                MermaidNode(id = id, label = label, shape = shape)
            } else {
                MermaidNode(id = cleaned, label = stripQuotes(cleaned), shape = MermaidNode.Shape.RECT)
            }
            val existing = nodeMap[node.id]
            if (existing == null || (existing.label == existing.id && node.label != node.id)) {
                nodeMap[node.id] = node
            }
            return nodeMap.getValue(node.id)
        }

        for (i in startIdx until lines.size) {
            val line = lines[i].trimEnd(';')
            if (line.startsWith("subgraph ", ignoreCase = true) ||
                line.equals("end", ignoreCase = true) ||
                line.startsWith("style ", ignoreCase = true) ||
                line.startsWith("classDef ", ignoreCase = true) ||
                line.startsWith("class ", ignoreCase = true)
            ) {
                continue
            }
            val edgeMatch = edgeRegex.matchEntire(line)
            if (edgeMatch != null) {
                val left = registerNode(edgeMatch.groupValues[1])
                val label = (edgeMatch.groupValues[2].ifEmpty { edgeMatch.groupValues[3] }).trim()
                val right = registerNode(edgeMatch.groupValues[4])
                if (left != null && right != null) {
                    edges += MermaidEdge(from = left.id, to = right.id, label = stripQuotes(label))
                }
            } else {
                registerNode(line)
            }
        }

        if (nodeMap.isEmpty()) return null

        val nodes = nodeMap.values.toList()
        val layers = computeLayers(nodes, edges)
        return MermaidDiagram(
            direction = direction,
            nodes = nodes,
            edges = edges,
            layers = layers,
        )
    }

    private fun stripQuotes(s: String): String {
        val t = s.trim()
        return if ((t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) ||
            (t.startsWith("'") && t.endsWith("'") && t.length >= 2)
        ) {
            t.substring(1, t.length - 1)
        } else {
            t
        }
    }

    private fun computeLayers(
        nodes: List<MermaidNode>,
        edges: List<MermaidEdge>,
    ): List<List<MermaidNode>> {
        val inDegree = HashMap<String, Int>()
        val adj = HashMap<String, MutableList<String>>()
        nodes.forEach {
            inDegree[it.id] = 0
            adj[it.id] = ArrayList()
        }
        edges.forEach { e ->
            if (e.from != e.to && adj.containsKey(e.from) && inDegree.containsKey(e.to)) {
                adj.getValue(e.from) += e.to
                inDegree[e.to] = inDegree.getValue(e.to) + 1
            }
        }
        val level = HashMap<String, Int>()
        val queue = ArrayDeque<String>()
        nodes.filter { inDegree.getValue(it.id) == 0 }.forEach {
            level[it.id] = 0
            queue.add(it.id)
        }
        if (queue.isEmpty() && nodes.isNotEmpty()) {
            level[nodes.first().id] = 0
            queue.add(nodes.first().id)
        }
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val cur = level.getValue(u)
            for (v in adj[u].orEmpty()) {
                val nextLvl = cur + 1
                if ((level[v] ?: -1) < nextLvl && nextLvl <= nodes.size) {
                    level[v] = nextLvl
                    queue.add(v)
                }
            }
        }
        nodes.forEach { if (!level.containsKey(it.id)) level[it.id] = 0 }
        val maxLvl = level.values.maxOrNull() ?: 0
        return (0..maxLvl).map { l -> nodes.filter { level[it.id] == l } }.filter { it.isNotEmpty() }
    }
}
