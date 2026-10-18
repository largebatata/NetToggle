package com.largebatata.nettoggle

import org.junit.Assert.*
import org.junit.Test

class FirewallControllerTest {
    private val pkg = "example.test.app"

    private class Fake(var chain: Boolean = true, var deny: Boolean = false) {
        val calls = mutableListOf<List<String>>()
        var reject: String? = null
        var malformed = false
        var ignoreWrites = false
        var changeChainDuringRead = false
        fun run(args: List<String>): CommandResult {
            calls += args
            if (args.first() == reject) return CommandResult(false, "SecurityException / timeout")
            val result = when (args.first()) {
                "get-chain3-enabled" -> "chain:" + if (chain) "enabled" else "disabled"
                "get-package-networking-enabled" -> {
                    if (changeChainDuringRead) chain = !chain
                    if (malformed) "help: ${args[1]}:allow" else args[1] + if (deny) ":deny" else ":allow"
                }
                "set-package-networking-enabled" -> { if (!ignoreWrites) deny = args[1] == "false"; "ok" }
                "set-chain3-enabled" -> { if (!ignoreWrites) chain = args[1] == "true"; "ok" }
                else -> error("Unexpected command: $args")
            }
            return CommandResult(true, result)
        }
        fun controller() = FirewallController(::run)
        fun writes() = calls.filter { it.first().startsWith("set-") }
    }

    @Test fun enabledDenyIsBlocked() { assertEquals("BLOCKED", Fake(deny = true).controller().getState(pkg)) }
    @Test fun enabledAllowIsAllowed() { assertEquals("ALLOWED", Fake().controller().getState(pkg)) }
    @Test fun disabledAllowIsAllowed() { assertEquals("ALLOWED", Fake(chain = false).controller().getState(pkg)) }
    @Test fun disabledDenyIsRetained() { assertEquals("INACTIVE_DENY", Fake(false, true).controller().getState(pkg)) }
    @Test fun disabledChainStillQueriesPackage() {
        val fake = Fake(chain = false).apply { reject = "get-package-networking-enabled" }
        assertTrue(fake.controller().getState(pkg).startsWith("ERROR:"))
    }
    @Test fun malformedSuccessOutputIsNotAllowed() {
        assertTrue(Fake().apply { malformed = true }.controller().getState(pkg).startsWith("ERROR:"))
    }
    @Test fun chainChangingMidReadIsUnknown() {
        assertTrue(Fake().apply { changeChainDuringRead = true }.controller().getState(pkg).startsWith("ERROR:"))
    }
    @Test fun invalidPackageNeverExecutes() {
        val fake = Fake()
        assertTrue(fake.controller().setBlocked("example.app;id", true).startsWith("ERROR:"))
        assertTrue(fake.controller().getState("example.app\n").startsWith("ERROR:"))
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun unreadableStateNeverWrites() {
        val fake = Fake().apply { reject = "get-chain3-enabled" }
        assertTrue(fake.controller().setBlocked(pkg, true).startsWith("ERROR:"))
        assertTrue(fake.writes().isEmpty())
    }
    @Test fun denyRegisteredBeforeChainEnabled() {
        val fake = Fake(chain = false)
        assertEquals("BLOCKED", fake.controller().setBlocked(pkg, true))
        assertEquals(listOf(listOf("set-package-networking-enabled", "false", pkg),
            listOf("set-chain3-enabled", "true")), fake.writes())
    }
    @Test fun enablingActiveChainIsUnnecessary() {
        val fake = Fake()
        assertEquals("BLOCKED", fake.controller().setBlocked(pkg, true))
        assertEquals(1, fake.writes().size)
    }
    @Test fun failedDenyNeverEnablesChain() {
        val fake = Fake(chain = false).apply { reject = "set-package-networking-enabled" }
        assertTrue(fake.controller().setBlocked(pkg, true).startsWith("ERROR:"))
        assertFalse(fake.calls.any { it.first() == "set-chain3-enabled" })
    }
    @Test fun failedEnableNeverClearsPreexistingDeny() {
        val fake = Fake(false, true).apply { reject = "set-chain3-enabled" }
        assertTrue(fake.controller().setBlocked(pkg, true).startsWith("ERROR:"))
        assertTrue(fake.deny)
        assertFalse(fake.writes().any { it == listOf("set-package-networking-enabled", "true", pkg) })
    }
    @Test fun restoreNeverDisablesGlobalChain() {
        val fake = Fake(deny = true)
        assertEquals("ALLOWED", fake.controller().setBlocked(pkg, false))
        assertTrue(fake.chain)
        assertFalse(fake.calls.any { it.first() == "set-chain3-enabled" })
    }
    @Test fun restoreRemovesInactiveDeny() {
        val fake = Fake(false, true)
        assertEquals("ALLOWED", fake.controller().setBlocked(pkg, false))
        assertFalse(fake.deny)
        assertFalse(fake.chain)
    }
    @Test fun successExitWithoutRuleChangeFailsReadback() {
        val fake = Fake().apply { ignoreWrites = true }
        assertTrue(fake.controller().setBlocked(pkg, true).startsWith("ERROR:"))
    }
    @Test fun postWriteReadFailureIsNotSuccess() {
        val fake = Fake()
        val controller = FirewallController { args ->
            val result = fake.run(args)
            if (args.first() == "set-package-networking-enabled") fake.reject = "get-chain3-enabled"
            result
        }
        assertTrue(controller.setBlocked(pkg, true).startsWith("ERROR:"))
    }

    @Test fun batchSharesChainQueriesWithoutCachingAcrossRequests() {
        val fake = Fake()
        val packages = (1..8).map { "test.app$it" }
        assertEquals(List(8) { "ALLOWED" }, fake.controller().getStates(packages))
        assertEquals(10, fake.calls.size)
        assertEquals(2, fake.calls.count { it.first() == "get-chain3-enabled" })
        assertEquals(8, fake.calls.count { it.first() == "get-package-networking-enabled" })
        fake.chain = false
        fake.deny = true
        assertEquals(listOf("INACTIVE_DENY"), fake.controller().getStates(listOf(pkg)))
        assertTrue(fake.writes().isEmpty())
    }

    @Test fun duplicatePackagesInBatchAreQueriedOnce() {
        val fake = Fake(deny = true)
        assertEquals(listOf("BLOCKED", "BLOCKED"), fake.controller().getStates(listOf(pkg, pkg)))
        assertEquals(3, fake.calls.size)
    }

    @Test fun emptyAndOversizedBatchNeverRunCommands() {
        val fake = Fake()
        assertTrue(fake.controller().getStates(emptyList()).isEmpty())
        assertTrue(fake.controller().getStates(List(9) { pkg }).all { it.startsWith("ERROR:") })
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun batchChainFailureNeverClaimsAnyPackageAllowed() {
        val fake = Fake().apply { reject = "get-chain3-enabled" }
        assertTrue(fake.controller().getStates(listOf(pkg, "test.other")).all { it.startsWith("ERROR:") })
        assertEquals(1, fake.calls.size)
    }

    @Test fun batchChainChangingInvalidatesAllResults() {
        val fake = Fake().apply { changeChainDuringRead = true }
        assertTrue(fake.controller().getStates(listOf(pkg)).single().startsWith("ERROR:"))
    }

    @Test fun batchFinalChainFailureInvalidatesEarlierPackageResults() {
        val fake = Fake()
        var chainReads = 0
        val controller = FirewallController { args ->
            if (args.first() == "get-chain3-enabled" && ++chainReads == 2) CommandResult(false, "timeout")
            else fake.run(args)
        }
        assertTrue(controller.getStates(listOf(pkg, "test.other")).all { it.startsWith("ERROR:") })
    }

    @Test fun perPackageBatchFailureDoesNotHideOtherSuccessfulRead() {
        val fake = Fake()
        val controller = FirewallController { args ->
            if (args == listOf("get-package-networking-enabled", pkg)) CommandResult(false, "package removed")
            else fake.run(args)
        }
        val results = controller.getStates(listOf(pkg, "test.other"))
        assertTrue(results[0].startsWith("ERROR:"))
        assertEquals("ALLOWED", results[1])
    }

    @Test fun malformedPackageInBatchIsNotExecuted() {
        val fake = Fake()
        val results = fake.controller().getStates(listOf("test.app;id", pkg))
        assertTrue(results[0].startsWith("ERROR:"))
        assertEquals("ALLOWED", results[1])
        assertFalse(fake.calls.any { it.contains("test.app;id") })
    }
}
