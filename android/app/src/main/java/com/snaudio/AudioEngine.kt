package com.snaudio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.view.KeyEvent
import android.os.Handler
import android.os.HandlerThread
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.io.File
import java.util.Locale

/**
 * One MediaPlayer for the plugin process. It is not tied to the React view,
 * so playback can continue after the plugin screen closes.
 */
object AudioEngine {
  const val AUDIO_DIR = "/storage/emulated/0/Document/Audio"

  private val thread = HandlerThread("sn-audio").apply { start() }
  private val handler = Handler(thread.looper)

  private var player: MediaPlayer? = null
  private var path: String? = null
  private var prepared = false
  private var looping = false
  private var reportedPosition: Int? = null
  private var focusRequest: AudioFocusRequest? = null
  private var onChange: (() -> Unit)? = null
  private var screenReceiver: BroadcastReceiver? = null
  private var buttonReceiver: BroadcastReceiver? = null
  private var session: MediaSession? = null
  private var appContext: Context? = null

  fun setOnChange(listener: (() -> Unit)?) {
    onChange = listener
  }

  fun watchScreen(context: Context) {
    if (screenReceiver != null) {
      return
    }
    val app = context.applicationContext
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
          post { pause() }
        }
      }
    }
    val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
    val noisy = object : BroadcastReceiver() {
      override fun onReceive(c: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
          post { pause() }
        }
      }
    }
    val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
      app.registerReceiver(noisy, noisyFilter, Context.RECEIVER_EXPORTED)
    } else {
      app.registerReceiver(receiver, filter)
      app.registerReceiver(noisy, noisyFilter)
    }
    screenReceiver = receiver
    appContext = app
    listenForHeadset(app)
    if (session == null) {
      val created = MediaSession(app, "SnAudio")
      created.setFlags(
        MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
          MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
      )
      created.setCallback(object : MediaSession.Callback() {
        override fun onPlay() {
          post { appContext?.let { resume(it) } }
        }

        override fun onPause() {
          post { pause() }
        }

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
          val event = mediaButton(mediaButtonIntent) ?: return false
          if (event.action == KeyEvent.ACTION_DOWN) {
            handleMediaKey(event.keyCode)
          }
          return true
        }
      })
      created.setPlaybackToLocal(mediaAttributes())
      session = created
    }
  }

  private fun listenForHeadset(app: Context) {
    if (buttonReceiver != null) {
      return
    }
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) {
          return
        }
        val event = mediaButton(intent) ?: return
        if (event.action != KeyEvent.ACTION_DOWN) {
          return
        }
        handleMediaKey(event.keyCode)
        if (isOrderedBroadcast) {
          abortBroadcast()
        }
      }
    }
    val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
    filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
      app.registerReceiver(receiver, filter)
    }
    buttonReceiver = receiver
  }

  private fun mediaButton(intent: Intent): KeyEvent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
    }
  }

  private fun handleMediaKey(keyCode: Int) {
    val context = appContext ?: return
    when (keyCode) {
      KeyEvent.KEYCODE_MEDIA_PLAY -> post { resume(context) }
      KeyEvent.KEYCODE_MEDIA_PAUSE,
      KeyEvent.KEYCODE_MEDIA_STOP -> post { pause() }
      KeyEvent.KEYCODE_HEADSETHOOK,
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> post { toggle(context) }
    }
  }

  private fun mediaAttributes(): AudioAttributes {
    return AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()
  }

  fun post(block: () -> Unit) {
    handler.post(block)
  }

  fun listDirectory(dirPath: String?): WritableMap {
    val base = File(AUDIO_DIR).canonicalFile
    val requested = if (dirPath.isNullOrBlank()) base else File(dirPath).canonicalFile
    val dir = if (requested.isDirectory && isInside(requested, base)) requested else base
    val map = Arguments.createMap()
    map.putString("path", dir.path)
    map.putBoolean("root", dir.path == base.path)
    val array = Arguments.createArray()
    val children = dir.listFiles() ?: emptyArray()
    children
      .mapNotNull { child -> entryFor(child, base) }
      .sortedWith(compareBy({ it.third != "dir" }, { it.first.lowercase(Locale.US) }))
      .forEach { (name, entryPath, kind) ->
        val item = Arguments.createMap()
        item.putString("name", name)
        item.putString("path", entryPath)
        item.putString("kind", kind)
        array.pushMap(item)
      }
    map.putArray("entries", array)
    return map
  }

  fun play(context: Context, filePath: String) {
    val file = allowedFile(filePath)
    val current = player
    if (prepared && current != null && path == file.absolutePath) {
      current.seekTo(0)
      current.start()
      reportedPosition = 0
      requestFocus(context)
      notifyChange()
      return
    }
    releasePlayer()
    val created = MediaPlayer()
    created.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build(),
    )
    created.setDataSource(file.absolutePath)
    created.isLooping = looping
    created.setOnCompletionListener {
      reportedPosition = safeDuration(created).coerceAtLeast(0)
      notifyChange()
    }
    created.setOnErrorListener { _, _, _ ->
      prepared = false
      notifyChange()
      true
    }
    created.prepare()
    created.start()
    player = created
    path = file.absolutePath
    prepared = true
    reportedPosition = 0
    requestFocus(context)
    notifyChange()
  }

  fun pause() {
    val current = player
    if (prepared && current != null && safePlaying(current)) {
      reportedPosition = safePosition(current)
      current.pause()
      notifyChange()
    }
  }

  fun resume(context: Context) {
    val current = player
    if (!prepared || current == null || safePlaying(current)) {
      return
    }
    val duration = safeDuration(current)
    val file = path
    if (duration > 0 && safePosition(current) >= duration - 500 && file != null) {
      play(context, file)
      return
    }
    reportedPosition = safePosition(current)
    requestFocus(context)
    current.start()
    notifyChange()
  }

  fun toggle(context: Context) {
    val current = player
    if (!prepared || current == null) {
      return
    }
    if (current.isPlaying) {
      pause()
    } else {
      resume(context)
    }
  }

  fun stop() {
    val current = player
    if (prepared && current != null) {
      if (current.isPlaying) {
        current.pause()
      }
      current.seekTo(0)
      reportedPosition = 0
      notifyChange()
    }
  }

  fun seekBy(deltaMs: Int) {
    val current = player ?: return
    if (!prepared) {
      return
    }
    val duration = safeDuration(current)
    val upper = if (duration > 0) duration else Int.MAX_VALUE
    val next = (safePosition(current) + deltaMs).coerceIn(0, upper)
    current.seekTo(next)
    reportedPosition = next
    notifyChange()
  }

  fun setLoop(enabled: Boolean) {
    looping = enabled
    player?.isLooping = enabled
    notifyChange()
  }

  fun status(live: Boolean = false): WritableMap {
    val map = Arguments.createMap()
    val currentPath = path
    if (currentPath == null) {
      map.putNull("path")
      map.putNull("name")
    } else {
      map.putString("path", currentPath)
      map.putString("name", File(currentPath).name)
    }
    val current = player
    val position = when {
      !live && reportedPosition != null -> reportedPosition!!
      prepared && current != null -> safePosition(current)
      else -> 0
    }
    val duration = if (prepared && current != null) safeDuration(current) else 0
    map.putDouble("positionMs", position.toDouble())
    map.putDouble("durationMs", duration.coerceAtLeast(0).toDouble())
    map.putBoolean("playing", prepared && safePlaying(current))
    map.putBoolean("loop", looping)
    return map
  }

  private fun notifyChange() {
    syncSession()
    onChange?.invoke()
  }

  private fun syncSession() {
    val current = session ?: return
    val playing = prepared && safePlaying(player)
    val position = if (prepared && player != null) safePosition(player!!).toLong() else 0L
    val state = when {
      !prepared -> PlaybackState.STATE_STOPPED
      playing -> PlaybackState.STATE_PLAYING
      else -> PlaybackState.STATE_PAUSED
    }
    val title = path?.let { File(it).name } ?: "Audio"
    current.setMetadata(
      MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .build(),
    )
    current.setPlaybackState(
      PlaybackState.Builder()
        .setActions(
          PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE,
        )
        .setState(state, position, if (playing) 1f else 0f)
        .build(),
    )
    current.isActive = prepared
  }

  private fun requestFocus(context: Context) {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(mediaAttributes())
      .build()
      .also { focusRequest = it }
    manager.requestAudioFocus(request)
  }

  private fun releasePlayer() {
    player?.run {
      try {
        reset()
        release()
      } catch (_: IllegalStateException) {
      }
    }
    player = null
    path = null
    prepared = false
  }

  private fun entryFor(child: File, base: File): Triple<String, String, String>? {
    if (child.name.isEmpty() || child.name.startsWith(".")) {
      return null
    }
    val canonical = try {
      child.canonicalFile
    } catch (_: Exception) {
      return null
    }
    if (!isInside(canonical, base)) {
      return null
    }
    val kind = when {
      canonical.isDirectory -> "dir"
      canonical.isFile && isAudioFile(child.name) -> "file"
      else -> return null
    }
    return Triple(child.name, canonical.path, kind)
  }

  private fun isInside(file: File, base: File): Boolean {
    return file.path == base.path || file.path.startsWith(base.path + File.separator)
  }

  private fun allowedFile(filePath: String): File {
    val base = File(AUDIO_DIR).canonicalFile
    val file = File(filePath).canonicalFile
    if (!isInside(file, base) || !file.isFile || !isAudioFile(file.name)) {
      throw IllegalArgumentException("Audio file is not in Document/Audio")
    }
    return file
  }

  private fun isAudioFile(name: String): Boolean {
    val lower = name.lowercase(Locale.US)
    return lower.endsWith(".mp3") || lower.endsWith(".wav")
  }

  private fun safePosition(mp: MediaPlayer): Int {
    return try {
      mp.currentPosition
    } catch (_: IllegalStateException) {
      0
    }
  }

  private fun safeDuration(mp: MediaPlayer): Int {
    return try {
      mp.duration
    } catch (_: IllegalStateException) {
      0
    }
  }

  private fun safePlaying(mp: MediaPlayer?): Boolean {
    if (mp == null) {
      return false
    }
    return try {
      mp.isPlaying
    } catch (_: IllegalStateException) {
      false
    }
  }
}
