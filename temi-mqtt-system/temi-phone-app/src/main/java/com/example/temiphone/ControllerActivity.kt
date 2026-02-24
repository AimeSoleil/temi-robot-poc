package com.example.temiphone

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager

class ControllerActivity : AppCompatActivity() {

    private lateinit var mqttManager: MqttManager
    private lateinit var locationApiClient: LocationApiClient

    private lateinit var connectionStatus: TextView
    private lateinit var statusText: TextView
    private lateinit var zeeloLocationText: TextView
    private lateinit var btnToggleAutoPublish: Button
    private lateinit var editLatitude: EditText
    private lateinit var editLongitude: EditText
    private lateinit var editHkE: EditText
    private lateinit var editHkN: EditText
    private lateinit var editFloor: EditText
    private lateinit var btnSendManualLocation: Button

    private val gson = Gson()

    /** Whether to auto-publish Zeelo SDK callbacks to MQTT */
    private var autoPublishEnabled = false

    /** Timestamp of the last MQTT publish (for throttling) */
    private var lastPublishTimeMs = 0L

    companion object {
        private const val TAG = "ControllerActivity"
    }

    // ──────────────────────────── lifecycle ────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        initViews()
        setupMqtt()
        setupButtons()
        initializeZeeloSDK()

        // ── Keep-alive: foreground service + battery exemption ──
        MqttForegroundService.start(this)
        requestBatteryOptimizationExemption()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationApiClient.stopLocationService()
        mqttManager.disconnect()
        MqttForegroundService.stop(this)
    }

    // ──────────────────────────── battery optimization ─────────────────

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot request battery optimization exemption", e)
                }
            }
        }
    }

    // ──────────────────────────── views ────────────────────────────────

    private fun initViews() {
        connectionStatus = findViewById(R.id.connectionStatus)
        statusText = findViewById(R.id.statusText)
        zeeloLocationText = findViewById(R.id.zeeloLocationText)
        btnToggleAutoPublish = findViewById(R.id.btnToggleAutoPublish)
        editLatitude = findViewById(R.id.editLatitude)
        editLongitude = findViewById(R.id.editLongitude)
        editHkE = findViewById(R.id.editHkE)
        editHkN = findViewById(R.id.editHkN)
        editFloor = findViewById(R.id.editFloor)
        btnSendManualLocation = findViewById(R.id.btnSendManualLocation)
    }

    // ──────────────────────────── MQTT ─────────────────────────────────

    private fun setupMqtt() {
        mqttManager = MqttManager(this)

        mqttManager.setConnectionListener { connected ->
            runOnUiThread {
                connectionStatus.text = if (connected) "MQTT: Connected" else "MQTT: Disconnected"
                connectionStatus.setTextColor(
                    if (connected) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
                )
            }
        }

        mqttManager.setMessageListener { topic, message ->
            if (topic == Config.TOPIC_STATUS) {
                runOnUiThread {
                    try {
                        val json = gson.fromJson(message, JsonObject::class.java)
                        val status = json.get("status")?.asString ?: "unknown"
                        val detail = json.get("detail")?.asString ?: ""
                        statusText.text = "Relay: $status – $detail"
                    } catch (_: Exception) {
                        statusText.text = "Relay: $message"
                    }
                }
            }
        }

        mqttManager.connect()
    }

    // ──────────────────────────── Zeelo SDK ────────────────────────────

    private fun initializeZeeloSDK() {
        locationApiClient = LocationApiClient(this)
        locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
            override fun onLocationUpdated(
                location: com.cherrypicks.zeelosdk.lite.location.model.Location?,
                gpsLocation: com.cherrypicks.zeelosdk.lite.location.model.GPSLocation?,
                source: com.cherrypicks.zeelosdk.lite.location.model.Source?
            ) {
                runOnUiThread {
                    updateZeeloLocationDisplay()

                    // Auto-publish to MQTT if enabled, respecting minimum interval
                    if (autoPublishEnabled) {
                        val now = System.currentTimeMillis()
                        if (now - lastPublishTimeMs >= Config.ZEELO_MIN_PUBLISH_INTERVAL_MS) {
                            publishZeeloLocation()
                            lastPublishTimeMs = now
                        }
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    statusText.text = "Zeelo error: $error"
                    Log.e(TAG, "Zeelo SDK error: $error")
                }
            }
        })
    }

    private fun updateZeeloLocationDisplay() {
        val lat = locationApiClient.getActiveLatitude()
        val lon = locationApiClient.getActiveLongitude()
        if (lat == null || lon == null) {
            zeeloLocationText.text = "Location: Waiting for Zeelo SDK..."
            return
        }
        val source = locationApiClient.getLocationSourceName()
        val hkE = locationApiClient.getActiveHkE()
        val hkN = locationApiClient.getActiveHkN()
        val floor = locationApiClient.getActiveFloorLevel() ?: 0
        zeeloLocationText.text = buildString {
            append("Source: $source\n")
            append("Lat: ${String.format("%.6f", lat)}\n")
            append("Lon: ${String.format("%.6f", lon)}\n")
            if (hkE != null && hkN != null) {
                append("HK1980: E=%.2f  N=%.2f\n".format(hkE, hkN))
            }
            append("Floor: $floor")
            if (locationApiClient.isIndoorSource()) {
                val gf = locationApiClient.getCurrentLocation()?.geofenceName
                if (!gf.isNullOrEmpty()) append("  |  Geofence: $gf")
            }
        }
    }

    // ──────────────────────────── auto-publish toggle ──────────────────

    private fun toggleAutoPublish() {
        autoPublishEnabled = !autoPublishEnabled
        if (autoPublishEnabled) {
            btnToggleAutoPublish.text = "Stop Auto-Publish"
            btnToggleAutoPublish.setBackgroundColor(0xFFF44336.toInt())
            lastPublishTimeMs = 0L  // allow immediate first publish
            statusText.text = "Auto-publish ON (min interval: ${Config.ZEELO_MIN_PUBLISH_INTERVAL_MS / 1000}s)"
            Log.d(TAG, "Auto-publish enabled")
        } else {
            btnToggleAutoPublish.text = "Start Auto-Publish"
            btnToggleAutoPublish.setBackgroundColor(0xFF4CAF50.toInt())
            statusText.text = "Auto-publish OFF"
            Log.d(TAG, "Auto-publish disabled")
        }
    }

    // ──────────────────────────── publish ──────────────────────────────

    private fun publishZeeloLocation() {
        try {
            val locationJson = locationApiClient.exportCurrentLocationAsJson()
            val command = JsonObject().apply {
                addProperty("action", "update_location")
                add("location_data", gson.fromJson(locationJson, JsonObject::class.java))
            }
            mqttManager.publishCommand(command.toString())
            runOnUiThread {
                updateZeeloLocationDisplay()
                statusText.text = "Zeelo location published  (${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())})"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing Zeelo location", e)
            runOnUiThread { statusText.text = "Publish error: ${e.message}" }
        }
    }

    private fun publishManualLocation() {
        val latStr = editLatitude.text.toString().trim()
        val lonStr = editLongitude.text.toString().trim()
        val hkEStr = editHkE.text.toString().trim()
        val hkNStr = editHkN.text.toString().trim()
        val floorStr = editFloor.text.toString().trim()

        if (latStr.isEmpty() || lonStr.isEmpty()) {
            Toast.makeText(this, "Please enter latitude and longitude", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        if (lat == null || lon == null) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show()
            return
        }

        val hkE = hkEStr.toDoubleOrNull() ?: 0.0
        val hkN = hkNStr.toDoubleOrNull() ?: 0.0

        try {
            val locationData = JsonObject().apply {
                add("location", JsonObject().apply {
                    addProperty("latitude", lat)
                    addProperty("longitude", lon)
                    addProperty("hkE", hkE)
                    addProperty("hkN", hkN)
                    addProperty("floorLevel", floorStr.toIntOrNull() ?: 0)
                    addProperty("isOutDoor", false)
                    addProperty("geofenceName", "")
                    addProperty("geofenceId", "")
                    addProperty("floorName", "")
                    addProperty("coverageArea", false)
                })
                addProperty("locationSource", "Manual")
                addProperty("direction", 0.0)
                addProperty("timestamp", System.currentTimeMillis())
            }
            val command = JsonObject().apply {
                addProperty("action", "update_location")
                add("location_data", locationData)
            }
            mqttManager.publishCommand(command.toString())
            statusText.text = buildString {
                append("Manual sent: lat=$latStr, lon=$lonStr")
                if (hkE != 0.0 || hkN != 0.0) append(", hkE=$hkEStr, hkN=$hkNStr")
                append(", floor=${floorStr.ifEmpty { "0" }}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing manual location", e)
            statusText.text = "Send error: ${e.message}"
        }
    }

    // ──────────────────────────── buttons ──────────────────────────────

    private fun setupButtons() {
        btnToggleAutoPublish.setOnClickListener { toggleAutoPublish() }
        btnSendManualLocation.setOnClickListener { publishManualLocation() }
    }
}
