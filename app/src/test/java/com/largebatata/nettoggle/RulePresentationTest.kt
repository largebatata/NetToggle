package com.largebatata.nettoggle

import org.junit.Assert.*
import org.junit.Test

class RulePresentationTest {
    @Test fun allowedMeansSwitchOnAndBlockedMeansOff() {
        assertEquals(RuleSwitchPresentation(true, true, false), presentSwitch(RuleState.ALLOWED, null, true, true, false))
        assertEquals(RuleSwitchPresentation(false, true, false), presentSwitch(RuleState.BLOCKED, null, true, true, false))
    }
    @Test fun retainedDenyMeansCurrentlyOnWithSeparateClearAction() {
        assertEquals(RuleSwitchPresentation(true, true, true), presentSwitch(RuleState.INACTIVE_DENY, null, true, true, false))
    }
    @Test fun cacheNeverEnablesSwitchOrClearAction() {
        for (cached in RuleState.entries) {
            val result = presentSwitch(null, cached, true, true, false)
            assertFalse(result.enabled)
            assertFalse(result.canClearRetained)
        }
    }
    @Test fun liveResultOverridesContradictoryHistory() {
        assertTrue(presentSwitch(RuleState.ALLOWED, RuleState.BLOCKED, true, true, false).allowed)
        assertFalse(presentSwitch(RuleState.BLOCKED, RuleState.ALLOWED, true, true, false).allowed)
    }
    @Test fun noConnectionBackgroundOrWriteDisablesInteraction() {
        assertFalse(presentSwitch(RuleState.ALLOWED, null, false, true, false).enabled)
        assertFalse(presentSwitch(RuleState.ALLOWED, null, true, false, false).enabled)
        assertFalse(presentSwitch(RuleState.INACTIVE_DENY, null, true, true, true).canClearRetained)
    }
    @Test fun failureReturnsToUnverifiedHistoricalPresentation() {
        val model = RuleListModel(listOf(RuleApp("test.app", "Test")), emptyMap())
        model.record("test.app", "ALLOWED", 100)
        model.invalidate()
        model.record("test.app", "ERROR:timeout", 101)
        val presentation = presentSwitch(model.state("test.app"), model.cached("test.app")?.state, true, true, false)
        assertTrue(presentation.allowed)
        assertFalse(presentation.enabled)
        assertEquals(0, model.counts().verified)
    }
    @Test fun authorizationRequiresServerPermissionAndLiveUserService() {
        assertEquals(ConnectionPresentation.STOPPED, presentConnection(false, true, true, false))
        assertEquals(ConnectionPresentation.UNAUTHORIZED, presentConnection(true, false, true, false))
        assertEquals(ConnectionPresentation.DISCONNECTED, presentConnection(true, true, false, false))
        assertEquals(ConnectionPresentation.DISCONNECTED, presentConnection(true, null, true, false))
        assertEquals(ConnectionPresentation.CONNECTING, presentConnection(true, true, false, true))
        assertEquals(ConnectionPresentation.AUTHORIZED, presentConnection(true, true, true, false))
    }
    @Test fun stableIdsSurviveSortingAndAvoidKnownStringHashCollision() {
        assertEquals("test.Aa".hashCode(), "test.BB".hashCode())
        assertNotEquals(stablePackageId("test.Aa"), stablePackageId("test.BB"))
        assertEquals(stablePackageId("test.app"), stablePackageId("test.app"))
    }
    @Test fun startupAtTopShowsNewlyVerifiedBlockedAppsAtTheActualTop() {
        assertTrue(keepAbsoluteListTop(0, 0, false))
    }
    @Test fun scrolledOrRestoredPositionKeepsItsAnchor() {
        assertFalse(keepAbsoluteListTop(3, 0, false))
        assertFalse(keepAbsoluteListTop(0, -20, false))
        assertFalse(keepAbsoluteListTop(0, 0, true))
    }
    @Test fun narrowScreensAndLargeFontsUseTwoLineCounts() {
        assertTrue(useStackedChrome(320, 1f))
        assertTrue(useStackedChrome(411, 1.5f))
        assertFalse(useStackedChrome(411, 1f))
        assertFalse(useStackedChrome(800, 1.3f))
    }
}
