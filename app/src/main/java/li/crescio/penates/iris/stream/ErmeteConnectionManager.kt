package li.crescio.penates.iris.stream

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit


class ErmeteConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onConnectionStateChanged: (ServerConnectionState) -> Unit,
    private val onDebugLog: (ConnectionDebugLogEntry) -> Unit,
) {
  companion object {
    private const val TAG = "ErmeteConnection"
    private const val AUDIO_TRACK_ID = "iris-audio-track"
    private const val STREAM_ID = "iris-stream"
    private const val ERMETE_PSK_HEADER = "X-Ermete-PSK"
  }

  private val httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
  private var wsClient: OkHttpClient? = null
  private var ws: WebSocket? = null

  private var peerConnectionFactory: PeerConnectionFactory? = null
  private var peerConnection: PeerConnection? = null
  private var audioSource: AudioSource? = null
  private var audioTrack: AudioTrack? = null
  private var cmdDataChannel: org.webrtc.DataChannel? = null

  private var connectionState = ServerConnectionState.DISCONNECTED

  fun connect(serverHttpUrl: String, ermetePsk: String) {
    close()

    val trimmedPsk = ermetePsk.trim()
    if (trimmedPsk.isEmpty()) {
      addDebugLog("Missing Ermete PSK, refusing connection")
      setConnectionState(ServerConnectionState.FAILED)
      return
    }

    val wsUrl = buildSignalingWebSocketUrl(serverHttpUrl)
    val iceServers = buildIceServers(serverHttpUrl)

    addDebugLog("Starting connection to $serverHttpUrl (role=client)")
    addDebugLog("Opening signaling WebSocket at $wsUrl")
    addDebugLog("Using ${iceServers.size} ICE server(s)")

    setConnectionState(ServerConnectionState.CONNECTING)
    initializePeerConnectionFactory()
    createPeerConnection(iceServers)

    wsClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    val wsRequest =
        Request.Builder()
            .url(wsUrl)
            .addHeader(ERMETE_PSK_HEADER, trimmedPsk)
            .build()
    ws = wsClient!!.newWebSocket(wsRequest, webSocketListener)
  }

  fun sendFrame(serverHttpUrl: String, ermetePsk: String, bytes: ByteArray) {
    val trimmedPsk = ermetePsk.trim()
    if (trimmedPsk.isEmpty()) {
      addDebugLog("Skipping frame upload: missing Ermete PSK")
      return
    }

    val frameUploadUrl = buildFrameUploadUrl(serverHttpUrl)
    scope.launch(Dispatchers.IO) {
      runCatching {
            val request =
                Request.Builder()
                    .url(frameUploadUrl)
                    .addHeader("Content-Type", "image/jpeg")
                    .addHeader(ERMETE_PSK_HEADER, trimmedPsk)
                    .addHeader("X-Frame-Id", UUID.randomUUID().toString())
                    .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()
            httpClient.newCall(request).execute().use { response ->
              if (!response.isSuccessful) {
                val message = "Frame upload failed: HTTP ${response.code}"
                addDebugLog(message)
                Log.e(TAG, message)
              }
            }
          }
          .onFailure {
            addDebugLog("Frame upload error ($frameUploadUrl): ${it.message ?: "unknown"}")
            Log.e(TAG, "Frame upload error", it)
          }
    }
  }

  private fun buildSignalingWebSocketUrl(serverHttpUrl: String): String {
    val normalizedBaseUrl = normalizeBaseUrl(serverHttpUrl)
    val websocketBaseUrl =
        when {
          normalizedBaseUrl.startsWith("http://") -> normalizedBaseUrl.replaceFirst("http://", "ws://")
          normalizedBaseUrl.startsWith("https://") -> normalizedBaseUrl.replaceFirst("https://", "wss://")
          normalizedBaseUrl.startsWith("ws://") || normalizedBaseUrl.startsWith("wss://") -> normalizedBaseUrl
          else -> "ws://$normalizedBaseUrl"
        }

    return "$websocketBaseUrl/v1/ws?role=client"
  }

  private fun buildFrameUploadUrl(serverHttpUrl: String): String {
    val normalizedBaseUrl = normalizeBaseUrl(serverHttpUrl)
    val httpBaseUrl =
        when {
          normalizedBaseUrl.startsWith("ws://") -> normalizedBaseUrl.replaceFirst("ws://", "http://")
          normalizedBaseUrl.startsWith("wss://") -> normalizedBaseUrl.replaceFirst("wss://", "https://")
          else -> normalizedBaseUrl
        }

    return "$httpBaseUrl/v1/frames"
  }

  private fun normalizeBaseUrl(serverHttpUrl: String): String {
    val trimmed = serverHttpUrl.trim().trimEnd('/')
    val withScheme =
        if (trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("ws://") ||
            trimmed.startsWith("wss://")) {
          trimmed
        } else {
          "http://$trimmed"
        }
    return withScheme.removeSuffix("/v1/ws").removeSuffix("/v1/frames")
  }

  private fun buildIceServers(serverHttpUrl: String): List<PeerConnection.IceServer> {
    val normalizedBaseUrl = normalizeBaseUrl(serverHttpUrl)
    val host = runCatching { URI(normalizedBaseUrl).host }.getOrNull().orEmpty()

    val stunUrls = linkedSetOf("stun:stun.l.google.com:19302", "stun:stun.cloudflare.com:3478")
    if (host.isNotBlank()) {
      stunUrls += "stun:$host:3478"
    }

    return stunUrls.map { url -> PeerConnection.IceServer.builder(url).createIceServer() }
  }

  fun sendPing() {
    val payload = JSONObject().put("type", "ping").put("text", "hello from iris")
    addDebugLog("Sending ping on cmd data channel")
    cmdDataChannel?.send(
        org.webrtc.DataChannel.Buffer(java.nio.ByteBuffer.wrap(payload.toString().toByteArray()), false))
  }

  fun close() {
    addDebugLog("Closing connection manager resources")
    ws?.close(1000, "closing")
    ws = null
    wsClient?.dispatcher?.executorService?.shutdown()
    wsClient = null

    cmdDataChannel?.close()
    cmdDataChannel = null
    peerConnection?.close()
    peerConnection = null
    audioTrack?.dispose()
    audioTrack = null
    audioSource?.dispose()
    audioSource = null
    setConnectionState(ServerConnectionState.DISCONNECTED)
  }

  private fun initializePeerConnectionFactory() {
    if (peerConnectionFactory != null) return

    addDebugLog("Initializing PeerConnectionFactory")

    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions())

    val adm = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
    peerConnectionFactory =
        PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
  }

  private fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
    val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
    addDebugLog("Creating RTCPeerConnection")
    peerConnection =
        peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
              override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

              override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                addDebugLog("ICE connection state: $state")
                when (state) {
                  PeerConnection.IceConnectionState.CONNECTED,
                  PeerConnection.IceConnectionState.COMPLETED -> setConnectionState(ServerConnectionState.CONNECTED)
                  PeerConnection.IceConnectionState.FAILED,
                  PeerConnection.IceConnectionState.DISCONNECTED,
                  PeerConnection.IceConnectionState.CLOSED -> setConnectionState(ServerConnectionState.FAILED)
                  else -> Unit
                }
              }

              override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

              override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                addDebugLog("ICE gathering state: $state")
              }

              override fun onIceCandidate(candidate: IceCandidate) {
                addDebugLog("Local ICE candidate generated")
                val json =
                    JSONObject()
                        .put("type", "candidate")
                        .put(
                            "candidate",
                            JSONObject()
                                .put("candidate", candidate.sdp)
                                .put("sdpMid", candidate.sdpMid)
                                .put("sdpMLineIndex", candidate.sdpMLineIndex))
                ws?.send(json.toString())
              }

              override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

              override fun onAddStream(stream: org.webrtc.MediaStream) = Unit

              override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit

              override fun onDataChannel(dataChannel: org.webrtc.DataChannel) {
                addDebugLog("Remote data channel opened: ${dataChannel.label()}")
                cmdDataChannel = dataChannel
                dataChannel.registerObserver(
                    object : org.webrtc.DataChannel.Observer {
                      override fun onBufferedAmountChange(previousAmount: Long) = Unit

                      override fun onStateChange() {
                        addDebugLog("Data channel state changed: ${dataChannel.state()}")
                      }

                      override fun onMessage(buffer: org.webrtc.DataChannel.Buffer) {
                        val bytes = ByteArray(buffer.data.remaining())
                        buffer.data.get(bytes)
                        val payload = String(bytes)
                        addDebugLog("Data channel message: $payload")
                        Log.d(TAG, "DataChannel recv: $payload")
                      }
                    })
              }

              override fun onRenegotiationNeeded() = Unit

              override fun onAddTrack(
                  receiver: org.webrtc.RtpReceiver,
                  mediaStreams: Array<out org.webrtc.MediaStream>
              ) = Unit

              override fun onTrack(transceiver: RtpTransceiver) = Unit
            })

    val source = peerConnectionFactory?.createAudioSource(MediaConstraints())
    audioSource = source
    audioTrack = peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, source)
    peerConnection?.addTrack(audioTrack, listOf(STREAM_ID))
    peerConnection?.addTransceiver(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)

    val init = org.webrtc.DataChannel.Init()
    val outboundDataChannel = peerConnection?.createDataChannel("cmd", init)
    addDebugLog("Created local data channel: cmd")
    cmdDataChannel = outboundDataChannel
  }

  private fun createAndSendOffer() {
    val pc = peerConnection ?: return
    addDebugLog("Creating local SDP offer")
    pc.createOffer(
        object : org.webrtc.SdpObserver {
          override fun onCreateSuccess(description: SessionDescription?) {
            description ?: return
            pc.setLocalDescription(noopSdpObserver, description)
            val offer = JSONObject().put("type", "offer").put("sdp", description.description)
            addDebugLog("Sending SDP offer to signaling server")
            ws?.send(offer.toString())
          }

          override fun onSetSuccess() = Unit

          override fun onCreateFailure(error: String?) {
            val reason = "Offer failed: ${error ?: "unknown"}"
            addDebugLog(reason)
            setConnectionState(ServerConnectionState.FAILED)
            Log.e(TAG, reason)
          }

          override fun onSetFailure(error: String?) = Unit
        },
        MediaConstraints())
  }

  private val webSocketListener =
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          addDebugLog("WebSocket opened: HTTP ${response.code}")
          setConnectionState(ServerConnectionState.SIGNALING)
          createAndSendOffer()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          addDebugLog("Signaling message received (${text.length} chars)")
          runCatching {
                val json = JSONObject(text)
                when (json.getString("type")) {
                  "answer" -> {
                    addDebugLog("Received SDP answer")
                    val sdp = json.getString("sdp")
                    peerConnection?.setRemoteDescription(
                        noopSdpObserver,
                        SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    sendPing()
                  }
                  "candidate" -> {
                    addDebugLog("Received remote ICE candidate")
                    val candidate = json.getJSONObject("candidate")
                    peerConnection?.addIceCandidate(
                        IceCandidate(
                            candidate.optString("sdpMid", "0"),
                            candidate.getInt("sdpMLineIndex"),
                            candidate.getString("candidate")))
                  }
                  "bye" -> {
                    addDebugLog("Received server bye")
                    close()
                  }
                  "error" -> {
                    val message = json.optString("message")
                    addDebugLog("Server error: $message")
                    setConnectionState(ServerConnectionState.FAILED)
                    Log.e(TAG, "Server error: $message")
                  }
                  else -> addDebugLog("Unknown signaling message type: ${json.optString("type")}")
                }
              }
              .onFailure {
                addDebugLog("Signaling parse error: ${it.message ?: "unknown"}")
                setConnectionState(ServerConnectionState.FAILED)
                Log.e(TAG, "WS parse error", it)
              }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          val serverCode = response?.code?.toString() ?: "n/a"
          addDebugLog("WebSocket failure (http=$serverCode): ${t.message ?: "unknown"}")
          if (t.message?.contains("Unable to parse TLS packet header", ignoreCase = true) == true) {
            addDebugLog(
                "TLS handshake failed. If your server is plain WS on this port, use http:// or ws:// instead of https://.")
          }
          setConnectionState(ServerConnectionState.FAILED)
          Log.e(TAG, "WebSocket failure", t)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
          addDebugLog("WebSocket closing: code=$code reason=$reason")
          setConnectionState(ServerConnectionState.DISCONNECTED)
          super.onClosing(webSocket, code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          addDebugLog("WebSocket closed: code=$code reason=$reason")
          setConnectionState(ServerConnectionState.DISCONNECTED)
          super.onClosed(webSocket, code, reason)
        }
      }

  private fun setConnectionState(newState: ServerConnectionState) {
    if (connectionState == newState) return
    addDebugLog("Connection state changed: $connectionState -> $newState")
    connectionState = newState
    onConnectionStateChanged(newState)
  }

  private fun addDebugLog(message: String) {
    onDebugLog(ConnectionDebugLogEntry(timestampMs = System.currentTimeMillis(), message = message))
  }

  private val noopSdpObserver =
      object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit

        override fun onSetSuccess() = Unit

        override fun onCreateFailure(error: String?) = Unit

        override fun onSetFailure(error: String?) {
          addDebugLog("SDP set failure: ${error ?: "unknown"}")
          Log.e(TAG, "SDP set failure: $error")
        }
      }
}
