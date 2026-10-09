package com.omni.dj

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import com.omni.dj.playback.DjPlaybackService
import com.omni.dj.ui.DjApp
import com.omni.dj.ui.DjViewModel

@UnstableApi
class MainActivity : ComponentActivity() {
    private val model: DjViewModel by viewModels()
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            model.attach((service as? DjPlaybackService.LocalBinder)?.service?.engine)
        }
        override fun onServiceDisconnected(name: ComponentName?) { model.attach(null) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(16, 16, 23)),
        )
        bound = bindService(Intent(this, DjPlaybackService::class.java).setAction(DjPlaybackService.LOCAL_BIND), connection, Context.BIND_AUTO_CREATE)
        lifecycle.addObserver(LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() })
        setContent { DjApp(model) }
    }
    override fun onDestroy() {
        if (bound) unbindService(connection)
        model.attach(null)
        super.onDestroy()
    }
}
