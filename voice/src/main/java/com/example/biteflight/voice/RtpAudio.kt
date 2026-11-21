package com.example.biteflight.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.random.Random

internal object Pcmu {
    fun encode(sample: Short): Byte {
        val pcm = sample.toInt()
        val magnitude = kotlin.math.abs(pcm).coerceAtMost(32635) + 132
        val exponent = (31 - Integer.numberOfLeadingZeros(magnitude) - 7).coerceIn(0, 7)
        val mantissa = (magnitude shr (exponent + 3)) and 15
        val sign = if (pcm < 0) 128 else 0
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    fun decode(sample: Byte): Short {
        val code = sample.toInt().inv() and 255
        val magnitude = (((code and 15) * 8 + 132) shl ((code shr 4) and 7)) - 132
        return (if (code and 128 == 0) magnitude else -magnitude).toShort()
    }
}

internal class RtpAudio : AutoCloseable {
    private val socket = DatagramSocket()
    val port: Int get() = socket.localPort

    fun start(peer: InetSocketAddress, onError: () -> Unit) {
        socket.connect(peer)
        thread(name = "BiteFlight RTP capture") { runAudio(onError, ::capture) }
        thread(name = "BiteFlight RTP playback") { runAudio(onError, ::play) }
    }

    private fun runAudio(onError: () -> Unit, action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            if (!socket.isClosed) onError()
        }
    }

    @SuppressLint("MissingPermission") // Both Call and Answer check RECORD_AUDIO before starting media.
    private fun capture() {
        val bufferSize = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize.coerceAtLeast(FRAME_SAMPLES * 4)
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            if (socket.isClosed) return
            recorder.startRecording()
            val samples = ShortArray(FRAME_SAMPLES)
            val data = ByteBuffer.allocate(HEADER + FRAME_SAMPLES)
            val packet = DatagramPacket(data.array(), data.capacity())
            var sequence = Random.nextInt(65536)
            var timestamp = Random.nextInt()
            val ssrc = Random.nextInt()
            var offset = 0
            while (!socket.isClosed) {
                val count = recorder.read(samples, offset, FRAME_SAMPLES - offset)
                check(count > 0)
                offset += count
                if (offset < FRAME_SAMPLES) continue
                data.clear()
                data.put(0x80.toByte()).put(0.toByte()).putShort(sequence++.toShort())
                data.putInt(timestamp).putInt(ssrc)
                samples.forEach { data.put(Pcmu.encode(it)) }
                socket.send(packet)
                timestamp += FRAME_SAMPLES
                offset = 0
            }
        } finally {
            recorder.release()
        }
    }

    private fun play() {
        val bufferSize = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val player = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(FRAME_SAMPLES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            check(player.state == AudioTrack.STATE_INITIALIZED)
            if (socket.isClosed) return
            player.play()
            val bytes = ByteArray(HEADER + FRAME_SAMPLES + 1)
            val packet = DatagramPacket(bytes, bytes.size)
            val samples = ShortArray(FRAME_SAMPLES)
            var lastSequence: Int? = null
            while (!socket.isClosed) {
                packet.length = bytes.size
                socket.receive(packet)
                if (packet.length != HEADER + FRAME_SAMPLES ||
                    bytes[0] != 0x80.toByte() || bytes[1].toInt() and 127 != 0
                ) continue
                val sequence = ((bytes[2].toInt() and 255) shl 8) or (bytes[3].toInt() and 255)
                val delta = lastSequence?.let { (sequence - it) and 65535 } ?: 1
                if (delta !in 1..32767) continue
                lastSequence = sequence
                for (index in samples.indices) samples[index] = Pcmu.decode(bytes[index + HEADER])
                check(player.write(samples, 0, samples.size) > 0)
            }
        } finally {
            player.release()
        }
    }

    override fun close() = socket.close()

    private companion object {
        const val RATE = 8000
        const val FRAME_SAMPLES = 160
        const val HEADER = 12
    }
}
