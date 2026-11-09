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
  fun listTracks(promise: Promise) {
    AudioEngine.post {
      try {
        promise.resolve(AudioEngine.listTracks())
      } catch (error: Exception) {
        promise.reject("LIST_FAILED", error.message, error)
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
        promise.reject("PLAY_FAILED", error.message, error)
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
        promise.reject("PAUSE_FAILED", error.message, error)
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
        promise.reject("RESUME_FAILED", error.message, error)
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
        promise.reject("TOGGLE_FAILED", error.message, error)
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
        promise.reject("STOP_FAILED", error.message, error)
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
        promise.reject("SEEK_FAILED", error.message, error)
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
        promise.reject("LOOP_FAILED", error.message, error)
      }
    }
  }

  @ReactMethod
  fun getStatus(promise: Promise) {
    AudioEngine.post {
      try {
        promise.resolve(AudioEngine.status(live = true))
      } catch (error: Exception) {
        promise.reject("STATUS_FAILED", error.message, error)
      }
    }
  }

  @ReactMethod
  fun addListener(eventName: String) {
  }

  @ReactMethod
  fun removeListeners(count: Double) {
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
