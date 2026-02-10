package com.example.temirelay

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.robotemi.sdk.Position
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnReposeStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener

class RelayActivity : AppCompatActivity(),
    OnRobotReadyListener,
    OnReposeStatusChangedListener {

    private lateinit var mqttManager: MqttRelayManager
    private lateinit var connectionStatus: TextView
    private lateinit var statusText: TextView
    private lateinit var locationText: TextView
    private lateinit var reposeStatusText: TextView
    private val gson = Gson()
    private var robotReady = false

    companion object {
        private const val TAG = "RelayActivity"
    }

    // ──────────────────────────── lifecycle ────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relay)

        connectionStatus = findViewById(R.id.connectionStatus)
        statusText = findViewById(R.id.statusText)
        locationText = findViewById(R.id.locationText)
        reposeStatusText = findViewById(R.id.reposeStatusText)

        setupMqtt()
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
    }

    // ──────────────────────────── robot callbacks ──────────────────────

    override fun onRobotReady(isReady: Boolean) {
        robotReady = isReady
        if (isReady) {
            Log.d(TAG, "Robot is ready")
            publishStatus("ready", "Robot is ready")
            runOnUiThread { statusText.text = "Robot Ready" }
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

            // Extract position from Zeelo / manual location
            val loc = locationData.getAsJsonObject("location")
            val lat = loc?.get("latitude")?.asFloat ?: 0f
            val lon = loc?.get("longitude")?.asFloat ?: 0f
            val floor = loc?.get("floorLevel")?.asInt ?: 0
            val source = locationData.get("locationSource")?.asString ?: "Unknown"
            val geofence = loc?.get("geofenceName")?.asString ?: ""

            // Update UI
            val summary = buildString {
                append("Lat: ${String.format("%.6f", lat)}\n")
                append("Lon: ${String.format("%.6f", lon)}\n")
                append("Floor: $floor")
                if (geofence.isNotEmpty()) append("  |  Geofence: $geofence")
                append("\nSource: $source")
            }
            runOnUiThread {
                locationText.text = summary
                statusText.text = "Location received – calling repose"
            }

            // Call temi SDK repose with the received position
            // Position(x, y, yaw) – we map lat→x, lon→y, yaw=0
            val position = Position(lat, lon, 0f, 0)
            Log.d(TAG, "Calling repose with Position(x=$lat, y=$lon, yaw=0)")

            val robot = Robot.getInstance()
            robot.repose(position)

            publishStatus("reposing", "Repose started (x=$lat, y=$lon)")

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
