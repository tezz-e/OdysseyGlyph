package com.example.odysseyglyph.e2e.tier3

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Master Test Suite for Tier 3: Cross-Feature Combinations (20 test cases).
 * Aggregates all pairwise interaction tests across subsystems.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    Tier3CrossFeatureCombinationsTest::class
)
class Tier3Suite
