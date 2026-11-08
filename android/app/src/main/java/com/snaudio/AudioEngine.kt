package com.snaudio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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

  fun setOnChange(listener: (() -> Unit)?) {
    onChange = listener
  }

  fun post(block: () -> Unit) {
    handler.post(block)
  }

  fun listTracks(): WritableArray {
    val array = Arguments.createArray()
    val dir = File(AUDIO_DIR)
    if (!dir.isDirectory) {
      return array
    }
    val files = dir.listFiles() ?: return array
    files
      .filter { it.isFile && isAudioFile(it.name) }
      .sortedBy { it.name.lowercase(Locale.US) }
      .forEach { file ->
        val item = Arguments.createMap()
        item.putString("name", file.name)
        item.putString("path", file.absolutePath)
        array.pushMap(item)
      }
    return array
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
      if (reportedPosition == null) {
        reportedPosition = safePosition(current)
      }
      current.pause()
      notifyChange()
    }
  }

  fun resume(context: Context) {
    val current = player
    if (prepared && current != null && !current.isPlaying) {
      requestFocus(context)
      current.start()
      notifyChange()
    }
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
    onChange?.invoke()
  }

  private fun requestFocus(context: Context) {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build(),
      )
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

  private fun allowedFile(filePath: String): File {
    val base = File(AUDIO_DIR).canonicalFile
    val file = File(filePath).canonicalFile
    val inside = file.path == base.path || file.path.startsWith(base.path + File.separator)
    if (!inside || !file.isFile || !isAudioFile(file.name)) {
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
