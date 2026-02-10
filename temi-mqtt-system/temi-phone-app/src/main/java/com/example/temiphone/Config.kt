package com.example.temiphone

object Config {
    // MQTT broker settings
    const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
    const val MQTT_USERNAME = "temi"
    const val MQTT_PASSWORD = "temi2026"
    const val MQTT_CLIENT_ID = "temi-phone-controller"

    // MQTT topics
    const val TOPIC_COMMAND = "temi/command"
    const val TOPIC_STATUS = "temi/status"
    const val TOPIC_LOCATION = "temi/location"
}
