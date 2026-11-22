package com.example.biteflight.voice

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.PeerConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class WebRtcAudioTest {
    @Test
    fun connectsPeersWhenCandidatesArriveSeparatelyFromDescriptions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)

        lateinit var caller: WebRtcAudio
        lateinit var recipient: WebRtcAudio
        val ready = CountDownLatch(2)
        val failed = AtomicBoolean()
        val callerConnected = AtomicBoolean()
        val recipientConnected = AtomicBoolean()
        val onError = {
            failed.set(true)
            ready.countDown()
            ready.countDown()
        }

        instrumentation.runOnMainSync {
            caller = WebRtcAudio(context, emptyList(),
                onLocalSdp = { recipient.answer(it) },
                onLocalCandidate = { recipient.addRemoteCandidate(it) },
                onConnection = {
                    if (it == PeerConnection.PeerConnectionState.CONNECTED && callerConnected.compareAndSet(false, true)) {
                        ready.countDown()
                    }
                },
                onError = onError
            )
            recipient = WebRtcAudio(context, emptyList(),
                onLocalSdp = { caller.applyAnswer(it) },
                onLocalCandidate = { caller.addRemoteCandidate(it) },
                onConnection = {
                    if (it == PeerConnection.PeerConnectionState.CONNECTED && recipientConnected.compareAndSet(false, true)) {
                        ready.countDown()
                    }
                },
                onError = onError
            )
            caller.offer()
        }

        try {
            assertTrue("Both peers should connect with separately exchanged candidates",
                ready.await(20, TimeUnit.SECONDS) && !failed.get())
        } finally {
            instrumentation.runOnMainSync {
                caller.close()
                recipient.close()
            }
        }
    }
}
