package com.largebatata.nettoggle

internal data class RuleSwitchPresentation(val allowed: Boolean, val enabled: Boolean, val canClearRetained: Boolean)

internal fun presentSwitch(live: RuleState?, cached: RuleState?, connected: Boolean,
    foreground: Boolean, writing: Boolean): RuleSwitchPresentation {
    val allowed = (live ?: cached) != RuleState.BLOCKED
    val enabled = live != null && connected && foreground && !writing
    return RuleSwitchPresentation(allowed, enabled, enabled && live == RuleState.INACTIVE_DENY)
}

internal enum class ConnectionPresentation { AUTHORIZED, UNAUTHORIZED, STOPPED, DISCONNECTED, CONNECTING }
internal fun presentConnection(serverAlive: Boolean, permission: Boolean?, serviceAlive: Boolean, binding: Boolean) = when {
    !serverAlive -> ConnectionPresentation.STOPPED
    permission == false -> ConnectionPresentation.UNAUTHORIZED
    permission == null -> ConnectionPresentation.DISCONNECTED
    binding -> ConnectionPresentation.CONNECTING
    serviceAlive -> ConnectionPresentation.AUTHORIZED
    else -> ConnectionPresentation.DISCONNECTED
}

/** Stable IDs survive sorting / filtering; unlike String.hashCode, Aa/BB do not collide. */
internal fun stablePackageId(pkg: String): Long = pkg.fold(-3750763034362895579L) { hash, char ->
    (hash xor char.code.toLong()) * 1099511628211L
}

internal fun keepAbsoluteListTop(firstVisible: Int, topOffset: Int, hasDeferredAnchor: Boolean) =
    firstVisible == 0 && topOffset >= 0 && !hasDeferredAnchor

internal fun useStackedChrome(widthDp: Int, fontScale: Float) =
    widthDp < 360 || (widthDp < 480 && fontScale > 1.3f) || fontScale > 1.7f
