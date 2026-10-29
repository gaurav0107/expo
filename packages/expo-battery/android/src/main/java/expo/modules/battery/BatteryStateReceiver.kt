package expo.modules.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager

class BatteryStateReceiver(private val onBatteryStateChange: (batteryState: BatteryModule.BatteryState) -> Unit) : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val bs = batteryStatusNativeToJS(status, plugged)
    onBatteryStateChange(bs)
  }
}
