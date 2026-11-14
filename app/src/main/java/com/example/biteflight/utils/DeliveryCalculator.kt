package com.example.biteflight.utils

import kotlin.math.*

object DeliveryCalculator {

    data class FeeConfig(
        val baseFee: Double = 15.0,
        val ratePerKm: Double = 5.0,
        val minFee: Double = 20.0,
        val maxDistanceKm: Double = 25.0,
        val roadFactor: Double = 1.3      // straight-line -> approximate road distance
    )

    // Single place to tune pricing (could later be filled from Remote Config)
    var config = FeeConfig()

    /** Straight-line distance (Haversine). */
    fun calculateDistanceKm(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(endLat - startLat)
        val dLng = Math.toRadians(endLng - startLng)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun roadDistanceKm(straightKm: Double): Double = straightKm * config.roadFactor

    fun isInRange(distanceKm: Double): Boolean = distanceKm <= config.maxDistanceKm

    fun calculateFee(distanceKm: Double): Double {
        val raw = max(config.minFee, config.baseFee + distanceKm * config.ratePerKm)
        return round(raw * 2.0) / 2.0     // nearest 0.5 EGP
    }
}