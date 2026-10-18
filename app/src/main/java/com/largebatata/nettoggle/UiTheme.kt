package com.largebatata.nettoggle

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

/** Reads platform-backed resource roles; no palette generator, library, or polling. */
internal class UiTheme(private val context: Context) {
    val background = context.getColor(R.color.nt_background)
    val surface = context.getColor(R.color.nt_surface)
    val surfaceHigh = context.getColor(R.color.nt_surface_high)
    val text = context.getColor(R.color.nt_text)
    val secondary = context.getColor(R.color.nt_secondary)
    val primary = context.getColor(R.color.nt_primary)
    val primaryContainer = context.getColor(R.color.nt_primary_container)
    val onPrimaryContainer = context.getColor(R.color.nt_on_primary_container)
    val outline = context.getColor(R.color.nt_outline)
    val blockedBackground = context.getColor(R.color.nt_blocked_background)
    val blockedText = context.getColor(R.color.nt_blocked_text)
    val connectedBackground = context.getColor(R.color.nt_connected_background)
    val connectedText = context.getColor(R.color.nt_connected_text)
    val warningBackground = context.getColor(R.color.nt_warning_background)
    val warningText = context.getColor(R.color.nt_warning_text)

    fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * context.resources.displayMetrics.density
    }

    fun ripple(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(withAlpha(primary, 0.12f)), shape(color, radius), shape(Color.WHITE, radius))

    fun switchThumb() = ColorStateList(arrayOf(
        intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()
    ), intArrayOf(withAlpha(secondary, 0.38f), primary, secondary))

    fun switchTrack() = ColorStateList(arrayOf(
        intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()
    ), intArrayOf(withAlpha(outline, 0.5f), primaryContainer, outline))

    private fun withAlpha(color: Int, alpha: Float) = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
}
