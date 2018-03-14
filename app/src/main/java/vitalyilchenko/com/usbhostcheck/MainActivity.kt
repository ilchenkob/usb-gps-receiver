package vitalyilchenko.com.usbhostcheck

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.TextView
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.app.ActivityCompat




class MainActivity : AppCompatActivity() {

    private var positionUpdateReceiver = PositionUpdateReceiver()
    private var serviceIntent: Intent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var txtCount = findViewById<TextView>(R.id.txtCount)
        var txtState = findViewById<TextView>(R.id.txtState)
        var txtPosition = findViewById<TextView>(R.id.txtPosition)
        var txtFixData = findViewById<TextView>(R.id.txtFixData)
        var txtSatData = findViewById<TextView>(R.id.txtSatData)

        var btnConnect = findViewById<Button>(R.id.btnConnect)
        var btnDisconnect = findViewById<Button>(R.id.btnDisconnect)

        positionUpdateReceiver.onUpdateCallback = { usbCount, state, lat, lon, fix, sat ->
            txtCount?.post { txtCount?.text = usbCount.toString() }
            txtState?.post { txtState?.text = state }
            txtPosition?.post { txtPosition?.text = "Lat: ${lat}  Lon: ${lon}" }
            txtFixData?.post { txtFixData?.text = fix }
            txtSatData?.post { txtSatData?.text = sat }
        }

        btnConnect?.setOnClickListener { _ ->
            serviceIntent = Intent(applicationContext, GeoPositionService::class.java)
            serviceIntent.action = GeoPositionService.START_ACTION
            startService(serviceIntent)

            btnConnect?.isEnabled = false
            btnDisconnect?.isEnabled = true
        }

        btnDisconnect?.setOnClickListener { _ ->
            serviceIntent.action = GeoPositionService.STOP_ACTION
            stopService(serviceIntent)

            btnConnect?.isEnabled = true
            btnDisconnect?.isEnabled = false
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            // Has location permissions
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION), 1990)
        }
    }

    override fun onDestroy() {
        stopService(serviceIntent)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter(GeoPositionService.UPDATE_ACTION)
        LocalBroadcastManager.getInstance(this).registerReceiver(positionUpdateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(positionUpdateReceiver)
    }
}

class PositionUpdateReceiver : BroadcastReceiver() {

    var onUpdateCallback: (usbCount: Int,
                           state: String,
                           lat: Double,
                           lon: Double,
                           fixData: String,
                           satData: String) -> Unit = { _,_,_,_,_,_ -> }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            var usbCount = intent.getIntExtra(GeoPositionService.UsbCountKey, -1)
            var state = intent.getStringExtra(GeoPositionService.StateKey)
            var lat = intent.getDoubleExtra(GeoPositionService.LatKey, 0.0)
            var lon = intent.getDoubleExtra(GeoPositionService.LonKey, 0.0)
            var fixData = intent.getStringExtra(GeoPositionService.FixDataKey)
            var satData = intent.getStringExtra(GeoPositionService.SatDataKey)
            onUpdateCallback.invoke(usbCount, state, lat, lon, fixData, satData)
        }
    }
}
