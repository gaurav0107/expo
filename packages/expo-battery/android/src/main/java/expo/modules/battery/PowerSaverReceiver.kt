package expo.modules.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class PowerSaverReceiver(private val onLowPowerModeChange: (lowPowerMode: Boolean) -> Unit) : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isLowPowerMode = powerManager.isPowerSaveMode
    onLowPowerModeChange(isLowPowerMode)
  }
}
