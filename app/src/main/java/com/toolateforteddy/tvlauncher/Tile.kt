package com.toolateforteddy.tvlauncher

import android.content.Intent
import android.graphics.drawable.Drawable

sealed interface Row

data class Header(val title: String) : Row

data class Tile(
    /** Stable key used for favorites: the package name for apps, the input id for inputs. */
    val key: String,
    val label: String,
    /** A 16:9 banner when the app provides one; otherwise null and [icon] is shown. */
    val banner: Drawable?,
    val icon: Drawable?,
    val intent: Intent,
    val isApp: Boolean,
) : Row
