package com.example.temiphone

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager(private val context: Context) {

    private var mqttClient: MqttAsyncClient? = null
    private var messageListener: ((String, String) -> Unit)? = null
    private var connectionListener: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "MqttManager"
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
                    subscribeToTopics()
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

    private fun subscribeToTopics() {
        try {
            val topics = arrayOf(Config.TOPIC_STATUS, Config.TOPIC_LOCATION)
            val qos = intArrayOf(1, 1)
            mqttClient?.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to status and location topics")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to subscribe", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing", e)
        }
    }

    fun publishCommand(command: String) {
        try {
            val mqttMessage = MqttMessage(command.toByteArray()).apply {
                qos = 1
                isRetained = false
            }
            mqttClient?.publish(Config.TOPIC_COMMAND, mqttMessage)
            Log.d(TAG, "Published command: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing command", e)
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
