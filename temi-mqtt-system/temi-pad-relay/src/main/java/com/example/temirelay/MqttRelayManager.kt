package com.example.temirelay

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttRelayManager(private val context: Context) {

    private var mqttClient: MqttAsyncClient? = null
    private var messageListener: ((String, String) -> Unit)? = null
    private var connectionListener: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "MqttRelayManager"
    }

    fun setMessageListener(listener: (topic: String, message: String) -> Unit) {
        messageListener = listener
    }

    fun setConnectionListener(listener: (connected: Boolean) -> Unit) {
        connectionListener = listener
    }

    fun connect() {
        try {
            mqttClient = MqttAsyncClient(
                Config.MQTT_BROKER_URL,
                Config.MQTT_CLIENT_ID,
                MemoryPersistence()
            )

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                userName = Config.MQTT_USERNAME
                password = Config.MQTT_PASSWORD.toCharArray()
                isAutomaticReconnect = true
                connectionTimeout = 10
                keepAliveInterval = 20
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "Connected to MQTT broker: $serverURI (reconnect=$reconnect)")
                    connectionListener?.invoke(true)
                    subscribeToCommands()
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "Connection lost", cause)
                    connectionListener?.invoke(false)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: return
                    val t = topic ?: return
                    Log.d(TAG, "Message received on $t: $payload")
                    messageListener?.invoke(t, payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "MQTT connection successful")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT connection failed", exception)
                    connectionListener?.invoke(false)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MQTT client", e)
        }
    }

    private fun subscribeToCommands() {
        try {
            mqttClient?.subscribe(Config.TOPIC_COMMAND, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to ${Config.TOPIC_COMMAND}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to subscribe to ${Config.TOPIC_COMMAND}", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing", e)
        }
    }

    fun publish(topic: String, message: String) {
        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                qos = 1
                isRetained = false
            }
            mqttClient?.publish(topic, mqttMessage)
            Log.d(TAG, "Published to $topic: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing message", e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
            Log.d(TAG, "Disconnected from MQTT broker")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }

    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }
}
