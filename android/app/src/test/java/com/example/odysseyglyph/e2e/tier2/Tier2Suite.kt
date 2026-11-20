package com.example.odysseyglyph.e2e.tier2

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Master Test Suite for Tier 2: Boundary & Corner Cases (185 test cases).
 * Aggregates all boundary tests across Features 1 through 37.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    Tier2FoundationBoundaryTest::class,      // Features 1–13 (65 tests)
    Tier2BentoConsoleBoundaryTest::class,     // Features 14–24 (55 tests)
    Tier2TacticalCropperBoundaryTest::class,  // Features 25–30 (30 tests)
    Tier2DynamicPodBoundaryTest::class,       // Features 31–34 (20 tests)
    Tier2PlayReadinessBoundaryTest::class     // Features 35–37 (15 tests)
)
class Tier2Suite
