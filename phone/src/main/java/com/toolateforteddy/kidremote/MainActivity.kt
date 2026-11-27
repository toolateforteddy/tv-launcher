package com.toolateforteddy.kidremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toolateforteddy.kidremote.ui.GrownUpScreen
import com.toolateforteddy.kidremote.ui.KidScreen
import com.toolateforteddy.kidremote.ui.KidRemoteTheme

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            KidRemoteTheme {
                val shows by vm.shows.collectAsStateWithLifecycle()
                val status by vm.tv.status.collectAsStateWithLifecycle()
                val notice by vm.notice.collectAsStateWithLifecycle()
                // First run: nothing to show a child yet, so start on setup.
                var grownUp by rememberSaveable { mutableStateOf(vm.store.tvHost == null && shows.isEmpty()) }

                if (grownUp) {
                    BackHandler { grownUp = false }
                    GrownUpScreen(vm, status, shows, notice, onDone = { grownUp = false })
                } else {
                    // Swallow Back so a stray swipe doesn't drop a toddler out of the remote.
                    BackHandler { }
                    KidScreen(
                        shows = shows,
                        status = status,
                        notice = notice,
                        onShow = vm::openShow,
                        onPlayPause = vm::playPause,
                        onOpenGrownUp = { grownUp = true },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.tv.start()
    }

    override fun onStop() {
        vm.tv.stop()
        vm.finder.stop()
        super.onStop()
    }
}
