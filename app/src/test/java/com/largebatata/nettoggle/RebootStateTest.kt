package com.largebatata.nettoggle

import org.junit.Assert.*
import org.junit.Test

/** Regression contract: old intent/observations never restore rules or authorization. */
class RebootStateTest {
    private val pkg = "test.reboot"
    private fun coldModel() = RuleListModel(listOf(RuleApp(pkg, "Test")),
        mapOf(pkg to CachedRule(RuleState.BLOCKED, 100)))

    private class SystemRules(var chain: Boolean, var deny: Boolean) {
        val calls = mutableListOf<List<String>>()
        val controller = FirewallController { args ->
            calls += args
            val value = when (args.first()) {
                "get-chain3-enabled" -> if (chain) "chain:enabled" else "chain:disabled"
                "get-package-networking-enabled" -> args[1] + if (deny) ":deny" else ":allow"
                "set-package-networking-enabled" -> { deny = args[1] == "false"; "" }
                "set-chain3-enabled" -> { chain = args[1].toBoolean(); "" }
                else -> error("Unexpected command")
            }
            CommandResult(true, value)
        }
    }
    @Test fun rebootHistoryCannotEnableSwitchOrPopulateVerifiedGroups() {
        val model = coldModel()
        assertEquals(0, model.counts().verified)
        assertEquals(1, model.visible(RuleGroup.ALL, "").size)
        assertTrue(model.visible(RuleGroup.BLOCKED, "").isEmpty())
        assertTrue(model.visible(RuleGroup.UNBLOCKED, "").isEmpty())
        assertFalse(presentSwitch(model.state(pkg), model.cached(pkg)?.state, false, true, false).enabled)
        assertEquals(ConnectionPresentation.STOPPED, presentConnection(false, true, false, false))
    }
    private fun readAfterReboot(chain: Boolean, deny: Boolean, expected: RuleState) {
        val model = coldModel()
        val system = SystemRules(chain, deny)
        assertTrue(model.record(pkg, system.controller.getState(pkg), 200))
        assertEquals(expected, model.state(pkg))
        assertEquals(0, model.counts().blocked)
        assertEquals(1, model.counts().unblocked)
        assertTrue(presentSwitch(model.state(pkg), model.cached(pkg)?.state, true, true, false).allowed)
        assertTrue(system.calls.none { it.first().startsWith("set-") })
        assertEquals(chain, system.chain)
        assertEquals(deny, system.deny)
    }
    @Test fun clearedRulesAndDisabledChainAreReadAsAllowedWithoutWrites() =
        readAfterReboot(false, false, RuleState.ALLOWED)

    @Test fun retainedDenyAndDisabledChainAreNotReportedAsActiveBlock() =
        readAfterReboot(false, true, RuleState.INACTIVE_DENY)

    @Test fun clearedRuleAndEnabledChainAreReadAsAllowedWithoutDisablingChain() =
        readAfterReboot(true, false, RuleState.ALLOWED)

    @Test fun explicitManualReblockWorksForBothObservedPossibleRebootOutcomes() {
        for (retained in listOf(false, true)) {
            val model = coldModel()
            val system = SystemRules(false, retained)
            model.record(pkg, system.controller.getState(pkg), 200)
            assertEquals(0, model.counts().blocked)
            assertTrue(model.record(pkg, system.controller.setBlocked(pkg, true), 300))
            assertEquals(RuleState.BLOCKED, model.state(pkg))
            assertTrue(system.chain); assertTrue(system.deny)
            assertTrue(system.calls.none { it == listOf("set-chain3-enabled", "false") })
        }
    }
}
