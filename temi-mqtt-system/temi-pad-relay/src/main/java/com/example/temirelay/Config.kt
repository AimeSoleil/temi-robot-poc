package com.example.temirelay

object Config {
    // MQTT broker settings
    const val MQTT_BROKER_URL = "tcp://10.6.40.84:1884"
    const val MQTT_USERNAME = "temi"
    const val MQTT_PASSWORD = "temi2026"
    const val MQTT_CLIENT_ID = "temi-pad-relay"

    // MQTT topics
    const val TOPIC_COMMAND = "temi/command"
    const val TOPIC_STATUS = "temi/status"

    // ── Coordinate mapping ───────────────────────────────────────────
    // Whether to use HK1980 grid coordinates (hkE, hkN) for mapping.
    // If false, raw latitude/longitude will be used (not recommended).
    const val USE_HK1980_MAPPING = true

    // Default yaw for repose when direction is not available
    const val DEFAULT_YAW = 0f
}
