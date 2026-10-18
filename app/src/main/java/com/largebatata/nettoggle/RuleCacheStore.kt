package com.largebatata.nettoggle

import android.content.Context

/** Local display cache only. Preferences are excluded from backup by the existing manifest. */
internal class RuleCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences("rule_display_cache_v1", Context.MODE_PRIVATE)

    fun load(): Map<String, CachedRule> = runCatching {
        if (prefs.getInt("schema", 0) == 1) RuleCacheCodec.decode(prefs.all) else emptyMap()
    }.getOrDefault(emptyMap())

    fun save(values: Map<String, CachedRule>) {
        val edit = prefs.edit().clear().putInt("schema", 1)
        RuleCacheCodec.encode(values).forEach { (pkg, encoded) -> edit.putString(pkg, encoded) }
        edit.apply() // Android schedules disk persistence; no synchronous per-row disk commits.
    }
}
