package com.largebatata.nettoggle

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RuleListModelTest {
    private val apps = listOf(RuleApp("test.z", "Zulu"), RuleApp("test.a", "Alpha"),
        RuleApp("test.b", "Bravo"), RuleApp("test.c", "Charlie"))
    private fun model(cache: Map<String, CachedRule> = emptyMap()) = RuleListModel(apps, cache, Locale.US)
    private fun RuleListModel.packages(group: RuleGroup = RuleGroup.ALL, query: String = "") = visible(group, query).map { it.pkg }

    @Test fun cacheIsDisplayOnlyAndCannotAuthorizeToggle() {
        val model = model(mapOf("test.z" to CachedRule(RuleState.BLOCKED, 100)))
        assertEquals(RuleState.BLOCKED, model.cached("test.z")?.state)
        assertNull(model.state("test.z"))
        assertEquals(RuleCounts(4, 0, 0), model.counts())
        assertEquals(4, model.counts().unknown)
        assertTrue(model.packages(RuleGroup.BLOCKED).isEmpty())
        assertTrue(model.packages(RuleGroup.UNBLOCKED).isEmpty())
        assertEquals(listOf("test.a", "test.b", "test.c", "test.z"), model.packages())
    }

    @Test fun unknownOnlyAppearsInAll() {
        val model = model()
        assertEquals(4, model.packages().size)
        assertTrue(model.packages(RuleGroup.BLOCKED).isEmpty())
        assertTrue(model.packages(RuleGroup.UNBLOCKED).isEmpty())
    }

    @Test fun verifiedBlocksSortFirstThenByName() {
        val model = model()
        model.record("test.z", "BLOCKED", 100)
        model.record("test.b", "BLOCKED", 101)
        assertEquals(listOf("test.b", "test.z", "test.a", "test.c"), model.packages())
        assertEquals(listOf("test.b", "test.z"), model.packages(RuleGroup.BLOCKED))
        assertEquals(RuleCounts(4, 2, 0), model.counts())
    }

    @Test fun inactiveDenyIsVerifiedUnblockedAndRetainsRestoreState() {
        val model = model()
        model.record("test.z", "INACTIVE_DENY", 100)
        model.record("test.a", "ALLOWED", 100)
        assertEquals(listOf("test.a", "test.z"), model.packages(RuleGroup.UNBLOCKED))
        assertTrue(model.packages(RuleGroup.BLOCKED).isEmpty())
        assertEquals(RuleState.INACTIVE_DENY, model.state("test.z"))
        assertEquals(RuleCounts(4, 0, 2), model.counts())
    }

    @Test fun searchIsRestrictedToSelectedGroupAndDoesNotChangeCounts() {
        val model = model()
        model.record("test.a", "BLOCKED", 100)
        model.record("test.b", "ALLOWED", 100)
        assertTrue(model.packages(RuleGroup.BLOCKED, "Bravo").isEmpty())
        assertEquals(listOf("test.b"), model.packages(RuleGroup.UNBLOCKED, " BRAVO "))
        assertEquals(listOf("test.a"), model.packages(RuleGroup.BLOCKED, "TEST.A"))
        assertEquals(RuleCounts(4, 1, 1), model.counts())
    }

    @Test fun successfulReadMovesRowAndUpdatesCountsImmediately() {
        val model = model()
        model.record("test.z", "ALLOWED", 100)
        model.record("test.a", "ALLOWED", 100)
        model.record("test.z", "BLOCKED", 101)
        assertEquals(listOf("test.z", "test.a", "test.b", "test.c"), model.packages())
        assertEquals(listOf("test.a"), model.packages(RuleGroup.UNBLOCKED))
        assertEquals(listOf("test.z"), model.packages(RuleGroup.BLOCKED))
        assertEquals(RuleCounts(4, 1, 1), model.counts())
        model.record("test.z", "ALLOWED", 102)
        assertTrue(model.packages(RuleGroup.BLOCKED).isEmpty())
        assertEquals(listOf("test.a", "test.z"), model.packages(RuleGroup.UNBLOCKED))
    }

    @Test fun readFailureDoesNotOverwriteLastSuccessfulCacheOrCountAsAllowed() {
        val model = model()
        model.record("test.z", "BLOCKED", 100)
        assertFalse(model.record("test.z", "ERROR:permission denied", 101))
        assertNull(model.state("test.z"))
        assertEquals(CachedRule(RuleState.BLOCKED, 100), model.cached("test.z"))
        assertEquals("permission denied", model.error("test.z"))
        assertEquals(0, model.counts().verified)
        assertTrue(model.packages(RuleGroup.UNBLOCKED).isEmpty())
    }

    @Test fun invalidateRetainsCacheButRemovesAllVerifiedAndErrors() {
        val model = model()
        model.record("test.z", "BLOCKED", 100)
        model.record("test.a", "ERROR:timeout", 100)
        model.invalidate()
        assertNull(model.state("test.z"))
        assertNull(model.error("test.a"))
        assertEquals(CachedRule(RuleState.BLOCKED, 100), model.cached("test.z"))
        assertEquals(0, model.counts().verified)
    }

    @Test fun lastBlockedPackagesAreVerifiedFirstEvenIfNamedLast() {
        val model = model(mapOf("test.z" to CachedRule(RuleState.BLOCKED, 100),
            "test.c" to CachedRule(RuleState.INACTIVE_DENY, 100),
            "test.a" to CachedRule(RuleState.ALLOWED, 100)))
        assertEquals(listOf("test.z", "test.c", "test.a", "test.b"), model.verificationOrder())
    }

    @Test fun priorityWithinEachClassUsesName() {
        val model = model(mapOf("test.z" to CachedRule(RuleState.BLOCKED, 100),
            "test.b" to CachedRule(RuleState.BLOCKED, 100)))
        assertEquals(listOf("test.b", "test.z", "test.a", "test.c"), model.verificationOrder())
    }

    @Test fun cacheForUninstalledPackageIsPruned() {
        val model = model(mapOf("test.removed" to CachedRule(RuleState.BLOCKED, 100)))
        assertTrue(model.cacheSnapshot().isEmpty())
        assertFalse(model.record("test.removed", "BLOCKED", 101))
        assertEquals(RuleCounts(4, 0, 0), model.counts())
    }

    @Test fun sameNameUsesPackageForStableOrdering() {
        val model = RuleListModel(listOf(RuleApp("test.z", "same"), RuleApp("test.a", "Same")), emptyMap(), Locale.US)
        assertEquals(listOf("test.a", "test.z"), model.packages())
    }

    @Test fun successfulRecheckClearsErrorAndReplacesCache() {
        val model = model()
        model.record("test.z", "ERROR:timeout", 100)
        assertTrue(model.record("test.z", "ALLOWED", 101))
        assertNull(model.error("test.z"))
        assertEquals(CachedRule(RuleState.ALLOWED, 101), model.cacheSnapshot()["test.z"])
    }

    @Test fun cacheRoundTripIncludesRetainedDenyAndObservationTime() {
        val cache = mapOf("test.a" to CachedRule(RuleState.ALLOWED, 100),
            "test.b" to CachedRule(RuleState.BLOCKED, 101),
            "test.c" to CachedRule(RuleState.INACTIVE_DENY, 102))
        assertEquals(cache, RuleCacheCodec.decode(RuleCacheCodec.encode(cache)))
        assertEquals(0, model(RuleCacheCodec.decode(RuleCacheCodec.encode(cache))).counts().verified)
    }

    @Test fun malformedCacheEntriesAreIgnored() {
        val raw = mapOf("schema" to 1, "test.a" to "BLOCKED|0", "test.b" to "ALLOWED|bad",
            "test.c" to "UNKNOWN|100", "test.d" to "BLOCKED|100|extra",
            "test.e" to true, "bad;name" to "BLOCKED|100", "test.f" to "INACTIVE_DENY|123")
        assertEquals(mapOf("test.f" to CachedRule(RuleState.INACTIVE_DENY, 123)), RuleCacheCodec.decode(raw))
    }

    @Test fun emptyInventoryHasZeroCounts() {
        val model = RuleListModel(emptyList(), emptyMap())
        assertEquals(RuleCounts(0, 0, 0), model.counts())
        assertTrue(model.visible(RuleGroup.ALL, "").isEmpty())
        assertTrue(model.verificationOrder().isEmpty())
    }
}
