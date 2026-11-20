package com.example.odysseyglyph.e2e.tier4

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Master Test Suite for Tier 4: Real-World Workflows (6 multi-step scenarios).
 * Aggregates all end-to-end user journeys simulating complete application workloads.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    Tier4RealWorldScenariosTest::class
)
class Tier4Suite
