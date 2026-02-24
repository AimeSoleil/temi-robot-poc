package com.example.temiphone

object Config {
    // MQTT broker settings
    const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1884"
    const val MQTT_USERNAME = "temi"
    const val MQTT_PASSWORD = "temi2026"
    const val MQTT_CLIENT_ID = "temi-phone-controller"

    // MQTT topics
    const val TOPIC_COMMAND = "temi/command"
    const val TOPIC_STATUS = "temi/status"
    const val TOPIC_LOCATION = "temi/location"
    const val TOPIC_ZEELO_LOCATION = "temi/zeelo_location"  // Zeelo SDK phone location updates
    
    // Zeelo Location SDK settings
    // The API key is configured in AndroidManifest.xml:
    // <meta-data android:name="com.cherrypicks.zeelo.sdk.api_key" android:value="YOUR_API_KEY" />
    const val ZEELO_ENABLE_HK1980 = true
    const val ZEELO_MIN_PUBLISH_INTERVAL_MS = 10_000L  // Minimum interval between MQTT publishes (ms). SDK callback-driven, this acts as a throttle.
}
