package com.example.temiphone

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.cherrypicks.zeelosdk.lite.location.ZeeloLocationCallback
import com.cherrypicks.zeelosdk.lite.location.ZeeloLocationDirectionCallback
import com.cherrypicks.zeelosdk.lite.location.ZeeloLocationZoneCallback
import com.cherrypicks.zeelosdk.lite.location.model.GPSLocation
import com.cherrypicks.zeelosdk.lite.location.model.Location
import com.cherrypicks.zeelosdk.lite.location.model.Source
import com.cherrypicks.zeelosdk.lite.location.ZeeloLocationManager

class LocationApiClient(private val context: Context) {

    private val gson = Gson()
    private var locationManager: ZeeloLocationManager? = null
    private var locationCallback: LocationCallback? = null
    
    // Current location state
    private var currentLocation: Location? = null
    private var currentGpsLocation: GPSLocation? = null
    private var currentLocationSource: Source? = null
    private var currentDirection: Double = 0.0
    private var currentGeofenceId: String = ""
    private var currentGeofenceName: String = ""

    companion object {
        private const val TAG = "LocationApiClient"
    }

    interface LocationCallback {
        fun onLocationUpdated(location: Location?, gpsLocation: GPSLocation?, source: Source?)
        fun onError(error: String)
    }

    /**
     * Initialize and start the Zeelo Location SDK
     * Must be called before requesting location data
     */
    fun initializeZeeloSDK(callback: LocationCallback? = null) {
        try {
            locationCallback = callback
            locationManager = ZeeloLocationManager.getInstance(context)
            
            // Enable Hong Kong 1980 Grid System if needed
            locationManager?.enableHK80(true)
            Log.d(TAG, "Zeelo SDK initialized and HK1980 enabled")
            
            // Setup location listener
            setupLocationListener()
            
            // Setup zone listener
            setupZoneListener()
            
            // Setup direction listener
            setupDirectionListener()
            
            // Start location service
            startLocationService()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Zeelo SDK", e)
            locationCallback?.onError("Failed to initialize Zeelo SDK: ${e.message}")
        }
    }

    /**
     * Setup the main location listener for receiving location updates
     */
    private fun setupLocationListener() {
        try {
            locationManager?.setLocationListener(object : ZeeloLocationCallback {
                override fun onUpdateLocation(
                    location: Location?,
                    gpsLocation: GPSLocation?,
                    locationSource: Source?
                ) {
                    try {
                        currentLocation = location
                        currentGpsLocation = gpsLocation
                        currentLocationSource = locationSource
                        
                        // Log the active source and its resolved coordinates
                        val srcName = locationSource?.toString() ?: "Unknown"
                        val isIndoor = srcName == "LocationEngine"
                        val activeLat = if (isIndoor) location?.latitude else gpsLocation?.latitude
                        val activeLon = if (isIndoor) location?.longitude else gpsLocation?.longitude
                        val activeHkE = if (isIndoor) location?.hkE else gpsLocation?.hkE
                        val activeHkN = if (isIndoor) location?.hkN else gpsLocation?.hkN
                        
                        Log.d(TAG, "Location updated [source=$srcName]: " +
                            "lat=$activeLat, lon=$activeLon, " +
                            "hkE=$activeHkE, hkN=$activeHkN, " +
                            "floor=${if (isIndoor) location?.floorLevel else gpsLocation?.floorlevel}, " +
                            "geofence=${location?.geofenceName}")
                        
                        // Notify callback
                        locationCallback?.onLocationUpdated(location, gpsLocation, locationSource)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing location update", e)
                        locationCallback?.onError("Error processing location: ${e.message}")
                    }
                }
            })
            Log.d(TAG, "Location listener setup successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up location listener", e)
            locationCallback?.onError("Failed to setup location listener: ${e.message}")
        }
    }

    /**
     * Setup geofence zone listener for zone entry/exit events
     */
    private fun setupZoneListener() {
        try {
            locationManager?.setLocationZoneListener(object : ZeeloLocationZoneCallback {
                override fun onZoneIn(geofenceId: String?, geofenceName: String?) {
                    currentGeofenceId = geofenceId ?: ""
                    currentGeofenceName = geofenceName ?: ""
                    Log.d(TAG, "Zone In: $geofenceName (ID: $geofenceId)")
                }

                override fun onZoneOut(geofenceId: String?, geofenceName: String?) {
                    Log.d(TAG, "Zone Out: $geofenceName (ID: $geofenceId)")
                }
            })
            Log.d(TAG, "Zone listener setup successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up zone listener", e)
            locationCallback?.onError("Failed to setup zone listener: ${e.message}")
        }
    }

    /**
     * Setup direction listener for device heading updates
     */
    private fun setupDirectionListener() {
        try {
            locationManager?.setZeeloLocationDirectionCallback(object : ZeeloLocationDirectionCallback {
                override fun onDirectionUpdate(direction: Double) {
                    currentDirection = direction
                    Log.d(TAG, "Direction updated: $direction degrees")
                }
            })
            Log.d(TAG, "Direction listener setup successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up direction listener", e)
            locationCallback?.onError("Failed to setup direction listener: ${e.message}")
        }
    }

    /**
     * Start the Zeelo location service
     * Call after initialization and setting up listeners
     */
    private fun startLocationService() {
        try {
            locationManager?.startLocationService()
            Log.d(TAG, "Location service started. Waiting ~5 seconds for accurate data...")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location service", e)
            locationCallback?.onError("Failed to start location service: ${e.message}")
        }
    }

    /**
     * Stop the Zeelo location service
     */
    fun stopLocationService() {
        try {
            locationManager?.stopLocationService()
            Log.d(TAG, "Location service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location service", e)
        }
    }

    /**
     * Get the current location data
     */
    fun getCurrentLocation(): Location? = currentLocation

    /**
     * Get the current GPS location data
     */
    fun getCurrentGpsLocation(): GPSLocation? = currentGpsLocation

    /**
     * Get the current direction
     */
    fun getCurrentDirection(): Double = currentDirection

    /**
     * Get current geofence information
     */
    fun getCurrentGeofence(): Pair<String, String> = Pair(currentGeofenceId, currentGeofenceName)

    /**
     * Get the current location source string.
     * "LocationEngine" = Zeelo indoor, "GPS" = GPS fallback.
     */
    fun getLocationSourceName(): String {
        return currentLocationSource?.toString() ?: "Unknown"
    }

    /**
     * Whether the current positioning comes from Zeelo's indoor engine
     * (as opposed to raw GPS fallback).
     */
    fun isIndoorSource(): Boolean {
        return getLocationSourceName() == "LocationEngine"
    }

    // ────────────────────── active-location helpers ────────────────────

    /**
     * Resolved latitude from the active source.
     * LocationEngine → [currentLocation], GPS → [currentGpsLocation].
     */
    fun getActiveLatitude(): Double? =
        if (isIndoorSource()) currentLocation?.latitude else currentGpsLocation?.latitude

    fun getActiveLongitude(): Double? =
        if (isIndoorSource()) currentLocation?.longitude else currentGpsLocation?.longitude

    fun getActiveHkE(): Double? =
        if (isIndoorSource()) currentLocation?.hkE else currentGpsLocation?.hkE

    fun getActiveHkN(): Double? =
        if (isIndoorSource()) currentLocation?.hkN else currentGpsLocation?.hkN

    fun getActiveFloorLevel(): Int? =
        if (isIndoorSource()) currentLocation?.floorLevel else currentGpsLocation?.floorlevel?.toInt()

    // ────────────────────── JSON export ────────────────────────────────

    /**
     * Export the **active** location as a flat JSON ready for MQTT.
     *
     * The `location` key always contains the resolved position
     * (from either [currentLocation] or [currentGpsLocation] depending
     * on [currentLocationSource]).
     *
     * Structure:
     * ```
     * {
     *   "location": { lat, lon, hkE, hkN, floor, geofence, ... },
     *   "gpsLocation": { ... },           // raw GPS object (if available)
     *   "locationSource": "LocationEngine" | "GPS",
     *   "direction": 54.96,
     *   "timestamp": ...
     * }
     * ```
     */
    fun exportCurrentLocationAsJson(): String {
        return try {
            val sourceName = getLocationSourceName()
            val isIndoor = isIndoorSource()

            val locationJson = JsonObject().apply {
                // ── "location" = resolved active position ──
                add("location", JsonObject().apply {
                    if (isIndoor && currentLocation != null) {
                        addProperty("latitude", currentLocation?.latitude)
                        addProperty("longitude", currentLocation?.longitude)
                        addProperty("hkE", currentLocation?.hkE)
                        addProperty("hkN", currentLocation?.hkN)
                        addProperty("floorLevel", currentLocation?.floorLevel)
                        addProperty("isOutDoor", currentLocation?.isOutDoor)
                        addProperty("geofenceName", currentLocation?.geofenceName)
                        addProperty("geofenceId", currentLocation?.geofenceId)
                        addProperty("floorName", currentLocation?.floorName)
                        addProperty("coverageArea", currentLocation?.coverageArea)
                    } else if (currentGpsLocation != null) {
                        // GPS fallback – map GPSLocation fields into the same shape
                        addProperty("latitude", currentGpsLocation?.latitude)
                        addProperty("longitude", currentGpsLocation?.longitude)
                        addProperty("hkE", currentGpsLocation?.hkE)
                        addProperty("hkN", currentGpsLocation?.hkN)
                        addProperty("floorLevel", currentGpsLocation?.floorlevel)
                        addProperty("isOutDoor", true)
                        addProperty("geofenceName", "")
                        addProperty("geofenceId", "")
                        addProperty("floorName", "")
                        addProperty("coverageArea", false)
                    }
                })

                // ── raw gpsLocation (always include if available) ──
                if (currentGpsLocation != null) {
                    add("gpsLocation", JsonObject().apply {
                        addProperty("latitude", currentGpsLocation?.latitude)
                        addProperty("longitude", currentGpsLocation?.longitude)
                        addProperty("hkE", currentGpsLocation?.hkE)
                        addProperty("hkN", currentGpsLocation?.hkN)
                        addProperty("floorLevel", currentGpsLocation?.floorlevel)
                        addProperty("horizontalAccuracy", currentGpsLocation?.horizontalAccuracy)
                        addProperty("direction", currentGpsLocation?.direction)
                        addProperty("directionAccuracy", currentGpsLocation?.directionAccuracy)
                    })
                }

                addProperty("locationSource", sourceName)
                addProperty("direction", currentDirection)
                addProperty("timestamp", System.currentTimeMillis())
            }

            Log.d(TAG, "Exported location (source=$sourceName): lat=${getActiveLatitude()}, " +
                "lon=${getActiveLongitude()}, hkE=${getActiveHkE()}, hkN=${getActiveHkN()}")

            locationJson.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting location as JSON", e)
            "{}"
        }
    }

}
