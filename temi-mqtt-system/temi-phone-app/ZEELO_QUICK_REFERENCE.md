# Zeelo SDK Quick Reference - Code Snippets

## 1. Basic Initialization (in Activity)

```kotlin
private lateinit var locationApiClient: LocationApiClient

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    
    // Initialize with callback
    locationApiClient = LocationApiClient(this)
    locationApiClient.initializeZeeloSDK(createLocationCallback())
}

private fun createLocationCallback() = object : LocationApiClient.LocationCallback {
    override fun onLocationUpdated(
        location: Location?,
        gpsLocation: GPSLocation?,
        source: LocationSource?
    ) {
        location?.let {
            Log.d("TAG", "Lat: ${it.latitude}, Lon: ${it.longitude}")
        }
    }

    override fun onLocationsReceived(locations: List<String>) {
        // Handle location list
    }

    override fun onZoneIn(geofenceId: String, geofenceName: String) {
        Toast.makeText(this@YourActivity, "Entered: $geofenceName", Toast.LENGTH_SHORT).show()
    }

    override fun onZoneOut(geofenceId: String, geofenceName: String) {
        Toast.makeText(this@YourActivity, "Exited: $geofenceName", Toast.LENGTH_SHORT).show()
    }

    override fun onDirectionUpdate(direction: Double) {
        Log.d("TAG", "Heading: $direction°")
    }

    override fun onError(error: String) {
        Log.e("TAG", "Error: $error")
    }
}

override fun onDestroy() {
    super.onDestroy()
    locationApiClient.stopLocationService()
}
```

## 2. Get Current Location

```kotlin
// Get precise location
val location = locationApiClient.getCurrentLocation()
location?.let {
    val latitude = it.latitude
    val longitude = it.longitude
    val floor = it.floorLevel
    val zone = it.geofenceName
    val inCoverage = it.coverageArea
}

// Get GPS fallback
val gpsLocation = locationApiClient.getCurrentGpsLocation()
gpsLocation?.let {
    val accuracy = it.horizontalAccuracy
}

// Get device heading
val direction = locationApiClient.getCurrentDirection()

// Get current geofence
val (zoneId, zoneName) = locationApiClient.getCurrentGeofence()
```

## 3. Export Location as JSON

```kotlin
// Get full location data as JSON
val locationJson = locationApiClient.exportCurrentLocationAsJson()

// Use with MQTT
val command = JsonObject().apply {
    addProperty("action", "update_location")
    add("data", gson.fromJson(locationJson, JsonObject::class.java))
}
mqttManager.publish("temi/location", command.toString())
```

## 4. Parse MQTT Location

```kotlin
// Legacy method - parse locations from MQTT message
val json = """
{
    "locations": ["Room 101", "Room 102", "Conference Room"]
}
"""
val locations = locationApiClient.parseLocationsFromMqtt(json)
// Result: ["Room 101", "Room 102", "Conference Room"]
```

## 5. Required Permissions (AndroidManifest.xml)

```xml
<!-- Location Access -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Network for Zeelo -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Sensors for Accuracy -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
```

## 6. Request Runtime Permissions

```kotlin
private val locationPermissions = arrayOf(
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION
)

private fun requestLocationPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        requestPermissions(locationPermissions, REQUEST_CODE_LOCATION)
    }
}

override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    
    if (requestCode == REQUEST_CODE_LOCATION) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted - initialize SDK
            locationApiClient.initializeZeeloSDK(callback)
        }
    }
}
```

## 7. Handle Location Updates with UI

```kotlin
private fun setupLocationUI() {
    locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
        override fun onLocationUpdated(
            location: Location?,
            gpsLocation: GPSLocation?,
            source: LocationSource?
        ) {
            runOnUiThread {
                location?.let {
                    val info = buildString {
                        appendLine("📍 Location Update")
                        appendLine("Latitude: ${String.format("%.6f", it.latitude)}")
                        appendLine("Longitude: ${String.format("%.6f", it.longitude)}")
                        appendLine("Floor: ${it.floorLevel}")
                        appendLine("Zone: ${it.geofenceName}")
                        appendLine("Accuracy: ${if (it.coverageArea) "High (Indoor)" else "Low (GPS)"}")
                    }
                    locationTextView.text = info
                }
            }
        }

        override fun onZoneIn(geofenceId: String, geofenceName: String) {
            runOnUiThread {
                zoneStatusTextView.text = "✅ In Zone: $geofenceName"
                zoneStatusTextView.setTextColor(Color.GREEN)
            }
        }

        override fun onZoneOut(geofenceId: String, geofenceName: String) {
            runOnUiThread {
                zoneStatusTextView.text = "❌ Left Zone: $geofenceName"
                zoneStatusTextView.setTextColor(Color.RED)
            }
        }

        override fun onDirectionUpdate(direction: Double) {
            runOnUiThread {
                directionTextView.text = String.format("🧭 Heading: %.0f°", direction)
            }
        }

        override fun onError(error: String) {
            runOnUiThread {
                Toast.makeText(this@YourActivity, "Error: $error", Toast.LENGTH_LONG).show()
            }
        }

        override fun onLocationsReceived(locations: List<String>) {}
    })
}
```

## 8. Advanced: Direct SDK Access

```kotlin
// If you need direct ZeeloLocationManager access
import zeelo.location.manager.ZeeloLocationManager

val manager = ZeeloLocationManager.getInstance(this)

// Enable HK1980 before other calls
manager.enableHK80(true)

// Set location listener
manager.setLocationListener(object : ZeeloLocationCallback {
    override fun onUpdateLocation(location: Location?, gps: GPSLocation?, source: LocationSource?) {
        // Handle update
    }
})

// Set zone listener
manager.setLocationZoneListener(object : ZeeloLocationZoneCallback {
    override fun onZoneIn(id: String?, name: String?) {
        // Handle zone entry
    }

    override fun onZoneOut(id: String?, name: String?) {
        // Handle zone exit
    }
})

// Set direction listener
manager.setZeeloLocationDirectionCallback(object : ZeeloLocationDirectionCallback {
    override fun onDirectionUpdate(direction: Double) {
        // Handle direction
    }
})

// Start service
manager.startLocationService()

// Later - stop service
manager.stopLocationService()
```

## 9. Gradle Dependencies

```gradle
android {
    compileSdk 34
    // ... other config
}

dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    
    // Zeelo Location SDK
    implementation files('libs/zeelo_location_prod_2.2.0.aar')
    
    // Zeelo Dependencies
    implementation 'org.greenrobot:eventbus:3.3.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.0.1'
    
    // Other libraries
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```

## 10. Integration Testing

```kotlin
class LocationIntegrationTest {
    @Test
    fun testLocationInitialization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = LocationApiClient(context)
        
        var locationReceived = false
        client.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
            override fun onLocationUpdated(location: Location?, gps: GPSLocation?, source: LocationSource?) {
                locationReceived = location != null
            }
            // ... implement other callbacks
        })
        
        // Wait for location update (max 10 seconds)
        Thread.sleep(10000)
        
        assert(locationReceived) { "Location should be received" }
        client.stopLocationService()
    }
}
```

## 11. Location Model Fields Reference

```kotlin
// Location object properties
location.latitude           // WGS84 latitude (double)
location.longitude          // WGS84 longitude (double)
location.hkE               // Hong Kong 1980 Easting (double)
location.hkN               // Hong Kong 1980 Northing (double)
location.floorLevel        // Floor number (int)
location.isOutDoor         // Is outdoor? (boolean)
location.geofenceName      // Zone/Building name (String)
location.geofenceId        // Zone ID UUID (String)
location.floorName         // Floor readable name (String)
location.coverageArea      // In coverage area? (boolean)

// GPS location properties
gpsLocation.latitude            // GPS latitude (double)
gpsLocation.longitude           // GPS longitude (double)
gpsLocation.horizontalAccuracy  // GPS accuracy in meters (double)
gpsLocation.direction           // GPS heading 0-360 (double)
gpsLocation.directionAccuracy   // Heading accuracy (double)
gpsLocation.floorLevel          // Always 0 for GPS (int)

// Location source
source.source               // "GPS" or "LocationEngine" (String)
```

## 12. Error Handling Pattern

```kotlin
locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
    override fun onError(error: String) {
        when {
            error.contains("Initialize", ignoreCase = true) -> {
                // SDK initialization failed
                Log.e("Location", "Failed to initialize SDK")
            }
            error.contains("Network", ignoreCase = true) -> {
                // Network connectivity issue
                Log.e("Location", "Network error - check connectivity")
            }
            error.contains("Permission", ignoreCase = true) -> {
                // Permission not granted
                Log.e("Location", "Location permission not granted")
            }
            else -> {
                Log.e("Location", "Unknown error: $error")
            }
        }
    }
    
    // ... other callbacks
})
```

---

## Common Patterns

### Pattern 1: Continuous Location Publishing
```kotlin
private fun startLocationPublishing() {
    locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
        override fun onLocationUpdated(location: Location?, gps: GPSLocation?, source: LocationSource?) {
            val json = locationApiClient.exportCurrentLocationAsJson()
            mqttManager.publish("device/location", json)
        }
        
        override fun onLocationsReceived(locations: List<String>) {}
        override fun onZoneIn(geofenceId: String, geofenceName: String) {}
        override fun onZoneOut(geofenceId: String, geofenceName: String) {}
        override fun onDirectionUpdate(direction: Double) {}
        override fun onError(error: String) {
            Log.e("Publish", error)
        }
    })
}
```

### Pattern 2: Zone-Based Navigation
```kotlin
private fun setupZoneNavigation() {
    locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
        override fun onZoneIn(geofenceId: String, geofenceName: String) {
            // Robot in target zone
            startRobotTask(geofenceName)
        }
        
        override fun onZoneOut(geofenceId: String, geofenceName: String) {
            // Robot left zone
            stopRobotTask()
        }
        
        override fun onLocationsReceived(locations: List<String>) {}
        override fun onLocationUpdated(location: Location?, gps: GPSLocation?, source: LocationSource?) {}
        override fun onDirectionUpdate(direction: Double) {}
        override fun onError(error: String) {}
    })
}
```

### Pattern 3: Navigation Heading
```kotlin
private fun setupCompassNavigation() {
    locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
        override fun onDirectionUpdate(direction: Double) {
            // direction: 0-360 degrees
            // 0 = North, 90 = East, 180 = South, 270 = West
            updateCompassUI(direction)
            
            if (direction > 315 || direction < 45) {
                robotNavigate("NORTH")
            } else if (direction >= 45 && direction < 135) {
                robotNavigate("EAST")
            } else if (direction >= 135 && direction < 225) {
                robotNavigate("SOUTH")
            } else {
                robotNavigate("WEST")
            }
        }
        
        override fun onLocationsReceived(locations: List<String>) {}
        override fun onLocationUpdated(location: Location?, gps: GPSLocation?, source: LocationSource?) {}
        override fun onZoneIn(geofenceId: String, geofenceName: String) {}
        override fun onZoneOut(geofenceId: String, geofenceName: String) {}
        override fun onError(error: String) {}
    })
}
```

---

**Quick Start:** Copy the "Basic Initialization" snippet to get started immediately!
