package com.snaudio

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule

class AudioModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "AudioModule"

  override fun initialize() {
    super.initialize()
    AudioEngine.watchScreen(reactContext)
    AudioEngine.setOnChange {
      AudioEngine.post { emitStatus() }
    }
  }

  override fun invalidate() {
    AudioEngine.setOnChange(null)
    super.invalidate()
  }

  @ReactMethod
  fun listTracks(dir: String, promise: Promise) {
    AudioEngine.post {
      try {
        promise.resolve(AudioEngine.listDirectory(dir))
      } catch (error: Exception) {
        reject(promise, "LIST_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun play(path: String, promise: Promise) {
    AudioEngine.post {
      try {
        AudioEngine.play(reactContext.applicationContext, path)
        promise.resolve(AudioEngine.status())
      } catch (error: Exception) {
        reject(promise, "PLAY_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun pause(promise: Promise) {
    AudioEngine.post {
      try {
        AudioEngine.pause()
        promise.resolve(AudioEngine.status())
      } catch (error: Exception) {
        reject(promise, "PAUSE_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun resume(promise: Promise) {
    AudioEngine.post {
      try {
        AudioEngine.resume(reactContext.applicationContext)
        promise.resolve(AudioEngine.status())
      } catch (error: Exception) {
        reject(promise, "RESUME_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun toggle(promise: Promise) {
    AudioEngine.post {
      try {
        AudioEngine.toggle(reactContext.applicationContext)
        promise.resolve(AudioEngine.status())
      } catch (error: Exception) {
        reject(promise, "TOGGLE_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun stop(promise: Promise) {
    AudioEngine.post {
      try {
        AudioEngine.stop()
        promise.resolve(AudioEngine.status())
      } catch (error: Exception) {
        reject(promise, "STOP_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun seekBy(deltaMs: Double, promise: Promise) {
    AudioEngine.post {
      try {
        AudioEngine.seekBy(deltaMs.toInt())
        promise.resolve(AudioEngine.status())
      } catch (error: Exception) {
        reject(promise, "SEEK_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun setLoop(enabled: Boolean, promise: Promise) {
    AudioEngine.post {
      try {
        AudioEngine.setLoop(enabled)
        promise.resolve(AudioEngine.status())
      } catch (error: Exception) {
        reject(promise, "LOOP_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun getStatus(promise: Promise) {
    AudioEngine.post {
      try {
        promise.resolve(AudioEngine.status(live = true))
      } catch (error: Exception) {
        reject(promise, "STATUS_FAILED", error)
      }
    }
  }

  @ReactMethod
  fun addListener(eventName: String) {
  }

  @ReactMethod
  fun removeListeners(count: Double) {
  }

  private fun reject(promise: Promise, code: String, error: Exception) {
    val detail = error.message ?: ""
    if (detail.contains("READ permission") || detail.contains("1503")) {
      promise.reject(
        "PERMISSION",
        "Read permission is required to load audio files.",
        error,
      )
      return
    }
    promise.reject(code, "Could not load audio", error)
  }

  private fun emitStatus() {
    if (!reactContext.hasActiveReactInstance()) {
      return
    }
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit("AudioStatus", AudioEngine.status())
  }
}
