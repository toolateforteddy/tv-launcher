package com.toolateforteddy.kidremote.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.toolateforteddy.kidremote.Show
import com.toolateforteddy.kidremote.TvStatus
import org.junit.Rule
import org.junit.Test

/** Renders the kid screen on the JVM so it can be looked at without a phone. */
class KidScreenShots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val shows = listOf("Tumble Leaf", "Masha and the Bear", "Bluey", "Sesame Street")
        .mapIndexed { i, name -> Show("$i", name, "https://example.com/$i", "") }

    @Test fun kid() = paparazzi.snapshot {
        KidRemoteTheme {
            KidScreen(shows, TvStatus.Ready(true), null, {}, {}, {})
        }
    }

    @Test fun kidEmpty() = paparazzi.snapshot {
        KidRemoteTheme {
            KidScreen(emptyList(), TvStatus.NoTv, null, {}, {}, {})
        }
    }
}
