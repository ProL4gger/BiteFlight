package com.example.rider

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.biteflight.voice.VoiceCallActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceCallActivityTest {
    @Test
    fun opensRiderCallScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, VoiceCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try {
            instrumentation.waitForIdleSync()
            assertNotNull(activity.findViewById<android.view.View>(com.example.biteflight.voice.R.id.etRemoteAddress))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
