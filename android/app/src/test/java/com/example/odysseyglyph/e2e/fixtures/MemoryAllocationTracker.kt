package com.example.odysseyglyph.e2e.fixtures

import org.junit.Assert.assertTrue

/**
 * Memory Allocation Tracker.
 * Validates zero-allocation invariants across draw loops and high-frequency animation frames.
 */
object MemoryAllocationTracker {

    /**
     * Executes a warm-up phase, forces garbage collection to establish baseline,
     * runs the block across [iterations], and measures net heap allocation delta.
     */
    fun measureHeapAllocations(iterations: Int = 100, block: () -> Unit): Long {
        // Warm-up to trigger JIT compilation and class loading
        for (i in 0 until 10) {
            block()
        }

        // Suggest GC to stabilize memory baseline
        System.gc()
        Thread.sleep(10)
        System.gc()

        val runtime = Runtime.getRuntime()
        val beforeMem = runtime.totalMemory() - runtime.freeMemory()

        for (i in 0 until iterations) {
            block()
        }

        val afterMem = runtime.totalMemory() - runtime.freeMemory()
        return (afterMem - beforeMem).coerceAtLeast(0L)
    }

    /**
     * Asserts that repeating [block] across [iterations] does not produce significant heap growth.
     * [maxAllowedBytesPerIteration] defaults to 0 bytes for strict zero-allocation loops.
     */
    fun assertZeroHeapAllocations(
        iterations: Int = 100,
        maxAllowedBytesPerIteration: Double = 0.0,
        block: () -> Unit
    ) {
        var minBytesPerIter = Double.MAX_VALUE
        var lastAllocated = 0L
        for (attempt in 0 until 3) {
            val allocatedBytes = measureHeapAllocations(iterations, block)
            val bytesPerIter = allocatedBytes.toDouble() / iterations.toDouble()
            if (bytesPerIter <= maxAllowedBytesPerIteration) {
                return
            }
            if (bytesPerIter < minBytesPerIter) {
                minBytesPerIter = bytesPerIter
                lastAllocated = allocatedBytes
            }
            System.gc()
            Thread.sleep(20)
        }
        assertTrue(
            "Excessive heap allocation in draw loop: $minBytesPerIter bytes/iter (total: $lastAllocated bytes over $iterations iterations, max allowed: $maxAllowedBytesPerIteration)",
            minBytesPerIter <= maxAllowedBytesPerIteration
        )
    }
}
