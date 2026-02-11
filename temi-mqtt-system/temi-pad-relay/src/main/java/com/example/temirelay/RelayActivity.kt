package com.example.temirelay

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.robotemi.sdk.Position
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnReposeStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager

class RelayActivity : AppCompatActivity(),
    OnRobotReadyListener,
    OnReposeStatusChangedListener {

    private lateinit var mqttManager: MqttRelayManager
    private lateinit var coordinateMapper: CoordinateMapper

    // UI – status
    private lateinit var connectionStatus: TextView
    private lateinit var statusText: TextView

    // UI – calibration
    private lateinit var calibrationStatus: TextView
    private lateinit var robotPositionText: TextView
    private lateinit var zeeloPositionText: TextView
    private lateinit var btnCaptureA: Button
    private lateinit var btnCaptureB: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnResetCalibration: Button

    // UI – location / repose
    private lateinit var locationText: TextView
    private lateinit var mappedPositionText: TextView
    private lateinit var reposeStatusText: TextView

    private val gson = Gson()
    private var robotReady = false

    // Calibration staging
    private var pendingAnchorA: CoordinateMapper.Anchor? = null
    private var pendingAnchorB: CoordinateMapper.Anchor? = null

    // Latest Zeelo data (set by incoming MQTT, used by calibration capture)
    private var latestHkE: Double? = null
    private var latestHkN: Double? = null

    companion object {
        private const val TAG = "RelayActivity"
    }

    // ──────────────────────────── lifecycle ────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relay)

        coordinateMapper = CoordinateMapper(this)

        initViews()
        setupCalibrationButtons()
        setupMqtt()
        refreshCalibrationUI()

        // ── Keep-alive: keep screen on, foreground service, battery exemption ──
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MqttForegroundService.start(this)
        requestBatteryOptimizationExemption()
    }

    override fun onStart() {
        super.onStart()
        Robot.getInstance().addOnRobotReadyListener(this)
        Robot.getInstance().addOnReposeStatusChangedListener(this)
    }

    override fun onStop() {
        super.onStop()
        Robot.getInstance().removeOnRobotReadyListener(this)
        Robot.getInstance().removeOnReposeStatusChangedListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
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

        calibrationStatus = findViewById(R.id.calibrationStatus)
        robotPositionText = findViewById(R.id.robotPositionText)
        zeeloPositionText = findViewById(R.id.zeeloPositionText)
        btnCaptureA = findViewById(R.id.btnCaptureA)
        btnCaptureB = findViewById(R.id.btnCaptureB)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        btnResetCalibration = findViewById(R.id.btnResetCalibration)

        locationText = findViewById(R.id.locationText)
        mappedPositionText = findViewById(R.id.mappedPositionText)
        reposeStatusText = findViewById(R.id.reposeStatusText)
    }

    // ──────────────────────────── robot callbacks ──────────────────────

    override fun onRobotReady(isReady: Boolean) {
        robotReady = isReady
        if (isReady) {
            Log.d(TAG, "Robot is ready")
            publishStatus("ready", "Robot is ready")
            runOnUiThread {
                statusText.text = "Robot Ready"
                updateRobotPositionDisplay()
            }
        }
    }

    override fun onReposeStatusChanged(status: Int, description: String) {
        val label = when (status) {
            OnReposeStatusChangedListener.IDLE -> "Idle"
            OnReposeStatusChangedListener.REPOSE_REQUIRED -> "Repose Required"
            OnReposeStatusChangedListener.REPOSING_START -> "Reposing..."
            OnReposeStatusChangedListener.REPOSING_GOING -> "Reposing (searching)"
            OnReposeStatusChangedListener.REPOSING_COMPLETE -> "Repose Complete ✓"
            OnReposeStatusChangedListener.REPOSING_OBSTACLE_DETECTED -> "Obstacle Detected"
            OnReposeStatusChangedListener.REPOSING_ABORT -> "Repose Aborted"
            else -> "Unknown ($status)"
        }
        Log.d(TAG, "Repose status: $label – $description")
        publishStatus("repose_$status", "$label: $description")
        runOnUiThread { reposeStatusText.text = "Repose: $label" }
    }

    // ──────────────────────────── calibration ─────────────────────────

    private fun setupCalibrationButtons() {

        btnCaptureA.setOnClickListener { captureAnchor("A") }
        btnCaptureB.setOnClickListener { captureAnchor("B") }

        btnCalibrate.setOnClickListener {
            val a = pendingAnchorA
            val b = pendingAnchorB
            if (a == null || b == null) {
                Toast.makeText(this, "Capture both A and B first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ok = coordinateMapper.calibrate(a, b)
            if (ok) {
                Toast.makeText(this, "Calibration complete ✓", Toast.LENGTH_SHORT).show()
                publishStatus("calibrated", coordinateMapper.getCalibrationSummary())
            } else {
                Toast.makeText(this, "Calibration failed – anchors too close", Toast.LENGTH_LONG).show()
            }
            refreshCalibrationUI()
        }

        btnResetCalibration.setOnClickListener {
            coordinateMapper.resetCalibration()
            pendingAnchorA = null
            pendingAnchorB = null
            Toast.makeText(this, "Calibration reset", Toast.LENGTH_SHORT).show()
            refreshCalibrationUI()
        }
    }

    /**
     * Capture the robot's current temi position + the latest Zeelo HK1980 coords
     * as a calibration anchor point.
     */
    private fun captureAnchor(label: String) {
        if (!robotReady) {
            Toast.makeText(this, "Robot not ready", Toast.LENGTH_SHORT).show()
            return
        }
        val hkE = latestHkE
        val hkN = latestHkN
        if (hkE == null || hkN == null) {
            Toast.makeText(this, "No Zeelo location received yet", Toast.LENGTH_SHORT).show()
            return
        }

        // Get robot's current position on the temi map
        val robot = Robot.getInstance()
        val pos = robot.getPosition()
        if (pos == null) {
            Toast.makeText(this, "Cannot read robot position", Toast.LENGTH_SHORT).show()
            return
        }

        val anchor = CoordinateMapper.Anchor(
            hkE = hkE,
            hkN = hkN,
            temiX = pos.x,
            temiY = pos.y
        )

        if (label == "A") {
            pendingAnchorA = anchor
            btnCaptureA.text = "A ✓"
        } else {
            pendingAnchorB = anchor
            btnCaptureB.text = "B ✓"
        }

        btnCalibrate.isEnabled = (pendingAnchorA != null && pendingAnchorB != null)

        Log.i(TAG, "Anchor $label captured: $anchor")
        Toast.makeText(this,
            "Anchor $label: hk(%.2f, %.2f) ↔ temi(%.3f, %.3f)".format(hkE, hkN, pos.x, pos.y),
            Toast.LENGTH_LONG).show()

        refreshCalibrationUI()
    }

    private fun refreshCalibrationUI() {
        if (coordinateMapper.isCalibrated) {
            calibrationStatus.text = "Calibrated ✓"
            calibrationStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            val parts = mutableListOf<String>()
            if (pendingAnchorA != null) parts.add("A ✓")
            if (pendingAnchorB != null) parts.add("B ✓")
            calibrationStatus.text = if (parts.isEmpty()) "Not calibrated"
            else "Captured: ${parts.joinToString(", ")} – press Calibrate"
            calibrationStatus.setTextColor(0xFFFF5722.toInt())
        }
        updateRobotPositionDisplay()
    }

    private fun updateRobotPositionDisplay() {
        if (!robotReady) {
            robotPositionText.text = "Robot position: not ready"
            return
        }
        try {
            val pos = Robot.getInstance().getPosition()
            if (pos != null) {
                robotPositionText.text = "Robot position: x=%.3f  y=%.3f  yaw=%.1f".format(pos.x, pos.y, pos.yaw)
            } else {
                robotPositionText.text = "Robot position: unavailable"
            }
        } catch (e: Exception) {
            robotPositionText.text = "Robot position: error"
        }
    }

    // ──────────────────────────── MQTT ─────────────────────────────────

    private fun setupMqtt() {
        mqttManager = MqttRelayManager(this)

        mqttManager.setConnectionListener { connected ->
            runOnUiThread {
                connectionStatus.text = if (connected) "MQTT: Connected" else "MQTT: Disconnected"
                connectionStatus.setTextColor(
                    if (connected) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
                )
            }
        }

        mqttManager.setMessageListener { _, message ->
            Log.d(TAG, "Received: $message")
            handleCommand(message)
        }

        mqttManager.connect()
    }

    // ──────────────────────────── command handler ──────────────────────

    private fun handleCommand(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val action = json.get("action")?.asString ?: return

            if (action != "update_location") {
                Log.w(TAG, "Ignoring unknown action: $action")
                return
            }

            val locationData = json.getAsJsonObject("location_data")
            if (locationData == null) {
                Log.w(TAG, "update_location: missing location_data")
                return
            }

            // Resolve active location based on locationSource:
            //   "LocationEngine" → use "location" object (Zeelo indoor)
            //   "GPS"            → use "gpsLocation" object (GPS fallback)
            //   "Manual"         → use "location" object
            val source = locationData.get("locationSource")?.asString ?: "Unknown"
            val activeLoc = when (source) {
                "GPS" -> locationData.getAsJsonObject("gpsLocation")
                    ?: locationData.getAsJsonObject("location")
                else -> locationData.getAsJsonObject("location")
            }

            if (activeLoc == null) {
                Log.w(TAG, "update_location: no location object for source=$source")
                return
            }

            val lat = activeLoc.get("latitude")?.asDouble ?: 0.0
            val lon = activeLoc.get("longitude")?.asDouble ?: 0.0
            val hkE = activeLoc.get("hkE")?.asDouble
            val hkN = activeLoc.get("hkN")?.asDouble
            val floor = activeLoc.get("floorLevel")?.asInt ?: 0
            val geofence = activeLoc.get("geofenceName")?.asString ?: ""
            val direction = locationData.get("direction")?.asFloat ?: Config.DEFAULT_YAW

            // Cache latest Zeelo data for calibration capture
            if (hkE != null && hkN != null && hkE != 0.0 && hkN != 0.0) {
                latestHkE = hkE
                latestHkN = hkN
            }

            // Build display summary
            val summary = buildString {
                append("Lat: ${String.format("%.6f", lat)}\n")
                append("Lon: ${String.format("%.6f", lon)}\n")
                if (hkE != null && hkN != null) {
                    append("HK1980: E=%.2f  N=%.2f\n".format(hkE, hkN))
                }
                append("Floor: $floor")
                if (geofence.isNotEmpty()) append("  |  Geofence: $geofence")
                append("\nSource: $source")
            }

            runOnUiThread {
                locationText.text = summary
                zeeloPositionText.text = if (hkE != null && hkN != null)
                    "Zeelo location: hkE=%.2f  hkN=%.2f".format(hkE, hkN)
                else "Zeelo location: (no HK1980 data)"
                updateRobotPositionDisplay()
            }

            // ── Map Zeelo coordinates to temi Position ──────────────
            val position: Position?

            if (Config.USE_HK1980_MAPPING && hkE != null && hkN != null && hkE != 0.0 && hkN != 0.0) {
                // Use calibrated affine transform (HK1980 → temi map)
                if (!coordinateMapper.isCalibrated) {
                    Log.w(TAG, "Not calibrated – skipping repose")
                    runOnUiThread {
                        statusText.text = "⚠ Not calibrated – calibrate first"
                        mappedPositionText.text = "Mapped temi position: needs calibration"
                    }
                    publishStatus("not_calibrated", "Location received but coordinate mapper is not calibrated")
                    return
                }
                position = coordinateMapper.toTemiPosition(hkE, hkN, direction)
            } else {
                // Fallback: raw lat/lon directly (not recommended – only for testing)
                Log.w(TAG, "Using raw lat/lon as Position – only for testing")
                position = Position(lat.toFloat(), lon.toFloat(), direction, 0)
            }

            if (position == null) {
                Log.e(TAG, "Failed to map position")
                runOnUiThread {
                    statusText.text = "Mapping error"
                    mappedPositionText.text = "Mapped temi position: error"
                }
                return
            }

            runOnUiThread {
                statusText.text = "Location received – calling repose"
                mappedPositionText.text = "Mapped temi: x=%.3f  y=%.3f  yaw=%.1f".format(
                    position.x, position.y, position.yaw)
            }

            Log.d(TAG, "Calling repose with Position(x=${position.x}, y=${position.y}, yaw=${position.yaw})")
            Robot.getInstance().repose(position)
            publishStatus("reposing",
                "Repose started (x=%.3f, y=%.3f, yaw=%.1f)".format(position.x, position.y, position.yaw))

        } catch (e: Exception) {
            Log.e(TAG, "Error handling command", e)
            publishStatus("error", e.message ?: "Unknown error")
            runOnUiThread { statusText.text = "Error: ${e.message}" }
        }
    }

    // ──────────────────────────── status publish ───────────────────────

    private fun publishStatus(status: String, detail: String) {
        val json = JsonObject().apply {
            addProperty("status", status)
            addProperty("detail", detail)
            addProperty("timestamp", System.currentTimeMillis())
        }
        mqttManager.publish(Config.TOPIC_STATUS, json.toString())
    }
}
