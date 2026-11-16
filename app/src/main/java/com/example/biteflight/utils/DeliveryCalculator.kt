package com.example.biteflight.utils

import kotlin.math.*

object DeliveryCalculator {

    data class FeeConfig(
        val baseFee: Double = 10.0,
        val ratePerKm: Double = 1.5,
        val minFee: Double = 20.0,
        val maxDistanceKm: Double = 50.0,
        val roadFactor: Double = 1.1
    )

    // Single place to configure delivery pricing
    var config = FeeConfig()

    /** Calculate straight-line distance using Haversine. */
    fun calculateDistanceKm(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double {
        val r = 6371.0

        val dLat = Math.toRadians(endLat - startLat)
        val dLng = Math.toRadians(endLng - startLng)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(startLat)) *
                cos(Math.toRadians(endLat)) *
                sin(dLng / 2).pow(2)

        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Estimate road distance from straight-line distance. */
    fun roadDistanceKm(straightKm: Double): Double {
        return straightKm * config.roadFactor
    }

    /** Allow delivery up to and including 50 km. */
    fun isInRange(distanceKm: Double): Boolean {
        return distanceKm <= config.maxDistanceKm
    }

    /** Calculate delivery fee based on estimated distance. */
    fun calculateFee(distanceKm: Double): Double {
        val raw = max(
            config.minFee,
            config.baseFee + distanceKm * config.ratePerKm
        )

        return round(raw * 2.0) / 2.0
    }
}
