package app.knotwork.design.components.pipelineeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [NodePorts.forType] — the single source for the
 * outbound-port enumeration per node type. Mirrors `node-specs.md`
 * §Ports.
 */
class NodePortsTest {

    @Test
    fun `given INPUT when forType then no inbound port and one default output`() {
        val ports = NodePorts.forType(NodeType.INPUT)

        assertEquals(0, ports.inbound)
        assertEquals(listOf(OutboundPort.Default), ports.outbound)
    }

    @Test
    fun `given OUTPUT when forType then one inbound and no outbound`() {
        val ports = NodePorts.forType(NodeType.OUTPUT)

        assertEquals(1, ports.inbound)
        assertTrue(ports.outbound.isEmpty())
    }

    @Test
    fun `given IF_CONDITION when forType then True and False ports in order`() {
        val ports = NodePorts.forType(NodeType.IF_CONDITION)

        assertEquals(listOf(OutboundPort.True, OutboundPort.False), ports.outbound)
    }

    @Test
    fun `given QUEUE_PROCESSOR when forType then Item and Done ports in order`() {
        val ports = NodePorts.forType(NodeType.QUEUE_PROCESSOR)

        assertEquals(listOf(OutboundPort.Item, OutboundPort.Done), ports.outbound)
    }

    @Test
    fun `given EVALUATION and zero retries when forType then Retry port is omitted`() {
        val ports = NodePorts.forType(NodeType.EVALUATION, maxRetries = 0)

        assertEquals(listOf(OutboundPort.Pass, OutboundPort.Fail), ports.outbound)
    }

    @Test
    fun `given EVALUATION and positive retries when forType then Retry port is between Pass and Fail`() {
        val ports = NodePorts.forType(NodeType.EVALUATION, maxRetries = 2)

        assertEquals(
            listOf(OutboundPort.Pass, OutboundPort.Retry, OutboundPort.Fail),
            ports.outbound,
        )
    }

    @Test
    fun `given INTENT_ROUTER when forType then one Custom port per non-blank class`() {
        val classes = listOf("simple", "  ", "complex", "")
        val ports = NodePorts.forType(NodeType.INTENT_ROUTER, intentClasses = classes)

        assertEquals(
            listOf(OutboundPort.Custom("simple"), OutboundPort.Custom("complex")),
            ports.outbound,
        )
    }

    @Test
    fun `given TOOL when forType then default ports (1 in, 1 unlabelled out)`() {
        val ports = NodePorts.forType(NodeType.TOOL)

        assertEquals(1, ports.inbound)
        assertEquals(listOf(OutboundPort.Default), ports.outbound)
    }
}
