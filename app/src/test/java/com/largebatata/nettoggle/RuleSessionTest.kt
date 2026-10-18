package com.largebatata.nettoggle

import org.junit.Assert.*
import org.junit.Test

class RuleSessionTest {
    @Test fun coldProcessRequiresOneFullRefresh() {
        val session = RuleSession()
        session.connected("server", "service")
        assertTrue(session.takeFullRefresh())
        assertFalse(session.takeFullRefresh())
    }
    @Test fun briefBackgroundReturnAndSameBinderRebindReuseSnapshot() {
        val session = RuleSession()
        session.connected("server", "service"); session.takeFullRefresh()
        session.leave(1_000); session.enter(600_999)
        session.connected("server", "service")
        assertFalse(session.takeFullRefresh())
    }
    @Test fun tenMinutesInBackgroundExpiresOnceOnReturn() {
        val session = RuleSession()
        session.connected("server", "service"); session.takeFullRefresh()
        session.leave(1_000); session.enter(601_000)
        assertTrue(session.takeFullRefresh())
        assertFalse(session.takeFullRefresh())
    }
    @Test fun foregroundTimeDoesNotCreateRefreshPolling() {
        val session = RuleSession()
        session.connected("server", "service"); session.takeFullRefresh()
        session.enter(Long.MAX_VALUE)
        assertFalse(session.takeFullRefresh())
    }
    @Test fun reconnectOrManualRefreshInvalidatesOldWriteGeneration() {
        val session = RuleSession()
        session.connected("server", "old"); session.takeFullRefresh()
        val old = session.generation
        session.connected("server", "new")
        assertTrue(session.generation > old)
        assertTrue(session.takeFullRefresh())
        session.invalidate(); assertTrue(session.takeFullRefresh())
        session.connected("another server", "new"); assertTrue(session.takeFullRefresh())
        assertFalse(session.sameServer("server"))
    }
    private fun model() = RuleListModel(listOf(
        RuleApp("app.target", "A"), RuleApp("app.sibling", "B"),
        RuleApp("app.allowed", "C"), RuleApp("app.blocked", "D"), RuleApp("app.retained", "E")
    ), emptyMap()).apply {
        record("app.target", "ALLOWED", 1); record("app.sibling", "ALLOWED", 1)
        record("app.allowed", "ALLOWED", 1); record("app.blocked", "BLOCKED", 1)
        record("app.retained", "INACTIVE_DENY", 1)
    }
    @Test fun blockingOnlyRechecksSameUidAndRetainedDeny() {
        val model = model()
        val scope = model.affectedByWrite("app.target", true, listOf("app.sibling"))
        assertEquals(setOf("app.target", "app.sibling", "app.retained"), scope)
        model.invalidate(scope)
        assertEquals(RuleState.ALLOWED, model.state("app.allowed"))
        assertEquals(RuleState.BLOCKED, model.state("app.blocked"))
        model.record("app.target", "BLOCKED", 2) // setBlocked's own readback
        assertEquals(setOf("app.sibling", "app.retained"), model.unverified().toSet())
    }
    @Test fun allowingCannotChangeGlobalChainAndOnlyAffectsSameUid() {
        assertEquals(setOf("app.target", "app.sibling"),
            model().affectedByWrite("app.target", false, listOf("app.sibling")))
    }
    @Test fun failedReadsStayUnknownWithoutAutomaticRetryLoop() {
        val model = model()
        model.record("app.target", "ERROR:timeout", 2)
        assertNull(model.state("app.target"))
        assertEquals(4, model.counts().verified)
        assertTrue(model.unverified().isEmpty())
        model.invalidate(listOf("app.target")) // explicit retry after failed write
        assertEquals(listOf("app.target"), model.unverified())
    }
    @Test fun diskHistoryNeverRestoresLiveSessionOrCounts() {
        val model = model()
        val cold = RuleListModel(model.apps, model.cacheSnapshot())
        assertEquals(0, cold.counts().verified)
        assertEquals(5, cold.unverified().size)
        assertTrue(cold.visible(RuleGroup.BLOCKED, "").isEmpty())
    }
    @Test fun groupSearchAndRepeatedRenderingDoNotInvalidateSnapshot() {
        val model = model()
        repeat(100) {
            RuleGroup.entries.forEach { model.visible(it, "app") }
            model.counts()
        }
        assertEquals(5, model.counts().verified)
        assertTrue(model.unverified().isEmpty())
    }
}
