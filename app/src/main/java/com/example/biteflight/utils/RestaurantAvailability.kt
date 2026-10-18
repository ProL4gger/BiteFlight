package com.example.biteflight.utils

import com.example.biteflight.data.model.User
import java.util.Calendar
import java.util.TimeZone

object RestaurantAvailability {
    enum class Status { OPEN, BUSY, CLOSED }

    fun minutes(time: String): Int? {
        if (!Regex("\\d{2}:\\d{2}").matches(time)) return null
        val (hour, minute) = time.split(":").map { it.toInt() }
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    fun status(user: User, now: Long = System.currentTimeMillis()): Status {
        if (!user.role.equals(User.ROLE_SELLER, true)) return Status.CLOSED
        // Existing restaurants without a schedule stay available until hours are set.
        if (user.openingTime.isNotBlank() || user.closingTime.isNotBlank()) {
            val opening = minutes(user.openingTime) ?: return Status.CLOSED
            val closing = minutes(user.closingTime) ?: return Status.CLOSED
            if (opening == closing) return Status.CLOSED
            val zone = user.restaurantTimeZone
            if (zone !in TimeZone.getAvailableIDs()) return Status.CLOSED
            val clock = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
                timeInMillis = now
            }
            val minute = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE)
            val open = if (opening < closing) {
                minute >= opening && minute < closing
            } else {
                minute >= opening || minute < closing
            }
            if (!open) return Status.CLOSED
        }
        return if (user.busy) Status.BUSY else Status.OPEN
    }

    fun label(user: User): String = when (status(user)) {
        Status.OPEN -> "Open · Accepting orders"
        Status.BUSY -> "Busy · Orders paused"
        Status.CLOSED -> "Closed · Not accepting orders"
    }

    fun hours(user: User): String = if (user.openingTime.isBlank() && user.closingTime.isBlank()) {
        "Opening hours not set"
    } else {
        val overnight = (minutes(user.openingTime) ?: 0) > (minutes(user.closingTime) ?: 0)
        "Daily ${user.openingTime}–${user.closingTime}" +
            (if (overnight) " (next day)" else "") + " · ${user.restaurantTimeZone}"
    }
}
