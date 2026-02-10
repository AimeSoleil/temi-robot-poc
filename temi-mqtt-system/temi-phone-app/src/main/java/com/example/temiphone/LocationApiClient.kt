package com.example.temiphone

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import okhttp3.*
import java.io.IOException

class LocationApiClient {

    private val client = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val TAG = "LocationApiClient"
    }

    interface LocationCallback {
        fun onLocationsReceived(locations: List<String>)
        fun onError(error: String)
    }

    fun parseLocationsFromMqtt(json: String): List<String> {
        return try {
            val jsonObject = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val locationsArray = jsonObject.getAsJsonArray("locations")
            locationsArray?.map { it.asString } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing locations", e)
            emptyList()
        }
    }
}
