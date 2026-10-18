package com.largebatata.nettoggle

internal data class CommandResult(val ok: Boolean, val output: String)

/** Pure rule logic; the runner is the only privileged boundary. No local rule persistence. */
internal class FirewallController(private val run: (List<String>) -> CommandResult) {
    private val validPackage = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

    fun getState(pkg: String): String {
        if (!validPackage.matches(pkg)) return "ERROR:无效的包名"
        return try { snapshot(pkg).state } catch (e: Exception) { "ERROR:${e.message}" }
    }

    /** Share the chain reads within one bounded batch, never across independent requests. */
    fun getStates(packages: List<String>): List<String> {
        if (packages.isEmpty()) return emptyList()
        if (packages.size > 8) return packages.map { "ERROR:每批最多核对 8 个应用" }
        if (packages.none { validPackage.matches(it) }) return packages.map { "ERROR:无效的包名" }
        val chain = try { readChain() } catch (e: Exception) {
            return packages.map { "ERROR:${e.message}" }
        }
        val distinctResults = packages.distinct().associateWith { pkg ->
            if (!validPackage.matches(pkg)) "ERROR:无效的包名"
            else try { Snapshot(chain, readDenied(pkg)).state } catch (e: Exception) { "ERROR:${e.message}" }
        }
        return try {
            check(chain == readChain()) { "读取期间拦截链发生变化，请刷新" }
            packages.map { distinctResults.getValue(it) }
        } catch (e: Exception) {
            packages.map { "ERROR:${e.message}" }
        }
    }

    fun setBlocked(pkg: String, blocked: Boolean): String {
        if (!validPackage.matches(pkg)) return "ERROR:无效的包名"
        return try {
            val before = snapshot(pkg) // Do not write if the current rule cannot be read.
            checked(listOf("set-package-networking-enabled", (!blocked).toString(), pkg))
            if (blocked && !before.chainEnabled) {
                // A failed or timed-out write may already have taken effect. Never roll back
                // to ALLOW: the existing DENY may belong to another tool. UI must re-read.
                checked(listOf("set-chain3-enabled", "true"))
            }
            val after = snapshot(pkg)
            val expected = if (blocked) "BLOCKED" else "ALLOWED"
            if (after.state == expected) expected else "ERROR:状态校验失败：${after.state}"
        } catch (e: Exception) {
            "ERROR:操作未确认：${e.message}；请刷新读取系统规则，必要时点击恢复联网"
        }
    }

    private data class Snapshot(val chainEnabled: Boolean, val denied: Boolean) {
        val state get() = when {
            denied && chainEnabled -> "BLOCKED"
            denied -> "INACTIVE_DENY"
            else -> "ALLOWED"
        }
    }

    private fun snapshot(pkg: String): Snapshot {
        val chain = readChain()
        val denied = readDenied(pkg)
        check(chain == readChain()) { "读取期间拦截链发生变化，请刷新" }
        return Snapshot(chain, denied)
    }

    private fun readDenied(pkg: String): Boolean =
        when (val output = checked(listOf("get-package-networking-enabled", pkg))) {
            "$pkg:deny" -> true
            "$pkg:allow" -> false
            else -> error("无法识别应用规则：$output")
        }

    private fun readChain(): Boolean = when (val output = checked(listOf("get-chain3-enabled"))) {
        "chain:enabled" -> true
        "chain:disabled" -> false
        else -> error("无法识别拦截链状态：$output")
    }

    private fun checked(args: List<String>): String {
        val result = run(args)
        check(result.ok) { "${args.first()}：${result.output}" }
        return result.output.trim()
    }
}
