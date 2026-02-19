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
import java.util.UUID
import java.util.concurrent.TimeUnit

class ErmeteConnectionManager(private val context: Context, private val scope: CoroutineScope) {
  companion object {
    private const val TAG = "ErmeteConnection"
    private const val AUDIO_TRACK_ID = "iris-audio-track"
    private const val STREAM_ID = "iris-stream"
  }

  private val httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
  private var wsClient: OkHttpClient? = null
  private var ws: WebSocket? = null

  private var peerConnectionFactory: PeerConnectionFactory? = null
  private var peerConnection: PeerConnection? = null
  private var audioSource: AudioSource? = null
  private var audioTrack: AudioTrack? = null
  private var cmdDataChannel: org.webrtc.DataChannel? = null

  fun connect(serverHttpUrl: String) {
    close()

    val wsUrl = serverHttpUrl.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/v1/ws"

    initializePeerConnectionFactory()
    createPeerConnection()

    wsClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    ws = wsClient!!.newWebSocket(Request.Builder().url(wsUrl).build(), webSocketListener)
  }

  fun sendFrame(serverHttpUrl: String, bytes: ByteArray) {
    scope.launch(Dispatchers.IO) {
      runCatching {
            val request =
                Request.Builder()
                    .url("$serverHttpUrl/v1/frames")
                    .addHeader("Content-Type", "image/jpeg")
                    .addHeader("X-Frame-Id", UUID.randomUUID().toString())
                    .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()
            httpClient.newCall(request).execute().use { response ->
              if (!response.isSuccessful) {
                Log.e(TAG, "Frame upload failed: ${response.code}")
              }
            }
          }
          .onFailure { Log.e(TAG, "Frame upload error", it) }
    }
  }

  fun sendPing() {
    val payload = JSONObject().put("type", "ping").put("text", "hello from iris")
    cmdDataChannel?.send(org.webrtc.DataChannel.Buffer(java.nio.ByteBuffer.wrap(payload.toString().toByteArray()), false))
  }

  fun close() {
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
  }

  private fun initializePeerConnectionFactory() {
    if (peerConnectionFactory != null) return

    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions())

    val adm = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
    peerConnectionFactory =
        PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
  }

  private fun createPeerConnection() {
    val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
    peerConnection =
        peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
              override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

              override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit

              override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

              override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit

              override fun onIceCandidate(candidate: IceCandidate) {
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
                cmdDataChannel = dataChannel
                dataChannel.registerObserver(
                    object : org.webrtc.DataChannel.Observer {
                      override fun onBufferedAmountChange(previousAmount: Long) = Unit

                      override fun onStateChange() = Unit

                      override fun onMessage(buffer: org.webrtc.DataChannel.Buffer) {
                        val bytes = ByteArray(buffer.data.remaining())
                        buffer.data.get(bytes)
                        Log.d(TAG, "DataChannel recv: ${String(bytes)}")
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
    cmdDataChannel = outboundDataChannel
  }

  private fun createAndSendOffer() {
    val pc = peerConnection ?: return
    pc.createOffer(
        object : org.webrtc.SdpObserver {
          override fun onCreateSuccess(description: SessionDescription?) {
            description ?: return
            pc.setLocalDescription(noopSdpObserver, description)
            val offer = JSONObject().put("type", "offer").put("sdp", description.description)
            ws?.send(offer.toString())
          }

          override fun onSetSuccess() = Unit

          override fun onCreateFailure(error: String?) {
            Log.e(TAG, "Offer failed: $error")
          }

          override fun onSetFailure(error: String?) = Unit
        },
        MediaConstraints())
  }

  private val webSocketListener =
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          createAndSendOffer()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          runCatching {
                val json = JSONObject(text)
                when (json.getString("type")) {
                  "answer" -> {
                    val sdp = json.getString("sdp")
                    peerConnection?.setRemoteDescription(
                        noopSdpObserver,
                        SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    sendPing()
                  }
                  "candidate" -> {
                    val candidate = json.getJSONObject("candidate")
                    peerConnection?.addIceCandidate(
                        IceCandidate(
                            candidate.optString("sdpMid", "0"),
                            candidate.getInt("sdpMLineIndex"),
                            candidate.getString("candidate")))
                  }
                  "bye" -> close()
                  "error" -> Log.e(TAG, "Server error: ${json.optString("message")}")
                }
              }
              .onFailure { Log.e(TAG, "WS parse error", it) }
        }
      }

  private val noopSdpObserver =
      object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit

        override fun onSetSuccess() = Unit

        override fun onCreateFailure(error: String?) = Unit

        override fun onSetFailure(error: String?) {
          Log.e(TAG, "SDP set failure: $error")
        }
      }
}
