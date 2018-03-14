package vitalyilchenko.com.usbhostcheck

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.content.BroadcastReceiver
import android.os.IBinder


/**
 * Created by vitalyilchenko on 3/4/18.
 */
class GeoPositionService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        val START_ACTION = "com.vitaliiilchenko.usbgps.startservice"
        val STOP_ACTION = "com.vitaliiilchenko.usbgps.stopservice"
        val RESTART_ACTION = "com.vitaliiilchenko.usbgps.restartservice"
        val UPDATE_ACTION = "com.vitaliiilchenko.usbgps.update_position"
        val UsbCountKey = "usb_count_key"
        val StateKey = "usb_state_key"
        val LatKey = "lat_key"
        val LonKey = "lon_key"
        val FixDataKey = "gps_fix_data_key"
        val SatDataKey = "gps_sat_data_key"
    }

    private var lastLocationSentTime = 0L

    private var connection: UsbDeviceConnection? = null
    private var trackingThread: Thread? = null

    private val VID = 5446
    private val PID = 423

    private var providerName = ""
    private var locationManager: LocationManager? = null

    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null

    private var usbCount = 0
    private var stateText = ""
    private var fixData = ""
    private var satData = ""
    private var latValue = 0.0
    private var lonValue = 0.0

//    override fun onHandleIntent(intent: Intent?) {
//        if (intent != null) {
////            if (intent.action == BootBroadcastReceiver.Action &&
////                    intent.hasExtra(BootBroadcastReceiver.IntentExtraName) &&
////                    intent.getBooleanExtra(BootBroadcastReceiver.IntentExtraName, false)) {
////                // Release the wake lock provided by the WakefulBroadcastReceiver.
////                WakefulBroadcastReceiver.completeWakefulIntent(intent)
////                startService()
////            } else
//            if (intent.action == START_ACTION) {
//                startService()
//            } else if (intent.action == STOP_ACTION) {
//                stopService()
//            }
//        }
//    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
        val broadcastIntent = Intent(RESTART_ACTION)
        sendBroadcast(broadcastIntent)
    }

    private fun startService() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val criteria = Criteria()
        criteria.accuracy = Criteria.ACCURACY_FINE
        var provider = locationManager?.getBestProvider(criteria, true)

        if (provider == null) {
            Log.i("USBGPS", "Provider is empty")
        } else {
            providerName = provider
        }

//        if (locationManager?.getProvider(providerName) != null) {
//            locationManager?.removeTestProvider(providerName)
//        }
        locationManager?.addTestProvider(providerName, false, false, false, false, true, true, true,
                Criteria.POWER_MEDIUM, Criteria.ACCURACY_FINE)

        if (trackingThread == null) {
            trackingThread = Thread({
                var buffer = ByteArray(64)
                var currentThread = trackingThread!!
                while (connection != null && !currentThread.isInterrupted) {
                    try {
                        var line = StringBuilder()
                        connection?.bulkTransfer(inEndpoint, buffer, buffer.size, 1000)
                        for (b in buffer) {
                            line.append(b.toChar())
                        }
                        if (line.contains("GPGGA", true)) {
                            fixData = line.toString()
                        } else if (line.contains("GPGSA", true)) {
                            satData = line.toString()
                        } else if (line.contains("GPGLL", true)) {
                            var now = SystemClock.uptimeMillis()
                            if (now - lastLocationSentTime > 990) { // not often than once per second
                                var data = line.toString()
                                var fields = data.split(',')
                                try {
                                    if (fields[1].contains('.') && fields[3].contains('.')) {
                                        latValue = convertGeoPosition(fields[1])
                                        lonValue = convertGeoPosition(fields[3])

                                        if (latValue > 10.0 && lonValue > 10.0) {
                                            mockLocation(latValue, lonValue)

                                        }
                                    }
                                } catch (ex: NumberFormatException) {
                                    latValue = 0.0
                                    lonValue = 0.0
                                }
                            }
                        }

                        sendUpdate()
                        Thread.sleep(90)
                    } catch(e: InterruptedException) {
                    }
                }
            })
        }

        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        var devices = manager.deviceList.values
        usbCount = devices.count()

        var device = devices.find { d -> d.vendorId == VID && d.productId == PID }
        if (device != null) {
            var cdcInterface = device.getInterface(1)
            outEndpoint = cdcInterface.getEndpoint(0)
            inEndpoint = cdcInterface.getEndpoint(1)

            connection = manager.openDevice(device)
            if (connection != null) {
                if (connection!!.claimInterface(cdcInterface, true)) {
                    connection?.controlTransfer(0x21, 0x22, 0x1, 0, null, 0, 0)
                    stateText = "CONNECTED: OK"

                    trackingThread?.start()
                } else {
                    stateText = "Claim interface failed"
                    connection?.close()
                    connection = null
                }
            }
        }

        sendUpdate()
    }

    private fun stopService() {
        trackingThread?.interrupt()
        connection?.close()
        connection = null
        stateText = "Disconnected"
        sendUpdate()
    }

    private fun sendUpdate() {
        val intent = Intent(UPDATE_ACTION)
        intent.putExtra(UsbCountKey, usbCount)
        intent.putExtra(StateKey, stateText)
        intent.putExtra(LonKey, lonValue)
        intent.putExtra(LatKey, latValue)
        intent.putExtra(SatDataKey, satData)
        intent.putExtra(FixDataKey, fixData)

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun mockLocation(lat: Double, lon: Double) {
        val loc = Location(providerName)
        loc.time = SystemClock.elapsedRealtime() // System.currentTimeMillis()
        loc.latitude = lat
        loc.longitude = lon
        loc.accuracy = 1.0f

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }

        locationManager?.setTestProviderLocation(providerName, loc)
    }

    private fun convertGeoPosition(originalValue: String): Double {
        var pointPosition = originalValue.indexOf('.')
        var degree = originalValue.substring(0, pointPosition - 2).toInt()
        var minutes = originalValue.substring(pointPosition - 2).toDouble()
        var decimal = degree + (minutes / 60.0)
        return decimal
    }
}

class SensorRestarterBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        var serviceIntent = Intent(context, GeoPositionService::class.java)
        serviceIntent.action = GeoPositionService.START_ACTION
        context.startService(serviceIntent)
    }
}