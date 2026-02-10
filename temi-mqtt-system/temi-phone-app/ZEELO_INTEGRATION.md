# Zeelo Location SDK Integration Guide

## Overview

The Temi Phone App has been updated to integrate the **Zeelo Location SDK** for precise indoor location tracking and geofence detection. This guide explains the integration, APIs, and how to use them.

## Zeelo SDK Version

- **SDK Version:** 2.2.0
- **AAR File:** `zeelo_location_prod_2.2.0.aar`
- **Minimum API Level:** 24 (Android 7.0)
- **Required Sensors:** Gyroscope and Magnetometer

## Key Features

✅ **Precise Indoor Positioning** - Fingerprinting-based location within coverage areas  
✅ **Geofence Detection** - Zone entry/exit callbacks  
✅ **Direction Tracking** - Device heading information (0-360°)  
✅ **Hybrid Mode** - Falls back to GPS in non-coverage areas  
✅ **Hong Kong 1980 Grid System** - HK80 coordinate support  

## Architecture

### Core Components

#### 1. **LocationApiClient.kt**
Wrapper class that manages Zeelo SDK initialization and lifecycle.

**Key Methods:**
- `initializeZeeloSDK(callback)` - Initialize SDK with location listener
- `startLocationService()` - Start location tracking
- `stopLocationService()` - Stop location tracking
- `getCurrentLocation()` - Get current location data
- `getCurrentDirection()` - Get device heading
- `exportCurrentLocationAsJson()` - Export location as JSON for MQTT

**Callbacks:**
```kotlin
interface LocationCallback {
    fun onLocationsReceived(locations: List<String>)
    fun onLocationUpdated(location: Location?, gpsLocation: GPSLocation?, source: LocationSource?)
    fun onZoneIn(geofenceId: String, geofenceName: String)
    fun onZoneOut(geofenceId: String, geofenceName: String)
    fun onDirectionUpdate(direction: Double)
    fun onError(error: String)
}
```

#### 2. **ControllerActivity.kt**
Main UI activity with Zeelo SDK initialization.

**Setup Process:**
1. Initialize views and MQTT
2. Call `initializeZeeloLocationSDK()`
3. SDK starts location service automatically
4. Receives location updates via callbacks

## SDK API Reference

### Initialization

```kotlin
// Get singleton instance
val locationManager = ZeeloLocationManager.getInstance(context)

// Enable Hong Kong 1980 Grid System (optional)
locationManager.enableHK80(true)

// Set location callback
locationManager.setLocationListener(object : ZeeloLocationCallback {
    override fun onUpdateLocation(
        location: Location?,
        gpsLocation: GPSLocation?,
        locationSource: LocationSource?
    ) {
        // Handle location update
    }
})

// Start service
locationManager.startLocationService()
```

### Location Listener

```kotlin
locationManager.setLocationListener(object : ZeeloLocationCallback {
    override fun onUpdateLocation(
        location: Location?,
        gpsLocation: GPSLocation?,
        locationSource: LocationSource?
    ) {
        // Location object properties:
        location?.let {
            val lat = it.latitude          // WGS84 latitude
            val lon = it.longitude         // WGS84 longitude
            val hkE = it.hkE              // HK1980 Easting
            val hkN = it.hkN              // HK1980 Northing
            val floor = it.floorLevel     // Floor number (e.g., 7)
            val outdoor = it.isOutDoor    // Boolean
            val zone = it.geofenceName    // Zone/Geofence name
            val zoneId = it.geofenceId    // Zone ID
            val coverage = it.coverageArea // In coverage area?
        }
        
        // GPS location for fallback
        gpsLocation?.let {
            val accuracy = it.horizontalAccuracy
            val direction = it.direction
            val dirAccuracy = it.directionAccuracy
        }
        
        // Source indicates GPS or LocationEngine
        val source = locationSource?.source
    }
})
```

### Zone/Geofence Listener

```kotlin
locationManager.setLocationZoneListener(object : ZeeloLocationZoneCallback {
    override fun onZoneIn(geofenceId: String?, geofenceName: String?) {
        // User entered zone
        Log.d("Zone", "Entered: $geofenceName")
    }
    
    override fun onZoneOut(geofenceId: String?, geofenceName: String?) {
        // User exited zone
        Log.d("Zone", "Exited: $geofenceName")
    }
})
```

### Direction Listener

```kotlin
locationManager.setZeeloLocationDirectionCallback(object : ZeeloLocationDirectionCallback {
    override fun onDirectionUpdate(direction: Double) {
        // Direction 0-360 degrees
        // 0° = North, 90° = East, 180° = South, 270° = West
        Log.d("Direction", "Heading: $direction°")
    }
})
```

### Service Control

```kotlin
// Start tracking
locationManager.startLocationService()

// Stop tracking
locationManager.stopLocationService()
```

## Data Models

### Location
```json
{
  "location": {
    "latitude": 22.250763,
    "longitude": 113.565228,
    "hkE": 773218.8,
    "hkN": 812570.9,
    "floorLevel": 7,
    "isOutDoor": false,
    "geofenceName": "BLDG0001",
    "geofenceId": "4a0efbe0-05cb-11ee-850e-b18ae57d88fb",
    "floorName": "70",
    "coverageArea": true
  },
  "gpsLocation": {
    "latitude": 22.250763,
    "longitude": 113.565228,
    "hkE": 773218.8,
    "hkN": 812570.9,
    "floorLevel": 0,
    "horizontalAccuracy": 18.6,
    "direction": 54.9,
    "directionAccuracy": 10.95
  },
  "locationSource": "LocationEngine",
  "direction": 54.9,
  "timestamp": 1697500000000
}
```

## Setup Instructions

### 1. Verify AAR File

The Zeelo SDK AAR must be in `libs/` directory:
```bash
cd temi-mqtt-system/temi-phone-app
ls -la zeelo_location_prod_2.2.0.aar
```

### 2. Build Configuration

The `build.gradle` includes:
```gradle
implementation files('libs/zeelo_location_prod_2.2.0.aar')
implementation 'org.greenrobot:eventbus:3.3.1'
```

### 3. Required Permissions

Add to `AndroidManifest.xml`:
```xml
<!-- Location permissions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Sensors (for Zeelo accuracy) -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
```

### 4. Runtime Permissions

Request permissions at runtime (API 23+):
```kotlin
private fun requestLocationPermissions() {
    val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
}
```

### 5. Initialize in Activity

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_controller)
    
    // Request permissions first
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        requestLocationPermissions()
    }
    
    // Initialize Zeelo SDK
    initializeZeeloLocationSDK()
}
```

## Important Notes

### ⚠️ Sensor Requirements
- **Gyroscope & Magnetometer Required** - Devices without these sensors are not supported
- Example unsupported: Redmi 10C (no gyroscope)

### ⏱️ Accuracy Timing
- Wait ~5 seconds after starting the service for accurate location data
- Longer duration with stationary position = more accuracy

### 📍 Coverage Areas
- **In Coverage Area:** Uses Zeelo's fingerprinting engine for ~1-5m accuracy
- **Outside Coverage:** Falls back to GPS (~10-30m accuracy)
- `coverageArea` field indicates if location is in fingerprinted area

### 🎯 Floor Level Stability
- Floor level information may be unstable
- Subject to change, especially during transitions

### 🌐 Network Requirement
- Mobile network must be available before calling location APIs
- Zeelo requires connectivity for positioning data

## Location Updates via MQTT

When location updates occur, the app publishes to MQTT:

```json
{
  "action": "update_location",
  "location_data": {
    "location": { ... },
    "gpsLocation": { ... },
    "direction": 54.9,
    "locationSource": "LocationEngine",
    "timestamp": 1697500000000
  }
}
```

The relay app (on Temi robot) can subscribe and use this data.

## Troubleshooting

### No Location Updates
- **Check:** Network connectivity
- **Check:** Permissions granted at runtime
- **Check:** Device has Gyroscope & Magnetometer
- **Check:** Waited 5 seconds after service start

### "Unknown location source"
- May indicate GPS-only mode (outside coverage)
- Check `locationSource` field - should be "GPS" or "LocationEngine"

### High Accuracy Variance
- Floor level may be unstable in transitional areas
- Fingerprint coverage may have gaps
- GPS accuracy depends on signal strength

### Service Not Starting
- Verify `AndroidManifest.xml` has required permissions
- Ensure runtime permissions requested
- Check logcat: `adb logcat | grep LocationApiClient`

## References

- **Zeelo SDK Version:** 2.1.0+
- **API Documentation:** See PDF specifications
- **Event Bus:** Used internally by Zeelo SDK for callbacks
- **Coverage Areas:** Configured server-side by Zeelo

## Example Usage

### Basic Setup
```kotlin
class ControllerActivity : AppCompatActivity() {
    private lateinit var locationApiClient: LocationApiClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        locationApiClient = LocationApiClient(this)
        locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
            override fun onLocationUpdated(location: Location?, gps: GPSLocation?, source: LocationSource?) {
                location?.let {
                    Log.d("Location", "Lat: ${it.latitude}, Lon: ${it.longitude}")
                }
            }
            
            override fun onZoneIn(geofenceId: String, geofenceName: String) {
                Log.d("Zone", "Entered $geofenceName")
            }
            
            override fun onError(error: String) {
                Log.e("Location", error)
            }
            
            // Implement other callbacks...
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationApiClient.stopLocationService()
    }
}
```

## License & Attribution

- **Zeelo Location SDK** - Proprietary (Zeelo)
- **EventBus** - Licensed under Apache License 2.0
