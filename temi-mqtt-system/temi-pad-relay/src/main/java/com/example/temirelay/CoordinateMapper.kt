package com.example.temirelay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.robotemi.sdk.Position
import kotlin.math.*

/**
 * Maps Zeelo HK1980 grid coordinates (hkE, hkN) to temi map coordinates (x, y).
 *
 * ## Why this is needed
 * - Zeelo SDK reports position in **HK1980 Grid** (hkE / hkN) – units are metres,
 *   but the origin is the Hong Kong 1980 datum and axes point East / North.
 * - temi SDK's [Position] uses an **internal map frame** whose origin is the
 *   home-base (charging dock) with its own orientation – also in metres.
 *
 * A simple **2-D affine transform** (translation + rotation + uniform scale)
 * maps one frame to the other. We compute the transform from **two calibration
 * anchor points** whose coordinates are known in *both* frames.
 *
 * ## Calibration procedure
 * 1. Drive the robot to a known spot (Anchor A).
 * 2. Record the temi Position (x, y) and the Zeelo location (hkE, hkN) at that spot.
 * 3. Repeat at a second spot (Anchor B), ideally several metres away.
 * 4. Call [calibrate] with both anchors. The transform is saved to SharedPreferences.
 *
 * After calibration, call [toTemiPosition] for every incoming Zeelo location.
 */
class CoordinateMapper(context: Context) {

    companion object {
        private const val TAG = "CoordinateMapper"
        private const val PREFS_NAME = "coordinate_mapper"

        // SharedPreferences keys
        private const val KEY_CALIBRATED = "calibrated"
        private const val KEY_SCALE = "scale"
        private const val KEY_ROTATION = "rotation"       // radians
        private const val KEY_OFFSET_X = "offset_x"
        private const val KEY_OFFSET_Y = "offset_y"

        // Anchor persistence keys
        private const val KEY_ANCHOR_A_HKE = "anchor_a_hke"
        private const val KEY_ANCHOR_A_HKN = "anchor_a_hkn"
        private const val KEY_ANCHOR_A_X = "anchor_a_x"
        private const val KEY_ANCHOR_A_Y = "anchor_a_y"
        private const val KEY_ANCHOR_B_HKE = "anchor_b_hke"
        private const val KEY_ANCHOR_B_HKN = "anchor_b_hkn"
        private const val KEY_ANCHOR_B_X = "anchor_b_x"
        private const val KEY_ANCHOR_B_Y = "anchor_b_y"
    }

    /**
     * A calibration anchor point known in both coordinate systems.
     */
    data class Anchor(
        val hkE: Double,    // Zeelo HK1980 Easting (metres)
        val hkN: Double,    // Zeelo HK1980 Northing (metres)
        val temiX: Float,   // temi map X (metres)
        val temiY: Float    // temi map Y (metres)
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Transform parameters: temi = scale * R(θ) * zeelo + offset
    private var scale: Double = 1.0
    private var rotation: Double = 0.0          // radians
    private var offsetX: Double = 0.0
    private var offsetY: Double = 0.0
    var isCalibrated: Boolean = false
        private set

    // Stored anchors (for display / re-calibration)
    var anchorA: Anchor? = null
        private set
    var anchorB: Anchor? = null
        private set

    init {
        loadCalibration()
    }

    // ──────────────────────── calibration ──────────────────────────────

    /**
     * Compute and persist the affine transform from two anchor points.
     *
     * The transform maps Zeelo (hkE, hkN) → temi (x, y) using:
     *   temi_x = scale * (cos θ · hkE  −  sin θ · hkN) + offset_x
     *   temi_y = scale * (sin θ · hkE  +  cos θ · hkN) + offset_y
     *
     * @return true if calibration succeeded
     */
    fun calibrate(a: Anchor, b: Anchor): Boolean {
        // Vectors in each coordinate system
        val dZeeloE = b.hkE - a.hkE
        val dZeeloN = b.hkN - a.hkN
        val dTemiX = (b.temiX - a.temiX).toDouble()
        val dTemiY = (b.temiY - a.temiY).toDouble()

        val distZeelo = hypot(dZeeloE, dZeeloN)
        val distTemi = hypot(dTemiX, dTemiY)

        if (distZeelo < 0.5 || distTemi < 0.5) {
            Log.e(TAG, "Anchors too close (zeelo=$distZeelo m, temi=$distTemi m). " +
                    "Choose points at least 0.5 m apart.")
            return false
        }

        // Scale = |temi vector| / |zeelo vector|
        scale = distTemi / distZeelo

        // Rotation = angle(temi vector) − angle(zeelo vector)
        val angleZeelo = atan2(dZeeloN, dZeeloE)
        val angleTemi = atan2(dTemiY, dTemiX)
        rotation = angleTemi - angleZeelo

        // Offset: solve for translation using anchor A
        offsetX = a.temiX - scale * (cos(rotation) * a.hkE - sin(rotation) * a.hkN)
        offsetY = a.temiY - scale * (sin(rotation) * a.hkE + cos(rotation) * a.hkN)

        anchorA = a
        anchorB = b
        isCalibrated = true

        saveCalibration()
        Log.i(TAG, "Calibration complete: scale=%.6f, rotation=%.2f°, offset=(%.3f, %.3f)".format(
            scale, Math.toDegrees(rotation), offsetX, offsetY))
        return true
    }

    // ──────────────────────── transform ────────────────────────────────

    /**
     * Convert Zeelo HK1980 coordinates to a temi [Position].
     *
     * @param hkE     Zeelo Easting  (metres)
     * @param hkN     Zeelo Northing (metres)
     * @param yawDeg  Direction in degrees (0 = North, clockwise).
     *                The same rotation offset is applied so the robot faces
     *                the correct direction in its own map frame.
     * @return a temi [Position], or null if not yet calibrated.
     */
    fun toTemiPosition(hkE: Double, hkN: Double, yawDeg: Float = 0f): Position? {
        if (!isCalibrated) {
            Log.w(TAG, "toTemiPosition called before calibration")
            return null
        }

        val x = scale * (cos(rotation) * hkE - sin(rotation) * hkN) + offsetX
        val y = scale * (sin(rotation) * hkE + cos(rotation) * hkN) + offsetY

        // Adjust yaw by the rotation offset (convert rad → deg)
        val adjustedYaw = yawDeg + Math.toDegrees(rotation).toFloat()

        Log.d(TAG, "Mapped (hkE=%.2f, hkN=%.2f) → temi(x=%.3f, y=%.3f, yaw=%.1f)".format(
            hkE, hkN, x, y, adjustedYaw))

        return Position(x.toFloat(), y.toFloat(), adjustedYaw, 0)
    }

    // ──────────────────────── persistence ──────────────────────────────

    private fun saveCalibration() {
        prefs.edit().apply {
            putBoolean(KEY_CALIBRATED, true)
            putFloat(KEY_SCALE, scale.toFloat())
            putFloat(KEY_ROTATION, rotation.toFloat())
            putFloat(KEY_OFFSET_X, offsetX.toFloat())
            putFloat(KEY_OFFSET_Y, offsetY.toFloat())
            // Anchors
            anchorA?.let {
                putFloat(KEY_ANCHOR_A_HKE, it.hkE.toFloat())
                putFloat(KEY_ANCHOR_A_HKN, it.hkN.toFloat())
                putFloat(KEY_ANCHOR_A_X, it.temiX)
                putFloat(KEY_ANCHOR_A_Y, it.temiY)
            }
            anchorB?.let {
                putFloat(KEY_ANCHOR_B_HKE, it.hkE.toFloat())
                putFloat(KEY_ANCHOR_B_HKN, it.hkN.toFloat())
                putFloat(KEY_ANCHOR_B_X, it.temiX)
                putFloat(KEY_ANCHOR_B_Y, it.temiY)
            }
            apply()
        }
        Log.d(TAG, "Calibration saved to SharedPreferences")
    }

    private fun loadCalibration() {
        if (!prefs.getBoolean(KEY_CALIBRATED, false)) {
            Log.d(TAG, "No saved calibration found")
            return
        }
        scale = prefs.getFloat(KEY_SCALE, 1f).toDouble()
        rotation = prefs.getFloat(KEY_ROTATION, 0f).toDouble()
        offsetX = prefs.getFloat(KEY_OFFSET_X, 0f).toDouble()
        offsetY = prefs.getFloat(KEY_OFFSET_Y, 0f).toDouble()

        anchorA = Anchor(
            hkE = prefs.getFloat(KEY_ANCHOR_A_HKE, 0f).toDouble(),
            hkN = prefs.getFloat(KEY_ANCHOR_A_HKN, 0f).toDouble(),
            temiX = prefs.getFloat(KEY_ANCHOR_A_X, 0f),
            temiY = prefs.getFloat(KEY_ANCHOR_A_Y, 0f)
        )
        anchorB = Anchor(
            hkE = prefs.getFloat(KEY_ANCHOR_B_HKE, 0f).toDouble(),
            hkN = prefs.getFloat(KEY_ANCHOR_B_HKN, 0f).toDouble(),
            temiX = prefs.getFloat(KEY_ANCHOR_B_X, 0f),
            temiY = prefs.getFloat(KEY_ANCHOR_B_Y, 0f)
        )

        isCalibrated = true
        Log.i(TAG, "Loaded calibration: scale=%.6f, rotation=%.2f°, offset=(%.3f, %.3f)".format(
            scale, Math.toDegrees(rotation), offsetX, offsetY))
    }

    /**
     * Reset calibration data and clear persisted state.
     */
    fun resetCalibration() {
        prefs.edit().clear().apply()
        scale = 1.0
        rotation = 0.0
        offsetX = 0.0
        offsetY = 0.0
        anchorA = null
        anchorB = null
        isCalibrated = false
        Log.i(TAG, "Calibration reset")
    }

    /**
     * Human-readable summary of current calibration state.
     */
    fun getCalibrationSummary(): String {
        if (!isCalibrated) return "Not calibrated"
        return buildString {
            append("Scale: %.6f\n".format(scale))
            append("Rotation: %.2f°\n".format(Math.toDegrees(rotation)))
            append("Offset: (%.3f, %.3f)\n".format(offsetX, offsetY))
            anchorA?.let { append("Anchor A: hk(%.2f, %.2f) → temi(%.3f, %.3f)\n".format(it.hkE, it.hkN, it.temiX, it.temiY)) }
            anchorB?.let { append("Anchor B: hk(%.2f, %.2f) → temi(%.3f, %.3f)".format(it.hkE, it.hkN, it.temiX, it.temiY)) }
        }
    }
}
