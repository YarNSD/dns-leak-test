package com.dnsleak.test.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.View

/**
 * Adaptive layout helper for Android TV (10-foot UI / overscan) and wide screens.
 * Leaves standard phones untouched.
 */
object TvUiUtils {

    fun isTvMode(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun applyTvLayout(activity: Activity, container: View, maxContentWidthDp: Int = 720) {
        val metrics = activity.resources.displayMetrics
        val screenWidthDp = metrics.widthPixels / metrics.density
        val isTv = isTvMode(activity)

        if (!isTv && screenWidthDp < 720f) return

        val overscanDp = if (isTv) 36f else 24f
        val targetWidthPx = (maxContentWidthDp * metrics.density).toInt()
        val overscanPx = (overscanDp * metrics.density).toInt()
        val sidePad = ((metrics.widthPixels - targetWidthPx) / 2).coerceAtLeast(overscanPx)
        val topBottomOverscan = if (isTv) (20f * metrics.density).toInt() else container.paddingTop

        container.setPadding(
            sidePad,
            topBottomOverscan,
            sidePad,
            container.paddingBottom
        )
    }
}
