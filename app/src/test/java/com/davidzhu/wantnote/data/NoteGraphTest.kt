package com.davidzhu.wantnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteGraphTest {
    private fun graph() = NoteGraph.build(
        notes = listOf(
            "a" to "甲",
            "b" to "乙",
            "c" to "丙",
            "lonely" to "孤立笔记",
        ),
        links = listOf(
            "a" to "b",
            "b" to "a", // duplicate in the other direction collapses in edges? no: kept as distinct pair
            "b" to "c",
            "c" to "c", // self link ignored
            "a" to "ghost", // dangling ignored
        ),
    )

    @Test
    fun buildDropsSelfAndDanglingLinks() {
        val g = graph()
        val ids = g.nodes.map { it.id }.toSet()
        assertTrue("a" in ids && "b" in ids && "c" in ids)
        assertFalse("lonely" in ids)
        assertEquals(setOf("a" to "b", "b" to "a", "b" to "c"), g.edges.map { it.sourceId to it.targetId }.toSet())
    }

    @Test
    fun degreesCountBothDirectionsAndNodesSortByDegree() {
        val g = graph()
        assertEquals(3, g.node("b")?.degree) // a->b, b->a, b->c
        assertEquals(2, g.node("a")?.degree) // a->b, b->a
        assertEquals(1, g.node("c")?.degree) // b->c only
        assertEquals("b", g.nodes.first().id) // highest degree first
    }

    @Test
    fun neighborsAreBidirectional() {
        val g = graph()
        assertEquals(setOf("a", "c"), g.neighbors.getValue("b"))
        assertTrue("b" in g.neighbors.getValue("a"))
        assertTrue("b" in g.neighbors.getValue("c"))
    }

    @Test
    fun buildWithoutLinksIsEmpty() {
        val g = NoteGraph.build(notes = listOf("a" to "甲"), links = emptyList())
        assertTrue(g.isEmpty)
        assertEquals(0, g.nodes.size)
        assertEquals(0, g.edges.size)
    }

    @Test
    fun buildIgnoresBlankNoteIds() {
        val g = NoteGraph.build(notes = listOf("" to "无 ID"), links = emptyList())
        assertTrue(g.isEmpty)
    }

    @Test
    fun highlightedContainsSelectedPlusNeighbors() {
        val g = graph()
        assertEquals(setOf("b", "a", "c"), g.highlighted("b"))
        assertEquals(setOf("a", "b"), g.highlighted("a"))
    }

    @Test
    fun highlightedIsSafeForNullUnknownOrIsolatedIds() {
        val g = graph()
        assertTrue(g.highlighted(null).isEmpty())
        assertTrue(g.highlighted("ghost").isEmpty())
        assertTrue(g.highlighted("lonely").isEmpty())
    }

    @Test
    fun isHighlightedEdgeTouchesSelected() {
        val g = graph()
        val ab = g.edges.first { it.sourceId == "a" && it.targetId == "b" }
        val bc = g.edges.first { it.sourceId == "b" && it.targetId == "c" }
        assertFalse(g.isHighlightedEdge(ab, "c")) // c is not an endpoint of a-b
        assertTrue(g.isHighlightedEdge(bc, "c"))
        assertFalse(g.isHighlightedEdge(ab, null))
    }

    @Test
    fun layoutIsDeterministicAndBounded() {
        val g = graph()
        val first = NoteGraphLayout.compute(g)
        val second = NoteGraphLayout.compute(g)
        assertEquals(first, second)
        first.values.forEach { point ->
            assertTrue(point.x in 0.05f..0.95f)
            assertTrue(point.y in 0.05f..0.95f)
        }
        assertEquals(g.nodes.map { it.id }.toSet(), first.keys.toSet())
    }

    @Test
    fun layoutHandlesEmptyAndSingleNodeGraphs() {
        assertEquals(emptyMap<String, GraphPoint>(), NoteGraphLayout.compute(NoteGraph(emptyList(), emptyList(), emptyMap())))
        val single = NoteGraph(
            nodes = listOf(NoteGraphNode("a", "甲", 0)),
            edges = emptyList(),
            neighbors = mapOf("a" to emptySet()),
        )
        val points = NoteGraphLayout.compute(single)
        assertEquals(1, points.size)
        assertEquals(GraphPoint(0.5f, 0.5f), points["a"])
    }

    @Test
    fun isolatedNodesOmittedEvenWithBlankTitles() {
        val g = NoteGraph.build(
            notes = listOf("a" to "", "b" to "乙"),
            links = listOf("a" to "b"),
        )
        assertEquals(setOf("a", "b"), g.nodes.map { it.id }.toSet())
        assertEquals("", g.node("a")?.title)
    }
}
