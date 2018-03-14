package vitalyilchenko.com.usbhostcheck

import android.content.Context
import android.content.Intent
import android.support.v4.content.WakefulBroadcastReceiver

/**
 * Created by vitalyilchenko on 3/4/18.
 */
// WakefulBroadcastReceiver ensures the device does not go back to sleep
// during the startup of the service
class BootBroadcastReceiver : WakefulBroadcastReceiver() {

    companion object {
        val IntentExtraName: String = "bootload"
        var Action: String = "BOOT_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
//        val startServiceIntent = Intent(context, GeoPositionService::class.java)
//        startServiceIntent.action = Action
//        startServiceIntent.putExtra(IntentExtraName, true);
//        startWakefulService(context, startServiceIntent)
    }
}