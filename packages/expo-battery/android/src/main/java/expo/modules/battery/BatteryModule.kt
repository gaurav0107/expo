package expo.modules.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.common.LifecycleState
import io.github.expo.modules.v2.Constant
import io.github.expo.modules.v2.Enumerable
import io.github.expo.modules.v2.Event
import io.github.expo.modules.v2.ExpoModule
import io.github.expo.modules.v2.JS
import io.github.expo.modules.v2.Module
import io.github.expo.modules.v2.Record
import io.github.expo.modules.v2.react.androidContext
import io.github.expo.modules.v2.react.reactContextOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.expo.modules.v2.events.Event as ModuleEvent

internal const val BATTERY_LEVEL_EVENT_NAME = "Expo.batteryLevelDidChange"
internal const val BATTERY_CHARGED_EVENT_NAME = "Expo.batteryStateDidChange"
internal const val POWER_MODE_EVENT_NAME = "Expo.powerModeDidChange"

@Record
internal data class BatteryLevelEvent(val batteryLevel: Float)

@Record
internal data class BatteryStateEvent(val batteryState: BatteryModule.BatteryState)

@Record
internal data class PowerModeEvent(val lowPowerMode: Boolean)

@ExpoModule("ExpoBattery")
class BatteryModule : Module() {
  enum class BatteryState(val value: Int) : Enumerable {
    UNKNOWN(0),
    UNPLUGGED(1),
    CHARGING(2),
    FULL(3),
    NOT_CHARGING(4)
  }

  @JS
  @Constant
  val isSupported: Boolean
    get() = true

  @Event(name = BATTERY_LEVEL_EVENT_NAME)
  private val onBatteryLevelChange = event<BatteryLevelEvent>(
    onStartObserving = ::updateBroadcastReceivers,
    onStopObserving = ::updateBroadcastReceivers
  )

  @Event(name = BATTERY_CHARGED_EVENT_NAME)
  private val onBatteryStateChange = event<BatteryStateEvent>(
    onStartObserving = ::updateBroadcastReceivers,
    onStopObserving = ::updateBroadcastReceivers
  )

  @Event(name = POWER_MODE_EVENT_NAME)
  private val onPowerModeChange = event<PowerModeEvent>(
    onStartObserving = ::updateBroadcastReceivers,
    onStopObserving = ::updateBroadcastReceivers
  )

  private val broadcastReceivers = listOf(
    EventBroadcastReceiver(
      onBatteryLevelChange,
      BatteryLevelReceiver { onBatteryLevelChange(BatteryLevelEvent(it)) },
      IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_LOW)
        addAction(Intent.ACTION_BATTERY_OKAY)
      }
    ),
    EventBroadcastReceiver(
      onBatteryStateChange,
      BatteryStateReceiver { onBatteryStateChange(BatteryStateEvent(it)) },
      IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    ),
    EventBroadcastReceiver(
      onPowerModeChange,
      PowerSaverReceiver { onPowerModeChange(PowerModeEvent(it)) },
      IntentFilter("android.os.action.POWER_SAVE_MODE_CHANGED")
    )
  )

  private var isLifecycleListenerAdded = false

  private val lifecycleListener = object : LifecycleEventListener {
    override fun onHostResume() = updateBroadcastReceivers()

    override fun onHostPause() = updateBroadcastReceivers()

    override fun onHostDestroy() = Unit
  }

  override fun onDestroy() {
    val reactContext = reactContextOrNull ?: return
    reactContext.removeLifecycleEventListener(lifecycleListener)
    synchronized(broadcastReceivers) {
      isLifecycleListenerAdded = false
      broadcastReceivers.forEach { it.update(reactContext, shouldRegister = false) }
    }
  }

  @JS
  suspend fun getBatteryLevelAsync(): Float = withContext(Dispatchers.IO) {
    val batteryIntent = androidContext.applicationContext.registerReceiver(
      null,
      IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    ) ?: return@withContext -1f

    val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level != -1 && scale != -1) {
      level / scale.toFloat()
    } else {
      -1f
    }
  }

  @JS
  suspend fun getBatteryStateAsync(): BatteryState = withContext(Dispatchers.IO) {
    val batteryIntent = androidContext.applicationContext.registerReceiver(
      null,
      IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    ) ?: return@withContext BatteryState.UNKNOWN

    val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    batteryStatusNativeToJS(status, plugged)
  }

  @JS
  suspend fun isLowPowerModeEnabledAsync(): Boolean = withContext(Dispatchers.IO) {
    isLowPowerModeEnabled
  }

  @JS
  suspend fun isBatteryOptimizationEnabledAsync(): Boolean = withContext(Dispatchers.IO) {
    val packageName = androidContext.applicationContext.packageName
    val powerManager = androidContext.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    powerManager?.isIgnoringBatteryOptimizations(packageName) == false
  }

  private fun updateBroadcastReceivers() {
    val reactContext = reactContextOrNull ?: return
    synchronized(broadcastReceivers) {
      if (!isLifecycleListenerAdded) {
        isLifecycleListenerAdded = true
        reactContext.addLifecycleEventListener(lifecycleListener)
      }
      val isInForeground = reactContext.lifecycleState == LifecycleState.RESUMED
      broadcastReceivers.forEach {
        it.update(reactContext, shouldRegister = isInForeground && it.event.isObserved)
      }
    }
  }

  // We default to false on web and any future platforms that haven't been implemented yet
  private val isLowPowerModeEnabled: Boolean
    get() {
      val powerManager = androidContext.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ?: return false
      return powerManager.isPowerSaveMode
    }
}

private class EventBroadcastReceiver(
  val event: ModuleEvent<*>,
  private val receiver: BroadcastReceiver,
  private val filter: IntentFilter
) {
  private var isRegistered = false

  fun update(context: Context, shouldRegister: Boolean) {
    if (shouldRegister == isRegistered) {
      return
    }
    if (shouldRegister) {
      context.registerReceiver(receiver, filter)
    } else {
      context.unregisterReceiver(receiver)
    }
    isRegistered = shouldRegister
  }
}
