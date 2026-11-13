package com.example.biteflight

import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.example.biteflight.ui.seller.SellerDashboardActivity
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DashboardNavigationTest {
    @Test fun sellerBarIsVisibleAndMenuOpens() {
        ActivityScenario.launch(SellerDashboardActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val home = activity.findViewById<View>(R.id.sellerHome)
                val availability = activity.findViewById<View>(R.id.sellerAvailability)
                assertTrue(home.left < availability.left)
                assertTrue(home.isShown && availability.isShown)
            }
            screenshot("seller-dashboard.png")
            scenario.onActivity {
                it.findViewById<View>(R.id.btnMenu).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<ViewPager2>(R.id.viewPager)
                Assert.assertEquals(0, viewPager.currentItem)
            }
        }
    }

    @Test fun buyerHomeIsCenteredAndSearchIsPresent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val cart = activity.findViewById<View>(R.id.buyerCart)
                val home = activity.findViewById<View>(R.id.buyerHome)
                val settings = activity.findViewById<View>(R.id.btnSettings)
                assertTrue(cart.left < home.left && home.left < settings.left)
                val parent = home.parent as View
                assertTrue(abs((home.left + home.right) / 2 - parent.width / 2) < 10)
                assertTrue(activity.findViewById<View>(R.id.etRestaurantSearch).isShown)
            }
            screenshot("buyer-dashboard.png")
            scenario.onActivity {
                it.findViewById<EditText>(R.id.etRestaurantSearch).setText("no-such-kitchen-987654321")
                assertTrue(it.findViewById<View>(R.id.tvRestaurantEmpty).isShown)
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
