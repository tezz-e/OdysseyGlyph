package com.example.odysseyglyph.e2e

import com.example.odysseyglyph.e2e.tier1.Tier1Suite
import com.example.odysseyglyph.e2e.tier2.Tier2Suite
import com.example.odysseyglyph.e2e.tier3.Tier3Suite
import com.example.odysseyglyph.e2e.tier4.Tier4Suite
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Master Opaque-Box E2E Test Suite for Glyph Odyssey UI/UX Overhaul.
 *
 * Groups all 4 tiers of verification across Features 1 through 37:
 * - Tier 1: Feature Coverage Suite (185 tests)
 * - Tier 2: Boundary & Corner Cases Suite (185 tests)
 * - Tier 3: Cross-Feature Combinations Suite (20 tests)
 * - Tier 4: Real-World Application Workflows Suite (6 scenarios)
 *
 * Grand Total: 396 test cases.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    Tier1Suite::class,
    Tier2Suite::class,
    Tier3Suite::class,
    Tier4Suite::class
)
class GlyphOdysseyE2ETestSuite
