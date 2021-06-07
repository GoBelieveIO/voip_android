package com.beetle.voip;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioTrack;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Peer connection client implementation.
 *
 * <p>All public methods are routed to local looper thread.
 * All PeerConnectionEvents callbacks are invoked from the same looper thread.
 * This class is a singleton.
 */
public class PeerConnectionClient {
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String VIDEO_TRACK_TYPE = "video";
    private static final String TAG = "PCRTCClient";
    private static final String VIDEO_CODEC_VP8 = "VP8";
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String VIDEO_CODEC_H264 = "H264";
    private static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
    private static final String VIDEO_CODEC_H264_HIGH = "H264 High";
    private static final String AUDIO_CODEC_OPUS = "opus";
    private static final String AUDIO_CODEC_ISAC = "ISAC";
    private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    private static final String VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    private static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
    private static final String DISABLE_WEBRTC_AGC_FIELDTRIAL =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
    private static final int HD_VIDEO_WIDTH = 1280;
    private static final int HD_VIDEO_HEIGHT = 720;
    private static final int BPS_IN_KBPS = 1000;
    private static final String RTCEVENTLOG_OUTPUT_DIR_NAME = "rtc_event_log";


    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();
    private final Timer statsTimer = new Timer();
    private final EglBase rootEglBase;
    private final Context appContext;
    private final PeerConnectionParameters peerConnectionParameters;
    private final PeerConnectionEvents events;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    PeerConnectionFactory.Options options = null;
    private AudioSource audioSource;

    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoSource videoSource;
    private boolean preferIsac;
    private String preferredVideoCodec;
    private boolean videoCapturerStopped;
    private boolean isError;

    ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();


    @Nullable
    private VideoSink localRender;
    @Nullable
    private List<VideoSink> remoteSinks;


    private int videoWidth;
    private int videoHeight;
    private int videoFps;
    private MediaConstraints audioConstraints;
    private ParcelFileDescriptor aecDumpFileDescriptor;
    private MediaConstraints sdpMediaConstraints;
    // Queued remote ICE candidates are consumed only after both local and
    // remote descriptions are set. Similarly local ICE candidates are sent to
    // remote peer after both local and remote description are set.
    private List<IceCandidate> queuedRemoteCandidates;

    private boolean isInitiator;
    private SessionDescription localSdp; // either offer or answer SDP
    private MediaStream mediaStream;
    private VideoCapturer videoCapturer;
    // enableVideo is set to true if video should be rendered and sent.
    private boolean renderVideo = true;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private RtpSender localVideoSender;
    // enableAudio is set to true if audio should be sent.
    private boolean enableAudio = true;
    private AudioTrack localAudioTrack;

    /**
     * Peer connection parameters.
     */
    public static class PeerConnectionParameters {
        public final boolean videoCallEnabled;
        public final boolean loopback;
        public final boolean tracing;
        public final int videoWidth;
        public final int videoHeight;
        public final int videoFps;
        public final int videoMaxBitrate;
        public final String videoCodec;
        public final boolean videoCodecHwAcceleration;
        public final boolean videoFlexfecEnabled;
        public final int audioStartBitrate;
        public final String audioCodec;
        public final boolean noAudioProcessing;
        public final boolean aecDump;
        public final boolean useOpenSLES;
        public final boolean disableBuiltInAEC;
        public final boolean disableBuiltInAGC;
        public final boolean disableBuiltInNS;
        public final boolean disableWebRtcAGCAndHPF;
        public final boolean enableLevelControl;

        public PeerConnectionParameters(boolean videoCallEnabled, boolean loopback, boolean tracing,
                                        int videoWidth, int videoHeight, int videoFps, int videoMaxBitrate, String videoCodec,
                                        boolean videoCodecHwAcceleration, int audioStartBitrate, String audioCodec,
                                        boolean noAudioProcessing, boolean aecDump, boolean useOpenSLES, boolean disableBuiltInAEC,
                                        boolean disableBuiltInAGC, boolean disableBuiltInNS, boolean enableLevelControl) {
            this.videoCallEnabled = videoCallEnabled;
            this.loopback = loopback;
            this.tracing = tracing;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.videoMaxBitrate = videoMaxBitrate;
            if (videoCodec == null) {
                this.videoCodec = VIDEO_CODEC_VP8;
            } else {
                this.videoCodec = videoCodec;
            }
            this.videoCodecHwAcceleration = videoCodecHwAcceleration;
            this.audioStartBitrate = audioStartBitrate;
            this.audioCodec = audioCodec;
            this.noAudioProcessing = noAudioProcessing;
            this.aecDump = aecDump;
            this.useOpenSLES = useOpenSLES;
            this.disableBuiltInAEC = disableBuiltInAEC;
            this.disableBuiltInAGC = disableBuiltInAGC;
            this.disableBuiltInNS = disableBuiltInNS;
            this.enableLevelControl = enableLevelControl;

            this.videoFlexfecEnabled = false;
            this.disableWebRtcAGCAndHPF = false;

        }
    }

    /**
     * Peer connection events.
     */
    public interface PeerConnectionEvents {
        /**
         * Callback fired once local SDP is created and set.
         */
        void onLocalDescription(final SessionDescription sdp);

        /**
         * Callback fired once local Ice candidate is generated.
         */
        void onIceCandidate(final IceCandidate candidate);

        /**
         * Callback fired once local ICE candidates are removed.
         */
        void onIceCandidatesRemoved(final IceCandidate[] candidates);

        /**
         * Callback fired once connection is established (IceConnectionState is
         * CONNECTED).
         */
        void onIceConnected();

        /**
         * Callback fired once connection is closed (IceConnectionState is
         * DISCONNECTED).
         */
        void onIceDisconnected();

        /**
         * Callback fired once peer connection is closed.
         */
        void onPeerConnectionClosed();

        /**
         * Callback fired once peer connection statistics is ready.
         */
        void onPeerConnectionStatsReady(final StatsReport[] reports);

        /**
         * Callback fired once peer connection error happened.
         */
        void onPeerConnectionError(final String description);
    }


    public PeerConnectionClient(Context appContext, EglBase eglBase,
                                PeerConnectionParameters peerConnectionParameters, PeerConnectionEvents events) {
        this.rootEglBase = eglBase;
        this.appContext = appContext;
        this.events = events;
        this.peerConnectionParameters = peerConnectionParameters;

        Log.d(TAG, "Preferred video codec: " + peerConnectionParameters.videoCodec);

        final String fieldTrials = getFieldTrials(peerConnectionParameters);
        executor.execute(() -> {
            Log.d(TAG, "Initialize WebRTC. Field trials: " + fieldTrials);
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                            .setFieldTrials(fieldTrials)
                            .setEnableInternalTracer(true)
                            .createInitializationOptions());
        });
    }


    /**
     * This function should only be called once.
     */
    public void createPeerConnectionFactory(PeerConnectionFactory.Options options) {
        if (factory != null) {
            throw new IllegalStateException("PeerConnectionFactory has already been constructed");
        }
        executor.execute(() -> createPeerConnectionFactoryInternal(options));
    }



    public void addIceServer(PeerConnection.IceServer server) {
        iceServers.add(server);
    }

    public void clearIceServer() {
        iceServers.clear();
    }


    public void setPeerConnectionFactoryOptions(PeerConnectionFactory.Options options) {
        this.options = options;
    }




    public void createPeerConnection(final VideoSink localRender, final VideoSink remoteSink,
                                     final VideoCapturer videoCapturer) {
        if (peerConnectionParameters.videoCallEnabled && videoCapturer == null) {
            Log.w(TAG, "Video call enabled but no video capturer provided.");
        }
        createPeerConnection(
                localRender, Collections.singletonList(remoteSink), videoCapturer);
    }


    public void createPeerConnection(final VideoSink localRender, final List<VideoSink> remoteSinks,
                                     final VideoCapturer videoCapturer) {
        if (peerConnectionParameters == null) {
            Log.e(TAG, "Creating peer connection without initializing factory.");
            return;
        }
        this.localRender = localRender;
        this.remoteSinks = remoteSinks;
        this.videoCapturer = videoCapturer;
        executor.execute(() -> {
            try {
                createMediaConstraintsInternal();
                createPeerConnectionInternal();
            } catch (Exception e) {
                reportError("Failed to create peer connection: " + e.getMessage());
                throw e;
            }
        });
    }



    public void close() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                closeInternal();
            }
        });
    }

    public boolean isVideoCallEnabled() {
        return peerConnectionParameters.videoCallEnabled && videoCapturer != null;
    }

    private void createPeerConnectionFactoryInternal(PeerConnectionFactory.Options options) {
        if (peerConnectionParameters.tracing) {
            PeerConnectionFactory.startInternalTracingCapture(
                    Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                            + "webrtc-trace.txt");
        }
        Log.d(TAG,
                "Create peer connection factory. Use video: " + peerConnectionParameters.videoCallEnabled);
        isError = false;

        // Check preferred video codec.
        preferredVideoCodec = VIDEO_CODEC_VP8;
        if (peerConnectionParameters.videoCodec != null) {
            if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_VP9)) {
                preferredVideoCodec = VIDEO_CODEC_VP9;
            } else if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_H264)) {
                preferredVideoCodec = VIDEO_CODEC_H264;
            }
        }
        Log.d(TAG, "Pereferred video codec: " + preferredVideoCodec);

        // Check if ISAC is used by default.
        preferIsac = peerConnectionParameters.audioCodec != null
                && peerConnectionParameters.audioCodec.equals(AUDIO_CODEC_ISAC);


        AudioDeviceModule adm = createJavaAudioDevice();
        // Create peer connection factory.
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        final boolean enableH264HighProfile =
                VIDEO_CODEC_H264_HIGH.equals(peerConnectionParameters.videoCodec);
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        if (peerConnectionParameters.videoCodecHwAcceleration) {
            encoderFactory = new DefaultVideoEncoderFactory(
                    rootEglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */, enableH264HighProfile);
            decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        } else {
            encoderFactory = new SoftwareVideoEncoderFactory();
            decoderFactory = new SoftwareVideoDecoderFactory();
        }

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        Log.d(TAG, "Peer connection factory created.");
        adm.release();

        Log.d(TAG, "Peer connection factory created.");
    }

    AudioDeviceModule createJavaAudioDevice() {
        // Enable/disable OpenSL ES playback.
        if (!peerConnectionParameters.useOpenSLES) {
            Log.w(TAG, "External OpenSLES ADM not implemented yet.");
            // TODO(magjed): Add support for external OpenSLES ADM.
        }
        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                reportError(errorMessage);
            }
        };

        return JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
                .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .createAudioDeviceModule();
    }


    private void createMediaConstraintsInternal() {
        // Create video constraints if video call is enabled.
        if (isVideoCallEnabled()) {
            videoWidth = peerConnectionParameters.videoWidth;
            videoHeight = peerConnectionParameters.videoHeight;
            videoFps = peerConnectionParameters.videoFps;

            // If video resolution is not specified, default to HD.
            if (videoWidth == 0 || videoHeight == 0) {
                videoWidth = HD_VIDEO_WIDTH;
                videoHeight = HD_VIDEO_HEIGHT;
            }

            // If fps is not specified, default to 30.
            if (videoFps == 0) {
                videoFps = 30;
            }
            Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);
        }

        // Create audio constraints.
        audioConstraints = new MediaConstraints();
        // added for audio performance measurements
        if (peerConnectionParameters.noAudioProcessing) {
            Log.d(TAG, "Disabling audio processing");
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
        }
        // Create SDP constraints.
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", Boolean.toString(isVideoCallEnabled())));
    }

    private void createPeerConnectionInternal() {
        if (factory == null || isError) {
            Log.e(TAG, "Peerconnection factory is not created");
            return;
        }
        Log.d(TAG, "Create peer connection.");

        queuedRemoteCandidates = new ArrayList<>();

        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // Enable DTLS for normal calls and disable for loopback calls.
        rtcConfig.enableDtlsSrtp = !peerConnectionParameters.loopback;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(rtcConfig, pcObserver);

        isInitiator = false;

        // Set INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

        List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
        if (isVideoCallEnabled()) {
            peerConnection.addTrack(createVideoTrack(videoCapturer), mediaStreamLabels);
            // We can add the renderers right away because we don't need to wait for an
            // answer to get the remote track.
//            remoteVideoTrack = getRemoteVideoTrack();
//            remoteVideoTrack.setEnabled(renderVideo);
//            for (VideoSink remoteSink : remoteSinks) {
//                remoteVideoTrack.addSink(remoteSink);
//            }
        }
        peerConnection.addTrack(createAudioTrack(), mediaStreamLabels);
        if (isVideoCallEnabled()) {
            findVideoSender();
        }

        if (peerConnectionParameters.aecDump) {
            try {
                ParcelFileDescriptor aecDumpFileDescriptor =
                        ParcelFileDescriptor.open(new File(Environment.getExternalStorageDirectory().getPath()
                                        + File.separator + "Download/audio.aecdump"),
                                ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
                                        | ParcelFileDescriptor.MODE_TRUNCATE);
                factory.startAecDump(aecDumpFileDescriptor.detachFd(), -1);
            } catch (IOException e) {
                Log.e(TAG, "Can not open aecdump file", e);
            }
        }
        Log.d(TAG, "Peer connection created.");
    }


    private void closeInternal() {
        if (factory != null && peerConnectionParameters.aecDump) {
            factory.stopAecDump();
        }
        Log.d(TAG, "Closing peer connection.");
        statsTimer.cancel();
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        Log.d(TAG, "Closing audio source.");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        Log.d(TAG, "Stopping capture.");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        Log.d(TAG, "Closing video source.");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        localRender = null;
        remoteSinks = null;
        Log.d(TAG, "Closing peer connection factory.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        options = null;
        rootEglBase.release();

        Log.d(TAG, "Closing peer connection done.");
        events.onPeerConnectionClosed();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
    }

    public boolean isHDVideo() {
        if (!isVideoCallEnabled()) {
            return false;
        }

        return videoWidth * videoHeight >= 1280 * 720;
    }

    private void getStats() {
        if (peerConnection == null || isError) {
            return;
        }
        boolean success = peerConnection.getStats(new StatsObserver() {
            @Override
            public void onComplete(final StatsReport[] reports) {
                events.onPeerConnectionStatsReady(reports);
            }
        }, null);
        if (!success) {
            Log.e(TAG, "getStats() returns false!");
        }
    }

    public void enableStatsEvents(boolean enable, int periodMs) {
        if (enable) {
            try {
                statsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                getStats();
                            }
                        });
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                Log.e(TAG, "Can not schedule statistics timer", e);
            }
        } else {
            statsTimer.cancel();
        }
    }

    public void setAudioEnabled(final boolean enable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                enableAudio = enable;
                if (localAudioTrack != null) {
                    localAudioTrack.setEnabled(enableAudio);
                }
            }
        });
    }

    public void setVideoEnabled(final boolean enable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                renderVideo = enable;
                if (localVideoTrack != null) {
                    localVideoTrack.setEnabled(renderVideo);
                }
                if (remoteVideoTrack != null) {
                    remoteVideoTrack.setEnabled(renderVideo);
                }
            }
        });
    }



    public void toogleVideo() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                renderVideo = !renderVideo;
                if (localVideoTrack != null) {
                    localVideoTrack.setEnabled(renderVideo);
                }
                if (remoteVideoTrack != null) {
                    remoteVideoTrack.setEnabled(renderVideo);
                }
            }
        });
    }

    public void toogleAudio() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                enableAudio = !enableAudio;
                if (localAudioTrack != null) {
                    localAudioTrack.setEnabled(enableAudio);
                }
            }
        });
    }

    public void createOffer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    Log.d(TAG, "PC Create OFFER");
                    isInitiator = true;
                    peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void createAnswer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    Log.d(TAG, "PC create ANSWER");
                    isInitiator = false;
                    peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    if (queuedRemoteCandidates != null) {
                        queuedRemoteCandidates.add(candidate);
                    } else {
                        peerConnection.addIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null || isError) {
                    return;
                }
                // Drain the queued remote candidates if there is any so that
                // they are processed in the proper order.
                drainCandidates();
                peerConnection.removeIceCandidates(candidates);
            }
        });
    }

    public void setRemoteDescription(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null || isError) {
                    return;
                }
                String sdpDescription = sdp.description;
                if (preferIsac) {
                    sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
                }
                if (isVideoCallEnabled()) {
                    sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
                }
                Log.i(TAG, "remote sdp:" + sdpDescription);

                if (peerConnectionParameters.audioStartBitrate > 0) {
                    sdpDescription = setStartBitrate(
                            AUDIO_CODEC_OPUS, false, sdpDescription, peerConnectionParameters.audioStartBitrate);
                }
                Log.d(TAG, "Set remote SDP.");
                SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
                peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
            }
        });
    }

    public void stopVideoSource() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (videoCapturer != null && !videoCapturerStopped) {
                    Log.d(TAG, "Stop video source.");
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                    }
                    videoCapturerStopped = true;
                }
            }
        });
    }

    public void startVideoSource() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (videoCapturer != null && videoCapturerStopped) {
                    Log.d(TAG, "Restart video source.");
                    videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
                    videoCapturerStopped = false;
                }
            }
        });
    }

    public void setVideoMaxBitrate(final Integer maxBitrateKbps) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null || localVideoSender == null || isError) {
                    return;
                }
                Log.d(TAG, "Requested max video bitrate: " + maxBitrateKbps);
                if (localVideoSender == null) {
                    Log.w(TAG, "Sender is not ready.");
                    return;
                }

                RtpParameters parameters = localVideoSender.getParameters();
                if (parameters.encodings.size() == 0) {
                    Log.w(TAG, "RtpParameters are not ready.");
                    return;
                }

                for (RtpParameters.Encoding encoding : parameters.encodings) {
                    // Null value means no limit.
                    encoding.maxBitrateBps = maxBitrateKbps == null ? null : maxBitrateKbps * BPS_IN_KBPS;
                }
                if (!localVideoSender.setParameters(parameters)) {
                    Log.e(TAG, "RtpSender.setParameters failed.");
                }
                Log.d(TAG, "Configured max video bitrate to: " + maxBitrateKbps);
            }
        });
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, "Peerconnection error: " + errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    events.onPeerConnectionError(errorMessage);
                    isError = true;
                }
            }
        });
    }

    private AudioTrack createAudioTrack() {
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(enableAudio);
        return localAudioTrack;
    }

    @Nullable
    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(capturer.isScreencast());
        capturer.initialize(surfaceTextureHelper, appContext, videoSource.getCapturerObserver());
        capturer.startCapture(videoWidth, videoHeight, videoFps);

        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(renderVideo);
        localVideoTrack.addSink(localRender);
        return localVideoTrack;
    }


    private void findVideoSender() {
        for (RtpSender sender : peerConnection.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();
                if (trackType.equals(VIDEO_TRACK_TYPE)) {
                    Log.d(TAG, "Found video sender.");
                    localVideoSender = sender;
                }
            }
        }
    }

    private static String getSdpVideoCodecName(PeerConnectionParameters parameters) {
        switch (parameters.videoCodec) {
            case VIDEO_CODEC_VP8:
                return VIDEO_CODEC_VP8;
            case VIDEO_CODEC_VP9:
                return VIDEO_CODEC_VP9;
            case VIDEO_CODEC_H264_HIGH:
            case VIDEO_CODEC_H264_BASELINE:
                return VIDEO_CODEC_H264;
            default:
                return VIDEO_CODEC_VP8;
        }
    }

    private static String getFieldTrials(PeerConnectionParameters peerConnectionParameters) {
        String fieldTrials = "";
        if (peerConnectionParameters.videoFlexfecEnabled) {
            fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
            Log.d(TAG, "Enable FlexFEC field trial.");
        }
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
            fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;
            Log.d(TAG, "Disable WebRTC AGC field trial.");
        }
        return fieldTrials;
    }


    private static String setStartBitrate(
            String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;
        // Search for codec rtpmap in format
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);

        // Check if a=fmtp string already exist in remote SDP for this codec and
        // update it with new bitrate parameter.
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            // Append new a=fmtp line if no such line exist for a codec.
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;
                if (isVideoCodec) {
                    bitrateSet =
                            "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "="
                            + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }
        return newSdpDescription.toString();
    }

    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        String[] lines = sdpDescription.split("\r\n");
        int mLineIndex = -1;
        String codecRtpMap = null;
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        String mediaDescription = "m=video ";
        if (isAudio) {
            mediaDescription = "m=audio ";
        }
        for (int i = 0; (i < lines.length) && (mLineIndex == -1 || codecRtpMap == null); i++) {
            if (lines[i].startsWith(mediaDescription)) {
                mLineIndex = i;
                continue;
            }
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
            }
        }
        if (mLineIndex == -1) {
            Log.w(TAG, "No " + mediaDescription + " line, so can't prefer " + codec);
            return sdpDescription;
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec);
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + ", prefer at " + lines[mLineIndex]);
        String[] origMLineParts = lines[mLineIndex].split(" ");
        if (origMLineParts.length > 3) {
            StringBuilder newMLine = new StringBuilder();
            int origPartIndex = 0;
            // Format is: m=<media> <port> <proto> <fmt> ...
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(codecRtpMap);
            for (; origPartIndex < origMLineParts.length; origPartIndex++) {
                if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
                    newMLine.append(" ").append(origMLineParts[origPartIndex]);
                }
            }
            lines[mLineIndex] = newMLine.toString();
            Log.d(TAG, "Change media description: " + lines[mLineIndex]);
        } else {
            Log.e(TAG, "Wrong SDP media description format: " + lines[mLineIndex]);
        }
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }

    private void drainCandidates() {
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidate candidate : queuedRemoteCandidates) {
                peerConnection.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }

    private void switchCameraInternal() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            if (!isVideoCallEnabled() || isError || videoCapturer == null) {
                Log.e(TAG, "Failed to switch camera. Video: " + isVideoCallEnabled() + ". Error : " + isError);
                return; // No video is sent or only one camera is available or error happened.
            }
            Log.d(TAG, "Switch camera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        } else if (videoCapturer instanceof ExternalCapturer) {
            if (!isVideoCallEnabled() || isError || videoCapturer == null) {
                Log.e(TAG, "Failed to switch camera. Video: " + isVideoCallEnabled() + ". Error : " + isError);
                return; // No video is sent or only one camera is available or error happened.
            }
            Log.d(TAG, "Switch camera");
            ExternalCapturer externalCapturer = (ExternalCapturer) videoCapturer;
            externalCapturer.switchCamera();
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }
    }

    public void switchCamera() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                switchCameraInternal();
            }
        });
    }

    public void changeCaptureFormat(final int width, final int height, final int framerate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                changeCaptureFormatInternal(width, height, framerate);
            }
        });
    }

    private void changeCaptureFormatInternal(int width, int height, int framerate) {
        if (!isVideoCallEnabled() || isError || videoCapturer == null) {
            Log.e(TAG,
                    "Failed to change capture format. Video: " + isVideoCallEnabled() + ". Error : " + isError);
            return;
        }
        Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
        videoSource.adaptOutputFormat(width, height, framerate);
    }

    // Implementation detail: observe ICE & stream changes and react accordingly.
    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidate(candidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidatesRemoved(candidates);
                }
            });
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
            Log.d(TAG, "SignalingState: " + newState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "IceConnectionState: " + newState);
                    if (newState == IceConnectionState.CONNECTED) {
                        events.onIceConnected();
                    } else if (newState == IceConnectionState.DISCONNECTED) {
                        events.onIceDisconnected();
                    } else if (newState == IceConnectionState.FAILED) {
                        reportError("ICE connection failed.");
                    }
                }
            });
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null || isError) {
                        return;
                    }
                    if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                        reportError("Weird-looking stream: " + stream);
                        return;
                    }
                    if (stream.videoTracks.size() == 1) {
                        remoteVideoTrack = stream.videoTracks.get(0);
                        remoteVideoTrack.setEnabled(renderVideo);
                        for (VideoSink remoteRender : remoteSinks) {
                            remoteVideoTrack.addSink(remoteRender);
                        }
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    remoteVideoTrack = null;
                }
            });
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            reportError("AppRTC doesn't use data channels, but got: " + dc.label() + " anyway!");
        }

        @Override
        public void onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon
            // signaling/negotiation protocol.
        }

        @Override
        public void onAddTrack(RtpReceiver var1, MediaStream[] var2) {

        }
    }

    // Implementation detail: handle offer creation/signaling and answer setting,
    // as well as adding remote ICE candidates once the answer SDP is set.
    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            if (localSdp != null) {
                reportError("Multiple SDP create.");
                return;
            }
            String sdpDescription = origSdp.description;
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
            }
            if (isVideoCallEnabled()) {
                sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
            }
            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
            localSdp = sdp;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection != null && !isError) {
                        Log.d(TAG, "Set local SDP from " + sdp.type);
                        peerConnection.setLocalDescription(sdpObserver, sdp);
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null || isError) {
                        return;
                    }
                    if (isInitiator) {
                        // For offering peer connection we first create offer and set
                        // local SDP, then after receiving answer set remote SDP.
                        if (peerConnection.getRemoteDescription() == null) {
                            // We've just set our local SDP so time to send it.
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                        } else {
                            // We've just set remote description, so drain remote
                            // and send local ICE candidates.
                            Log.d(TAG, "Remote SDP set succesfully");
                            drainCandidates();
                        }
                    } else {
                        // For answering peer connection we set remote SDP and then
                        // create answer and set local SDP.
                        if (peerConnection.getLocalDescription() != null) {
                            // We've just set our local SDP so time to send it, drain
                            // remote and send local ICE candidates.
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                            drainCandidates();
                        } else {
                            // We've just set remote SDP - do nothing for now -
                            // answer will be created soon.
                            Log.d(TAG, "Remote SDP set succesfully");
                        }
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(final String error) {
            reportError("createSDP error: " + error);
        }

        @Override
        public void onSetFailure(final String error) {
            reportError("setSDP error: " + error);
        }
    }
}
