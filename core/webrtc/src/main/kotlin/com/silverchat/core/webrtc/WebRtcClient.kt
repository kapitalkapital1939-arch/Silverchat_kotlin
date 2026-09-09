package com.silverchat.core.webrtc

import android.content.Context
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.model.CallId
import com.silverchat.core.model.CallState
import com.silverchat.core.model.ConnectionQuality
import com.silverchat.core.model.IceServer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters
import org.webrtc.SessionDescription
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Обёртка над WebRTC PeerConnection.
 *
 * Ответственности:
 *  - инициализация [PeerConnectionFactory] ровно один раз на процесс;
 *  - создание/освобождение PeerConnection (утечка нативной памяти — самый
 *    частый баг в WebRTC-интеграциях, поэтому dispose обязателен);
 *  - захват аудио/видео, переключение камеры, mute;
 *  - статистика качества соединения для UI-индикатора;
 *  - ограничение битрейта: обычным пользователям — 1.2 Мбит/с,
 *    Premium (перк HD_CALLS) — 2.5 Мбит/с.
 *
 * Сигналинг (обмен SDP/ICE) — НЕ здесь: он идёт через :core:network WebSocket,
 * а этот класс лишь генерирует и принимает сигналы.
 */
@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val eglBase: EglBase by lazy { EglBase.create() }
    private val rootEglContext get() = eglBase.eglBaseContext

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: VideoCapturer? = null

    private val _state = MutableStateFlow(CallState.IDLE)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _quality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val quality: StateFlow<ConnectionQuality> = _quality.asStateFlow()

    private val _localStream = MutableStateFlow<MediaStream?>(null)
    val localStream: StateFlow<MediaStream?> = _localStream.asStateFlow()

    private val _remoteStream = MutableStateFlow<MediaStream?>(null)
    val remoteStream: StateFlow<MediaStream?> = _remoteStream.asStateFlow()

    /** Кандидаты, которые нужно отправить собеседнику через WebSocket. */
    val iceCandidates: Flow<IceCandidate> = callbackFlow {
        listener.onIceCandidate = { trySend(it) }
        awaitClose { listener.onIceCandidate = null }
    }

    /** События для CallController: ICE-состояния, удалённый поток, ошибки. */
    private val listener = MutableSignalingListener()

    /* ── Инициализация ──────────────────────────────────────────────────── */

    /**
     * Инициализация фабрики. Вызывается один раз; повторные вызовы
     * безопасны (идемпотентно).
     */
    fun initialize() {
        if (peerConnectionFactory != null) return

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .setFieldTrials(
                    // Отключаем встроенное шумоподавление: оно конфликтует с
                    // аппаратным AEC на части устройств и «съедает» голос
                    "WebRTC-AudioProcessing:Disabled/",
                )
                .createInitializationOptions(),
        )

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setSamplesReadyCallback { }
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(rootEglContext, true, true)
        val decoderFactory = SoftwareVideoDecoderFactory()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply { disableEncryption = false })
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        ScLogger.i(LogTag.CALL, "PeerConnectionFactory инициализирован")
    }

    /* ── PeerConnection ─────────────────────────────────────────────────── */

    fun createPeerConnection(
        callId: CallId,
        iceServers: List<IceServer>,
        observer: PeerConnection.Observer = listener,
    ): PeerConnection? {
        initialize()

        val rtcConfig = PeerConnection.RTCConfiguration(
            iceServers.map { server ->
                PeerConnection.IceServer.builder(server.urls)
                    .setUsername(server.username.orEmpty())
                    .setPassword(server.credential.orEmpty())
                    .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE)
                    .createIceServer()
            },
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            // Aggressive: быстрее восстанавливаем звонок при переключении Wi-Fi <-> LTE
            iceConnectionReceivingTimeout = ICE_TIMEOUT_MS
            iceBackupCandidatePairPingInterval = ICE_BACKUP_PING_MS
            keyType = PeerConnection.KeyType.ECDSA
            // DTLS-SRTP обязателен: медиа шифруется даже при P2P
            enableDscp = true
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        peerConnection?.dispose()
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        ScLogger.i(LogTag.CALL, "PeerConnection создан для звонка ${callId.raw}")
        return peerConnection
    }

    /** Локальные медиа-дорожки: микрофон всегда, камера — для видеозвонка. */
    fun createLocalMedia(audio: Boolean = true, video: Boolean = false, frontCamera: Boolean = true) {
        val factory = peerConnectionFactory ?: return
        val stream = factory.createLocalMediaStream("sc_local_stream")

        if (audio) {
            audioSource = factory.createAudioSource(MediaConstraints())
            factory.createAudioTrack("sc_audio", audioSource).let { stream.addTrack(it) }
        }

        if (video) {
            val capturer = createCameraCapturer(frontCamera) ?: run {
                ScLogger.w(LogTag.CALL, "Камера недоступна — звонок без видео")
                return@createLocalMedia
            }
            videoCapturer = capturer
            videoSource = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(
                org.webrtc.SurfaceTextureHelper.create("sc_capture", rootEglContext),
                context,
                videoSource?.capturerObserver,
            )
            capturer.startCapture(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS)

            factory.createVideoTrack("sc_video", videoSource).let { stream.addTrack(it) }
        }

        stream.audioTracks.forEach { it.setVolume(1.0) }
        _localStream.value = stream
        peerConnection?.addStream(stream)
    }

    private fun createCameraCapturer(front: Boolean): VideoCapturer? = runCatching {
        val enumerator = org.webrtc.Camera2Enumerator(context)
        val deviceName = enumerator.deviceNames.firstOrNull {
            if (front) enumerator.isFrontFacing(it) else enumerator.isBackFacing(it)
        } ?: enumerator.deviceNames.firstOrNull()
        deviceName?.let { enumerator.createCapturer(it, null) }
    }.getOrNull()

    fun switchCamera() {
        (videoCapturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        _localStream.value?.audioTracks?.forEach { it.setEnabled(enabled) }
    }

    fun setCameraEnabled(enabled: Boolean) {
        _localStream.value?.videoTracks?.forEach { it.setEnabled(enabled) }
    }

    /* ── SDP ────────────────────────────────────────────────────────────── */

    fun createOffer(isPremium: Boolean, onSuccess: (SessionDescription) -> Unit, onFailure: (String) -> Unit) {
        val constraints = offerConstraints(isPremium)
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                // Сначала фиксируем local SDP, затем отдаём его в сигналинг
                peerConnection?.setLocalDescription(
                    object : SdpObserverAdapter() {
                        override fun onSetSuccess() = onSuccess(description)
                        override fun onSetFailure(error: String?) = onFailure(error ?: "setLocalDescription failed")
                    },
                    description,
                )
            }

            override fun onCreateFailure(error: String?) = onFailure(error ?: "createOffer failed")
        }, constraints)
    }

    fun createAnswer(isPremium: Boolean, onSuccess: (SessionDescription) -> Unit, onFailure: (String) -> Unit) {
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(
                    object : SdpObserverAdapter() {
                        override fun onSetSuccess() = onSuccess(description)
                        override fun onSetFailure(error: String?) = onFailure(error ?: "setLocalDescription failed")
                    },
                    description,
                )
            }

            override fun onCreateFailure(error: String?) = onFailure(error ?: "createAnswer failed")
        }, offerConstraints(isPremium))
    }

    fun applyRemoteDescription(description: SessionDescription, onDone: () -> Unit) {
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() = onDone()
            override fun onSetFailure(error: String?) {
                ScLogger.e(LogTag.CALL, "Не удалось применить remote SDP: $error")
            }
        }, description)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * Битрейт: Premium получает HD-поток.
     * Ограничение ставится через RtpParameters, а не через SDP-munging —
     * правка SDP строками ломается при обновлении WebRTC.
     */
    fun applyBitrateLimit(maxKbps: Int) {
        val senders = peerConnection?.senders ?: return
        senders.forEach { sender ->
            val params: RtpParameters = sender.parameters ?: return@forEach
            params.encodings.forEach { encoding ->
                encoding.maxBitrateBps = maxKbps * 1000
                encoding.maxFramerate = if (maxKbps >= PREMIUM_BITRATE_KBPS) 60 else 30
            }
            sender.setParameters(params)
        }
    }

    /* ── Рендер ─────────────────────────────────────────────────────────── */

    /** Инициализация SurfaceViewRenderer из Compose (AndroidView). */
    fun initRenderer(renderer: SurfaceViewRenderer) {
        renderer.init(rootEglContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setZOrderMediaOverlay(true)
    }

    fun releaseRenderer(renderer: SurfaceViewRenderer) {
        runCatching { renderer.release() }
    }

    /* ── Освобождение ресурсов ──────────────────────────────────────────── */

    /**
     * ОБЯЗАТЕЛЬНО вызывать после завершения звонка.
     * WebRTC держит нативную память: без dispose за 10 звонков процесс
     * упирается в лимит и падает с SIGSEGV.
     */
    fun dispose() {
        ScLogger.i(LogTag.CALL, "Освобождаем WebRTC-ресурсы")
        runCatching {
            _localStream.value?.dispose()
            _remoteStream.value?.dispose()
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoSource?.dispose()
            audioSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
        }.onFailure { ScLogger.w(LogTag.CALL, "Ошибка при освобождении ресурсов", it) }

        peerConnection = null
        videoCapturer = null
        videoSource = null
        audioSource = null
        _localStream.value = null
        _remoteStream.value = null
        _quality.value = ConnectionQuality.UNKNOWN
        _state.value = CallState.IDLE
    }

    fun shutdown() {
        dispose()
        runCatching {
            peerConnectionFactory?.dispose()
            eglBase.release()
        }
        peerConnectionFactory = null
    }

    /* ── Внутренний listener ────────────────────────────────────────────── */

    private inner class MutableSignalingListener : PeerConnection.Observer {

        var onIceCandidate: ((IceCandidate) -> Unit)? = null

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            ScLogger.d(LogTag.CALL, "signaling: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            ScLogger.i(LogTag.CALL, "ice: $state")
            _state.value = when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> CallState.ACTIVE

                PeerConnection.IceConnectionState.CHECKING -> CallState.CONNECTING
                PeerConnection.IceConnectionState.DISCONNECTED -> CallState.RECONNECTING

                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.CLOSED,
                -> CallState.FAILED

                else -> _state.value
            }
            _quality.value = state.toQuality()
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            if (!receiving) _quality.value = ConnectionQuality.LOST
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let { onIceCandidate?.invoke(it) }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddStream(stream: MediaStream?) {
            ScLogger.i(LogTag.CALL, "Получен удалённый поток")
            _remoteStream.value = stream
        }

        override fun onRemoveStream(stream: MediaStream?) {
            _remoteStream.value = null
        }

        override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit

        override fun onRenegotiationNeeded() {
            ScLogger.d(LogTag.CALL, "Требуется пересогласование SDP")
        }

        override fun onAddTrack(rtpReceiver: org.webrtc.RtpReceiver?, streams: Array<out MediaStream>?) {
            streams?.firstOrNull()?.let { _remoteStream.value = it }
        }

        override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
            transceiver?.receiver?.track()?.let { track ->
                val stream = _remoteStream.value ?: return@let
                if (track is org.webrtc.VideoTrack) stream.addTrack(track)
            }
        }
    }

    private fun PeerConnection.IceConnectionState?.toQuality(): ConnectionQuality = when (this) {
        PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> ConnectionQuality.EXCELLENT
        PeerConnection.IceConnectionState.CHECKING -> ConnectionQuality.FAIR
        PeerConnection.IceConnectionState.DISCONNECTED -> ConnectionQuality.POOR
        PeerConnection.IceConnectionState.FAILED, PeerConnection.IceConnectionState.CLOSED -> ConnectionQuality.LOST
        else -> ConnectionQuality.UNKNOWN
    }

    private fun offerConstraints(isPremium: Boolean): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        // Единый набор кодеков: VP8/H264 для видео, Opus для аудио
        optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        optional.add(
            MediaConstraints.KeyValuePair(
                "videoMaxBitrate",
                (if (isPremium) PREMIUM_BITRATE_KBPS else DEFAULT_BITRATE_KBPS).toString(),
            ),
        )
    }

    private companion object {
        const val ICE_TIMEOUT_MS = 4_000
        const val ICE_BACKUP_PING_MS = 2_000
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FPS = 30
        const val DEFAULT_BITRATE_KBPS = 1200
        const val PREMIUM_BITRATE_KBPS = 2500
    }
}

/** Пустая реализация SdpObserver — наследники переопределяют нужное. */
abstract class SdpObserverAdapter : org.webrtc.SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit

    companion object {
        val NOOP = object : SdpObserverAdapter() {}
    }
}
