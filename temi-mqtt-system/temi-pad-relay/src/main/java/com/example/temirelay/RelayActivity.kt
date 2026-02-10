package com.example.temirelay

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener

class RelayActivity : AppCompatActivity(),
    OnRobotReadyListener,
    OnGoToLocationStatusChangedListener {

    private lateinit var mqttManager: MqttRelayManager
    private lateinit var statusText: TextView
    private lateinit var connectionStatus: TextView
    private val gson = Gson()

    companion object {
        private const val TAG = "RelayActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relay)

        statusText = findViewById(R.id.statusText)
        connectionStatus = findViewById(R.id.connectionStatus)

        setupMqtt()
    }

    private fun setupMqtt() {
        mqttManager = MqttRelayManager(this)

        mqttManager.setConnectionListener { connected ->
            runOnUiThread {
                connectionStatus.text = if (connected) "MQTT: Connected" else "MQTT: Disconnected"
            }
        }

        mqttManager.setMessageListener { topic, message ->
            Log.d(TAG, "Received command: $message")
            handleCommand(message)
        }

        mqttManager.connect()
    }

    private fun handleCommand(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val action = json.get("action")?.asString ?: return

            runOnUiThread {
                statusText.text = "Executing: $action"
            }

            val robot = Robot.getInstance()

            when (action) {
                "goto" -> {
                    val location = json.get("location")?.asString ?: return
                    publishStatus("navigating", "Going to $location")
                    robot.goTo(location)
                }
                "speak" -> {
                    val text = json.get("text")?.asString ?: return
                    publishStatus("speaking", text)
                    val ttsRequest = TtsRequest.create(text, true)
                    robot.speak(ttsRequest)
                }
                "stop" -> {
                    publishStatus("stopped", "Movement stopped")
                    robot.stopMovement()
                }
                "get_locations" -> {
                    val locations = robot.locations
                    val response = JsonObject().apply {
                        addProperty("type", "locations")
                        add("locations", gson.toJsonTree(locations))
                    }
                    mqttManager.publish(Config.TOPIC_LOCATION, response.toString())
                }
                else -> {
                    Log.w(TAG, "Unknown action: $action")
                    publishStatus("error", "Unknown action: $action")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command", e)
            publishStatus("error", e.message ?: "Unknown error")
        }
    }

    private fun publishStatus(status: String, detail: String) {
        val json = JsonObject().apply {
            addProperty("status", status)
            addProperty("detail", detail)
            addProperty("timestamp", System.currentTimeMillis())
        }
        mqttManager.publish(Config.TOPIC_STATUS, json.toString())
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady) {
            Log.d(TAG, "Robot is ready")
            publishStatus("ready", "Robot is ready")
            runOnUiThread {
                statusText.text = "Robot Ready"
            }
        }
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        Log.d(TAG, "GoTo status: $location -> $status ($description)")
        publishStatus(status.lowercase(), "GoTo $location: $description")

        runOnUiThread {
            statusText.text = "GoTo $location: $status"
        }
    }

    override fun onStart() {
        super.onStart()
        Robot.getInstance().addOnRobotReadyListener(this)
        Robot.getInstance().addOnGoToLocationStatusChangedListener(this)
    }

    override fun onStop() {
        super.onStop()
        Robot.getInstance().removeOnRobotReadyListener(this)
        Robot.getInstance().removeOnGoToLocationStatusChangedListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect()
    }
}
