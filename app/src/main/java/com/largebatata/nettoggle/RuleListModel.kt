package com.largebatata.nettoggle

import java.text.Collator
import java.util.Locale

internal enum class RuleState {
    BLOCKED, ALLOWED, INACTIVE_DENY;

    companion object {
        fun parse(value: String?): RuleState? = entries.firstOrNull { it.name == value }
    }
}

internal enum class RuleGroup { ALL, BLOCKED, UNBLOCKED }
internal data class RuleApp(val pkg: String, val label: String)
internal data class CachedRule(val state: RuleState, val observedAt: Long)
internal data class RuleCounts(val all: Int, val blocked: Int, val unblocked: Int) {
    val verified get() = blocked + unblocked
    val unknown get() = all - verified
}

/** Main-thread model. Historical observations never become verified through cache loading. */
internal class RuleListModel(
    val apps: List<RuleApp>,
    cached: Map<String, CachedRule>,
    locale: Locale = Locale.getDefault()
) {
    private val installed = apps.map { it.pkg }.toSet()
    private val cache = cached.filterKeys { it in installed }.toMutableMap()
    private val verified = mutableMapOf<String, RuleState>()
    private val errors = mutableMapOf<String, String>()
    private val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    private val byName = Comparator<RuleApp> { a, b ->
        collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg)
    }

    fun state(pkg: String) = verified[pkg]
    fun cached(pkg: String) = cache[pkg]
    fun error(pkg: String) = errors[pkg]
    fun cacheSnapshot(): Map<String, CachedRule> = cache.toMap()

    fun invalidate() {
        verified.clear()
        errors.clear()
    }

    fun invalidate(packages: Collection<String>) {
        packages.forEach { verified.remove(it); errors.remove(it) }
    }

    fun unverified() = verificationOrder().filter { state(it) == null && error(it) == null }

    /** Enabling Chain 3 can activate every retained deny, but cannot change other allow UIDs. */
    fun affectedByWrite(pkg: String, blocked: Boolean, sameUid: Collection<String>): Set<String> =
        buildSet {
            add(pkg); addAll(sameUid)
            if (blocked) apps.forEach {
                val known = state(it.pkg) ?: cached(it.pkg)?.state
                if (known == RuleState.INACTIVE_DENY ||
                    (state(it.pkg) == null && known == RuleState.BLOCKED)) add(it.pkg)
            }
        }

    fun record(pkg: String, result: String, observedAt: Long): Boolean {
        if (pkg !in installed) return false
        val state = RuleState.parse(result)
        if (state == null) {
            verified.remove(pkg)
            errors[pkg] = result.removePrefix("ERROR:").take(180)
            return false
        }
        verified[pkg] = state
        cache[pkg] = CachedRule(state, observedAt)
        errors.remove(pkg)
        return true
    }

    fun counts() = RuleCounts(apps.size, verified.values.count { it == RuleState.BLOCKED },
        verified.values.count { it != RuleState.BLOCKED })

    fun visible(group: RuleGroup, query: String): List<RuleApp> {
        val needle = query.trim()
        return apps.filter { app ->
            val state = verified[app.pkg]
            val inGroup = when (group) {
                RuleGroup.ALL -> true
                RuleGroup.BLOCKED -> state == RuleState.BLOCKED
                RuleGroup.UNBLOCKED -> state == RuleState.ALLOWED || state == RuleState.INACTIVE_DENY
            }
            inGroup && (needle.isEmpty() || app.label.contains(needle, ignoreCase = true) ||
                app.pkg.contains(needle, ignoreCase = true))
        }.sortedWith(Comparator { a, b ->
            val aBlocked = verified[a.pkg] == RuleState.BLOCKED
            val bBlocked = verified[b.pkg] == RuleState.BLOCKED
            if (group == RuleGroup.ALL && aBlocked != bBlocked) {
                if (aBlocked) -1 else 1
            } else byName.compare(a, b)
        })
    }

    /** Last denied apps first, retained inactive denies next; each package is queried once. */
    fun verificationOrder(): List<String> = apps.sortedWith(Comparator { a, b ->
        fun priority(app: RuleApp) = when (cache[app.pkg]?.state) {
            RuleState.BLOCKED -> 0
            RuleState.INACTIVE_DENY -> 1
            else -> 2
        }
        (priority(a) - priority(b)).takeIf { it != 0 } ?: byName.compare(a, b)
    }).map { it.pkg }.distinct()
}

internal object RuleCacheCodec {
    private val validPackage = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

    fun decode(values: Map<String, *>): Map<String, CachedRule> = values.mapNotNull { (pkg, raw) ->
        if (!validPackage.matches(pkg) || raw !is String) return@mapNotNull null
        val parts = raw.split('|')
        if (parts.size != 2) return@mapNotNull null
        val state = RuleState.parse(parts[0]) ?: return@mapNotNull null
        val time = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        pkg to CachedRule(state, time)
    }.toMap()

    fun encode(values: Map<String, CachedRule>): Map<String, String> = values
        .filter { (pkg, rule) -> validPackage.matches(pkg) && rule.observedAt > 0 }
        .mapValues { (_, rule) -> "${rule.state.name}|${rule.observedAt}" }
}
