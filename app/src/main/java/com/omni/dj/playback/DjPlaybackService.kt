package com.omni.dj.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.omni.dj.DjApplication
import com.omni.dj.MainActivity

@UnstableApi
class DjPlaybackService : MediaSessionService() {
    lateinit var engine: MixEngine
        private set
    private var session: MediaSession? = null
    inner class LocalBinder : Binder() { val service get() = this@DjPlaybackService }
    private val localBinder = LocalBinder()
    override fun onCreate() {
        super.onCreate()
        engine = MixEngine(application as DjApplication) { player -> session?.setPlayer(transport(player)) }
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, transport(engine.sessionPlayer)).setSessionActivity(activity).build()
    }
    private fun transport(player: Player): Player = object : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
        override fun seekToNext() { engine.next() }
        override fun seekToNextMediaItem() { engine.next() }
        override fun seekToPrevious() { engine.previous() }
        override fun seekToPreviousMediaItem() { engine.previous() }
        override fun seekTo(positionMs: Long) { engine.seek(positionMs) }
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) { engine.seek(positionMs) }
        override fun play() { engine.play() }
        override fun pause() { engine.pause() }
    }
    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == LOCAL_BIND) localBinder else super.onBind(intent)
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!engine.state.value.playing) stopSelf()
    }
    override fun onDestroy() {
        session?.release(); engine.release(); super.onDestroy()
    }
    companion object { const val LOCAL_BIND = "com.omni.dj.LOCAL_BIND" }
}
