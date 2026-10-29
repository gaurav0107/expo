package expo.modules.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

class BatteryLevelReceiver(private val onBatteryLevelChange: (batteryLevel: Float) -> Unit) : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val batteryIntent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    if (batteryIntent == null) {
      Log.e("Battery", "ACTION_BATTERY_CHANGED unavailable. Events won't be received")
      return
    }
    val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val batteryLevel: Float = if (level != -1 && scale != -1) {
      level / scale.toFloat()
    } else {
      -1f
    }
    onBatteryLevelChange(batteryLevel)
  }
}
