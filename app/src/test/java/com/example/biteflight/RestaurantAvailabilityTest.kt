package com.example.biteflight

import com.example.biteflight.data.model.User
import com.example.biteflight.utils.RestaurantAvailability
import com.example.biteflight.utils.RestaurantAvailability.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RestaurantAvailabilityTest {
    private val seller = User(role = User.ROLE_SELLER, openingTime = "09:00", closingTime = "22:00", restaurantTimeZone = "GMT+02:00")

    private fun time(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.JANUARY, 1, hour, minute)
        }.timeInMillis

    // Cairo is UTC+2 in January; the buyer's device timezone must not affect this.
    private val cairoSeller get() = seller.copy(restaurantTimeZone = "Africa/Cairo")

    @Test fun openingInclusiveAndClosingExclusive() {
        assertEquals(Status.CLOSED, RestaurantAvailability.status(cairoSeller, time(6, 59)))
        assertEquals(Status.OPEN, RestaurantAvailability.status(cairoSeller, time(7)))
        assertEquals(Status.OPEN, RestaurantAvailability.status(cairoSeller, time(19, 59)))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(cairoSeller, time(20)))
    }

    @Test fun overnightHours() {
        val overnight = cairoSeller.copy(openingTime = "18:00", closingTime = "02:00")
        assertEquals(Status.OPEN, RestaurantAvailability.status(overnight, time(16)))
        assertEquals(Status.OPEN, RestaurantAvailability.status(overnight, time(23)))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(overnight, time(0)))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(overnight, time(12)))
    }

    @Test fun busyOnlyOverridesOpenHours() {
        val busy = cairoSeller.copy(busy = true)
        assertEquals(Status.BUSY, RestaurantAvailability.status(busy, time(12)))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(busy, time(23)))
    }

    @Test fun legacyProfilesRemainOpenButRespectBusy() {
        val legacy = User(role = User.ROLE_SELLER)
        assertEquals(Status.OPEN, RestaurantAvailability.status(legacy, time(12)))
        assertEquals(Status.BUSY, RestaurantAvailability.status(legacy.copy(busy = true), time(12)))
    }

    @Test fun invalidSchedulesFailClosed() {
        assertNull(RestaurantAvailability.minutes("25:00"))
        assertNull(RestaurantAvailability.minutes("09:60"))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(cairoSeller.copy(closingTime = "09:00"), time(12)))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(cairoSeller.copy(closingTime = ""), time(12)))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(cairoSeller.copy(restaurantTimeZone = "Invalid"), time(12)))
        assertEquals(Status.CLOSED, RestaurantAvailability.status(User(), time(12)))
    }
}
