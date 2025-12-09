package com.phonecam

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WebRtcClient(
    private val context: Context,
    private val eglBase: EglBase,
    private val signaling: Signaling,
    private var targetBitrateMbps: Int = 15,
    private val status: (String) -> Unit
) {
    private val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
    private val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
    private val peerConnectionFactory: PeerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(encoderFactory)
        .setVideoDecoderFactory(decoderFactory)
        .createPeerConnectionFactory()

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var peerConnection: PeerConnection? = null
    private var videoSender: RtpSender? = null
    private var iceGatheringComplete: CompletableDeferred<Unit>? = null
    private var previewRenderer: SurfaceViewRenderer? = null
    private var previewStarted = false

    fun attachPreview(renderer: SurfaceViewRenderer) {
        previewRenderer = renderer
        videoTrack?.addSink(renderer)
    }

    suspend fun startPreview() {
        if (previewStarted) return
        status("Starting preview…")
        startLocalTracks()
        previewStarted = true
        status("Preview ready")
    }

    suspend fun connect(signalingUrl: String) {
        if (!previewStarted) {
            status("Starting camera…")
            startLocalTracks()
            previewStarted = true
        }
        status("Preparing connection…")
        buildPeerConnection()
        status("Establishing connection…")
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection missing")
        val offer = pc.createOfferAsync(mediaConstraints())
        pc.setLocalDescriptionAsync(offer)
        awaitIceGatheringComplete()
        val local = pc.localDescription ?: offer
        status("Connecting to PC…")
        val answer = signaling.exchangeOffer(signalingUrl, local)
        pc.setRemoteDescriptionAsync(answer)
        status("Connected to PC")
    }

    suspend fun restartCamera() {
        status("Restarting camera after unlock…")
        try {
            // Stop current capture if active
            videoCapturer?.stopCapture()
            
            // Restart capture with same settings
            videoCapturer?.startCapture(1920, 1080, 60)
            status("Camera restarted")
        } catch (e: Exception) {
            status("Camera restart error: ${e.message}")
            throw e
        }
    }

    suspend fun disconnect() {
        status("Disconnecting…")
        try {
            // Close peer connection cleanly
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            videoSender = null
            iceGatheringComplete = null
            
            status("Disconnected - camera preview active")
        } catch (e: Exception) {
            status("Disconnect error: ${e.message}")
            throw e
        }
    }

    fun stop() {
        status("Stopping…")
        try { videoCapturer?.stopCapture() } catch (_: Exception) { }
        videoCapturer?.dispose()
        surfaceHelper?.dispose()
        videoSource?.dispose()
        videoTrack?.dispose()
        audioSource?.dispose()
        audioTrack?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        cameraThread.quitSafely()
    }

    private fun startLocalTracks() {
        if (videoTrack != null) {
            // Already started
            return
        }
        
        try {
            val helper = SurfaceTextureHelper.create("capture", eglBase.eglBaseContext)
                ?: throw IllegalStateException("SurfaceTextureHelper creation failed")
            surfaceHelper = helper

            val capturer = createCapturer(context)
                ?: throw IllegalStateException("No camera capturer available")
            videoCapturer = capturer
            
            // Create video source with isScreencast=false for camera
            val vSource = peerConnectionFactory.createVideoSource(false)
            videoSource = vSource
            
            capturer.initialize(helper, context, vSource.capturerObserver)
            
            // Request 1920x1080 @ 60fps (landscape orientation)
            // If camera is held in portrait, this becomes 1080x1920
            capturer.startCapture(1920, 1080, 60)
            
            status("Camera: requesting 1920x1080@60fps")
            
            val vTrack = peerConnectionFactory.createVideoTrack("video0", vSource)
            vTrack.setEnabled(true)
            videoTrack = vTrack
            previewRenderer?.let { vTrack.addSink(it) }

            audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            audioTrack = peerConnectionFactory.createAudioTrack("audio0", audioSource)
        } catch (e: Exception) {
            status("Camera error: ${e.message}")
            throw e
        }
    }

    private fun buildPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        iceGatheringComplete = CompletableDeferred()
        peerConnection = peerConnectionFactory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) { /* no trickle; wait for complete */ }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                val message = when(newState) {
                    PeerConnection.IceConnectionState.CHECKING -> "Finding network path…"
                    PeerConnection.IceConnectionState.CONNECTED -> "Network connected"
                    PeerConnection.IceConnectionState.COMPLETED -> "Network stable"
                    PeerConnection.IceConnectionState.FAILED -> "Network connection failed"
                    PeerConnection.IceConnectionState.DISCONNECTED -> "Network disconnected"
                    PeerConnection.IceConnectionState.CLOSED -> "Connection closed"
                    else -> "Network: $newState"
                }
                status(message)
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                val message = when(newState) {
                    PeerConnection.PeerConnectionState.CONNECTING -> "Connecting to PC…"
                    PeerConnection.PeerConnectionState.CONNECTED -> "Connected to PC"
                    PeerConnection.PeerConnectionState.FAILED -> "Connection failed"
                    PeerConnection.PeerConnectionState.DISCONNECTED -> "Disconnected from PC"
                    PeerConnection.PeerConnectionState.CLOSED -> "Connection closed"
                    else -> "Status: $newState"
                }
                status(message)
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    iceGatheringComplete?.complete(Unit)
                }
            }
            override fun onAddStream(p0: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
        }) ?: throw IllegalStateException("PeerConnection creation failed")

        videoTrack?.let { track ->
            videoSender = peerConnection?.addTrack(track)
            
            // Configure bitrate with initial value
            // Configure bitrate with initial value (only at connection time)
            updateVideoSenderBitrate()
        }
        audioTrack?.let { track ->
            peerConnection?.addTrack(track)
        }
    }

    private fun updateVideoSenderBitrate() {
        videoSender?.let { sender ->
            val parameters = sender.parameters
            parameters.degradationPreference = org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
            
            // Convert Mbps to bps and set range
            // Cap at 15 Mbps for stable encoding
            val cappedBitrate = minOf(targetBitrateMbps, 15)
            val maxBps = cappedBitrate * 1_000_000
            val minBps = maxBps // Set min = max for stable bitrate (no adaptation)
            
            parameters.encodings.forEach { encoding ->
                encoding.maxBitrateBps = maxBps
                encoding.minBitrateBps = minBps
                encoding.maxFramerate = 60
                // Explicitly disable adaptive bitrate
                encoding.networkPriority = 1 // High priority
                encoding.active = true
                // Don't set scaleResolutionDownBy - leave unset for no downscaling
            }
            
            sender.parameters = parameters
            val actualMbps = minOf(targetBitrateMbps, 15)
            status("Video bitrate: ${actualMbps} Mbps (fixed, no adaptation), maintain 1080p")
        }
    }

    private fun createCapturer(context: Context): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        val preferBack = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        val preferFront = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val ordered = listOfNotNull(preferBack) + deviceNames.filterNot { it == preferBack }
        for (name in ordered) {
            val capturer = enumerator.createCapturer(name, null)
            if (capturer != null) return capturer
        }
        // fallback to any
        return preferFront?.let { enumerator.createCapturer(it, null) }
    }

    private fun mediaConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
    }

    private suspend fun awaitIceGatheringComplete() {
        try {
            iceGatheringComplete?.await()
        } catch (_: Exception) {
            // Swallow and continue; ICE might still work with host candidates.
        }
    }
}

private suspend fun PeerConnection.createOfferAsync(constraints: MediaConstraints): SessionDescription = suspendCancellableCoroutine { cont ->
    this.createOffer(object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) { cont.resume(desc) }
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
        override fun onSetFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
    }, constraints)
}

private suspend fun PeerConnection.setLocalDescriptionAsync(desc: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
    this.setLocalDescription(object : org.webrtc.SdpObserver {
        override fun onSetSuccess() { cont.resume(Unit) }
        override fun onSetFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onCreateFailure(p0: String?) {}
    }, desc)
}

private suspend fun PeerConnection.setRemoteDescriptionAsync(desc: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
    this.setRemoteDescription(object : org.webrtc.SdpObserver {
        override fun onSetSuccess() { cont.resume(Unit) }
        override fun onSetFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onCreateFailure(p0: String?) {}
    }, desc)
}
