package com.example.odysseyglyph.e2e.tier1

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Master Test Suite for Tier 1: Feature Coverage (185 test cases).
 * Aggregates all happy-path tests across Features 1 through 37.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    Tier1FoundationCoverageTest::class,      // Features 1–13 (65 tests)
    Tier1BentoConsoleCoverageTest::class,     // Features 14–24 (55 tests)
    Tier1TacticalCropperCoverageTest::class,  // Features 25–30 (30 tests)
    Tier1DynamicPodCoverageTest::class,       // Features 31–34 (20 tests)
    Tier1PlayReadinessCoverageTest::class     // Features 35–37 (15 tests)
)
class Tier1Suite
