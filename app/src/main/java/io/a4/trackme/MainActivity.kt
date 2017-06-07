package io.a4.trackme

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.support.v4.content.ContextCompat
import android.util.Log
import android.support.v7.app.AppCompatActivity
import android.support.v4.app.ActivityCompat
import android.os.Bundle
import android.support.v7.app.NotificationCompat
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import org.jetbrains.anko.*
import org.json.JSONObject
import org.jetbrains.anko.appcompat.v7.tintedEditText
import org.jetbrains.anko.design.textInputLayout
import android.provider.Settings
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.widget.SwitchCompat
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.github.kittinunf.fuel.core.FuelManager
import org.jetbrains.anko.appcompat.v7.tintedButton
import org.jetbrains.anko.appcompat.v7.tintedTextView
import org.jetbrains.anko.custom.ankoView
import org.jetbrains.anko.db.*
import java.util.concurrent.atomic.AtomicInteger


val PREFS_FILENAME = "io.a4.trackme.prefs"


inline fun ViewManager.switchCompat(theme: Int = 0) = switchCompat(theme) {}

inline fun ViewManager.switchCompat(theme: Int = 0, init: SwitchCompat.() -> Unit): SwitchCompat {
    return ankoView({ SwitchCompat(it) }, theme, init)
}

class App : Application() {
    companion object {
        lateinit var instance: App
            private  set

        lateinit var cm: ConnectivityManager
            private  set

        lateinit var prefs: SharedPreferences
            private set

        lateinit var lm: LocationManager
            private set

        lateinit var broadcaster: LocalBroadcastManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        prefs = this.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
        broadcaster = LocalBroadcastManager.getInstance(this)
    }
}

class DbHelper : ManagedSQLiteOpenHelper(App.instance, "locations.db", null, 1) {

    companion object {
        val instance by lazy { DbHelper() }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.createTable("locations", true,
                "_id" to INTEGER + PRIMARY_KEY,
                //"lat" to REAL,
                //"lng" to REAL,
                "ts" to INTEGER
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.dropTable("locations", true)
        onCreate(db)
    }
}
// Access property for Context
val Context.database: DbHelper
    get() = DbHelper.instance

class Loc(val _id: Int, val ts: Int)

class MainActivity : AppCompatActivity() {

    var lastReq: TextView? = null
    var I: Int = 0

    // Custom method to determine whether a service is running
    fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Loop through the running services
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                // If the service is running then return true
                return true
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var intent = Intent(getBaseContext(), LocationTrackingService::class.java)
        var servicerunning = isServiceRunning(LocationTrackingService::class.java)

        var endpoint: EditText? = null
        var user: EditText? = null
        var pass: EditText? = null
        var lastErr: TextView? = null
        var switchLabel = "Stopped"
        if (servicerunning) {
            switchLabel = "Running"
        }


        scrollView {
            verticalLayout {
                padding = dip(16)
                tintedTextView {
                    textSize = 18f
                    text = "Status"
                    //gravity = Gravity.CENTER
                    bottomPadding = dip(12)
                }
                switchCompat {
                    text = switchLabel
                    textSize = 18f
                    isChecked = servicerunning
                    setOnCheckedChangeListener { buttonView, isChecked ->
                        if (isChecked) {
                            startService(intent)
                            this.text = "Running"
                            toast("Service started")
                        } else {
                            stopService(intent)
                            this.text = "Stopped"
                            toast("Service stopped")
                        }
                    }
                    bottomPadding = dip(24)
                }
                lastReq = tintedTextView {
                    text = ""
                }
                lastErr = tintedTextView {
                    text = ""
                    bottomPadding = dip(24)
                }
                tintedTextView {
                    textSize = 18f
                    text = "Settings"
                    //gravity = Gravity.CENTER
                    bottomPadding = dip(24)
                }
                textInputLayout {
                    endpoint = tintedEditText {
                        hint = "HTTP Endpoint URL"
                        singleLine = true
                    }
                }.lparams(width = matchParent, height = wrapContent)
                textInputLayout {
                    user = tintedEditText {
                        hint = "Username"
                        singleLine = true
                    }
                }.lparams(width = matchParent, height = wrapContent)
                textInputLayout {
                    pass = tintedEditText {
                        hint = "Password"
                        singleLine = true
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                }.lparams(width = matchParent, height = wrapContent)


                endpoint!!.setText(App.prefs!!.getString("endpoint", ""))
                user!!.setText(App.prefs!!.getString("user", ""))
                pass!!.setText(App.prefs!!.getString("pass", ""))
                lastReq!!.setText("Last request: ${App.prefs!!.getString("last_request", "never")}")
                lastErr!!.setText("Last error: ${App.prefs!!.getString("last_error", "never")}")
                tintedButton("Save") {
                    setOnClickListener {
                        val editor = App.prefs!!.edit()
                        editor.putString("endpoint", endpoint!!.text.toString())
                        editor.putString("user", user!!.text.toString())
                        editor.putString("pass", pass!!.text.toString())
                        editor.apply()

                        lastReq!!.setText("Last request omg: ${App.prefs!!.getString("last_error", "never")}")
                        longToast("Settings saved")
                    }
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 123)
        }


    }

    override fun onResume() {
        super.onResume()
        toast("onresume")
        database.use {
            var data = select("locations", "_id", "ts").parseList(classParser<Loc>())

            I++
            lastReq!!.setText("Last request ${data}: ${App.prefs!!.getString("last_error", "never")}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (permissions.size == 0) return
    }
}

//if (requestCode == 123) {
//        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted.
            //doLocationAccessRelatedJob();
        //}
        //else {
        //    // User refused to grant permission. You can add AlertDialog here
        //    Toast.makeText(this, "You didn't give permission to access device location", Toast.LENGTH_LONG).show();
        //    startInstalledAppDetailsActivity();
        //}

object NotificationID {
    private val c = AtomicInteger(0)
    val id: Int
        get() = c.incrementAndGet()
}



class LocationTrackingService    : Service() {
    var locationManager: LocationManager? = null
    var notifID: Int = 0


    //: Notification? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "start called")
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onCreate() {
        if (locationManager == null)
            locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            var listener = locationListeners[0]
            listener.deviceID = Settings.Secure.getString(applicationContext.getContentResolver(), Settings.Secure.ANDROID_ID)
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL, DISTANCE, listener)
        } catch (e: SecurityException) {
            Log.e(TAG, "Fail to request location update", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "GPS provider does not exist", e)
        }
        Log.i(TAG, "look good")
        FuelManager.instance.baseHeaders = mapOf(
                "User-Agent" to "Are You Tracking Me? - ${BuildConfig.VERSION_NAME}")
        var notif = NotificationCompat.Builder(this@LocationTrackingService)
                .setContentTitle("Yes, I'm tracking you!")
                .setSubText("Running")
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        val mNotifyMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
// Builds the notification and issues it.
        notifID = NotificationID.id
        mNotifyMgr.notify(notifID, notif)
        RUNNING = true
    }

    override fun onDestroy() {
        super.onDestroy()
        val mNotifyMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotifyMgr.cancel(notifID)
        if (locationManager != null)
            for (i in 0..locationListeners.size) {
                try {
                    locationManager?.removeUpdates(locationListeners[i])
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove location listeners")
                }
            }

        RUNNING = false
    }


    companion object {
        val TAG = "LocationTrackingService"
        var RUNNING = false
        //val INTERVAL = 600000.toLong()
        val INTERVAL = 5000.toLong()
        val DISTANCE = 0.toFloat() // In meters

        //var lol = applicationContext.getSharedPreferences(PREFS_FILENAME, 0)
        val locationListeners = arrayOf(
                LTRLocationListener(LocationManager.GPS_PROVIDER)
                //LTRLocationListener(LocationManager.NETWORK_PROVIDER)
        )

        class LTRLocationListener(provider: String) : android.location.LocationListener {

            val lastLocation = Location(provider)
            var deviceID: String? = null

            override fun onLocationChanged(location: Location?) {
                lastLocation.set(location)
                val ts = System.currentTimeMillis()
                var payload = JSONObject()
                payload.put("lat", location!!.latitude)
                payload.put("lng", location!!.longitude)
                payload.put("ts", ts)
                payload.put("device_id", this.deviceID!!)

                Log.i(TAG, "Sending payload to server")

                val endpoint = App.prefs!!.getString("endpoint", "")
                val user = App.prefs!!.getString("user", "")
                val pass = App.prefs!!.getString("pass", "")
                // TODO get the connectivitymanager the same way as the android id
                //var cm: ConnectivityManager = .getSystemService(Context.CONNECTIVITY_SERVICE)
                var activeNetwork: NetworkInfo  = App.cm.getActiveNetworkInfo()
                if (activeNetwork != null) {
                    if (activeNetwork.isConnectedOrConnecting()) {

                    }
                }
                App.instance.applicationContext.database.use {
                    val values = ContentValues()
                    //values.put("lat", 1.0)
                    //values.put("lng", 2.0)
                    values.put("ts", ts)
                    values.put("_id", ts)
                    insert("locations", null, values)
                }

                //doAsync {
                    val editor = App.prefs!!.edit()
                    val date = DateFormat.format("yyyy-MM-ddThh:mm:ss a", java.util.Date()).toString()
                    editor.putString("last_request", date)
                    Fuel.post(endpoint).authenticate(user, pass).body(payload.toString()).response { _, response, result ->
                        when (result) {
                            is Result.Failure -> {
                                val body = String(response.data)
                                val code = response.httpStatusCode
                                Log.e(TAG, "failed to send payload $code: $body")
                                editor.putString("last_error", "$date, error $code: $body")
                                editor.commit()
                            }
                            is Result.Success -> {
                                Log.i(TAG, "payload sent")
                                editor.commit()
                            }
                        }
                    }
                //}
            }
            override fun onProviderDisabled(provider: String?) {
            }

            override fun onProviderEnabled(provider: String?) {
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }

        }
    }

}