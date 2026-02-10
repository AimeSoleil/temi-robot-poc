package com.example.temiphone

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject

class ControllerActivity : AppCompatActivity() {

    private lateinit var mqttManager: MqttManager
    private lateinit var locationApiClient: LocationApiClient
    private lateinit var connectionStatus: TextView
    private lateinit var statusText: TextView
    private lateinit var locationSpinner: Spinner
    private lateinit var btnGoTo: Button
    private lateinit var btnStop: Button
    private lateinit var btnRefreshLocations: Button
    private lateinit var editSpeak: EditText
    private lateinit var btnSpeak: Button

    private val gson = Gson()
    private val locations = mutableListOf<String>()

    companion object {
        private const val TAG = "ControllerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        initViews()
        setupMqtt()
        setupButtons()
    }

    private fun initViews() {
        connectionStatus = findViewById(R.id.connectionStatus)
        statusText = findViewById(R.id.statusText)
        locationSpinner = findViewById(R.id.locationSpinner)
        btnGoTo = findViewById(R.id.btnGoTo)
        btnStop = findViewById(R.id.btnStop)
        btnRefreshLocations = findViewById(R.id.btnRefreshLocations)
        editSpeak = findViewById(R.id.editSpeak)
        btnSpeak = findViewById(R.id.btnSpeak)
    }

    private fun setupMqtt() {
        mqttManager = MqttManager(this)
        locationApiClient = LocationApiClient()

        mqttManager.setConnectionListener { connected ->
            runOnUiThread {
                connectionStatus.text = if (connected) "MQTT: Connected" else "MQTT: Disconnected"
                btnGoTo.isEnabled = connected
                btnStop.isEnabled = connected
                btnSpeak.isEnabled = connected
                btnRefreshLocations.isEnabled = connected
            }
        }

        mqttManager.setMessageListener { topic, message ->
            when (topic) {
                Config.TOPIC_STATUS -> {
                    runOnUiThread {
                        try {
                            val json = gson.fromJson(message, JsonObject::class.java)
                            val status = json.get("status")?.asString ?: "unknown"
                            val detail = json.get("detail")?.asString ?: ""
                            statusText.text = "Status: $status - $detail"
                        } catch (e: Exception) {
                            statusText.text = "Status: $message"
                        }
                    }
                }
                Config.TOPIC_LOCATION -> {
                    val locs = locationApiClient.parseLocationsFromMqtt(message)
                    runOnUiThread {
                        locations.clear()
                        locations.addAll(locs)
                        updateLocationSpinner()
                    }
                }
            }
        }

        mqttManager.connect()
    }

    private fun setupButtons() {
        btnGoTo.setOnClickListener {
            val selectedLocation = locationSpinner.selectedItem?.toString()
            if (selectedLocation != null) {
                val command = JsonObject().apply {
                    addProperty("action", "goto")
                    addProperty("location", selectedLocation)
                }
                mqttManager.publishCommand(command.toString())
                statusText.text = "Sent: Go to $selectedLocation"
            }
        }

        btnStop.setOnClickListener {
            val command = JsonObject().apply {
                addProperty("action", "stop")
            }
            mqttManager.publishCommand(command.toString())
            statusText.text = "Sent: Stop"
        }

        btnRefreshLocations.setOnClickListener {
            val command = JsonObject().apply {
                addProperty("action", "get_locations")
            }
            mqttManager.publishCommand(command.toString())
            statusText.text = "Requesting locations..."
        }

        btnSpeak.setOnClickListener {
            val text = editSpeak.text.toString().trim()
            if (text.isNotEmpty()) {
                val command = JsonObject().apply {
                    addProperty("action", "speak")
                    addProperty("text", text)
                }
                mqttManager.publishCommand(command.toString())
                statusText.text = "Sent: Speak '$text'"
                editSpeak.text.clear()
            }
        }
    }

    private fun updateLocationSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        locationSpinner.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect()
    }
}
