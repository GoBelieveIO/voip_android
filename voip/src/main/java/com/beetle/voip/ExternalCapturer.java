/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.beetle.voip;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;

import android.util.Log;
import android.util.Size;
import android.view.Surface;


import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.components.TextureFrameConsumer;
import com.google.mediapipe.framework.Compat;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.framework.TextureFrame;
import com.google.mediapipe.glutil.EglManager;


import org.webrtc.CapturerObserver;

import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.HashMap;
import java.util.Map;


public class ExternalCapturer implements VideoCapturer, LifecycleOwner {

  final static boolean ENABLE_FACE_EFFECT = true;

  // Side packet / stream names.
  private static final String USE_FACE_DETECTION_INPUT_SOURCE_INPUT_SIDE_PACKET_NAME =
          "use_face_detection_input_source";
  private static final String SELECTED_EFFECT_ID_INPUT_STREAM_NAME = "selected_effect_id";
  private static final String OUTPUT_FACE_GEOMETRY_STREAM_NAME = "multi_face_geometry";

  private static final String EFFECT_SWITCHING_HINT_TEXT = "Tap to switch between effects!";

  private static final boolean USE_FACE_DETECTION_INPUT_SOURCE = false;
  private static final int MATRIX_TRANSLATION_Z_INDEX = 14;

  private static final int SELECTED_EFFECT_ID_AXIS = 0;
  private static final int SELECTED_EFFECT_ID_FACEPAINT = 1;
  private static final int SELECTED_EFFECT_ID_GLASSES = 2;


  private final static String TAG = "ExternalCapturer";
  private static final boolean FLIP_FRAMES_VERTICALLY = true;
  private static final int NUM_BUFFERS = 2;

  CameraHelper.CameraFacing cameraFacing;
  CameraXPreviewHelper cameraHelper;

  SurfaceTexture previewFrameTexture;


  int width;
  int height;

  Context applicationContext;
  SurfaceTextureHelper surfaceTextureHelper;
  private CapturerObserver capturerObserver;

  FrameProcessor processor;
  EglManager eglManager;

  ExternalTextureConverter converter;

  private final Object effectSelectionLock = new Object();
  private int selectedEffectId;

  private LifecycleRegistry lifecycleRegistry;

  public ExternalCapturer() {


    cameraFacing = CameraHelper.CameraFacing.FRONT;

    lifecycleRegistry = new LifecycleRegistry(this);
    lifecycleRegistry.markState(Lifecycle.State.RESUMED);

  }


  @Override
  public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext,
                         CapturerObserver capturerObserver) {
    this.capturerObserver = capturerObserver;
    this.surfaceTextureHelper = surfaceTextureHelper;

    this.applicationContext = applicationContext;

    this.eglManager = new EglManager(null);

    ApplicationInfo applicationInfo;
    try {
      applicationInfo =
              applicationContext.getPackageManager().getApplicationInfo(applicationContext.getPackageName(), PackageManager.GET_META_DATA);
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Cannot find application info: " + e);
      return;
    }

    processor =
            new FrameProcessor(
                    applicationContext,
                    eglManager.getNativeContext(),
                    applicationInfo.metaData.getString("binaryGraphName"),
                    applicationInfo.metaData.getString("inputVideoStreamName"),
                    applicationInfo.metaData.getString("outputVideoStreamName"));
    processor
            .getVideoSurfaceOutput()
            .setFlipY(
                    applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));


    if (ENABLE_FACE_EFFECT) {
      // By default, render the axis effect for the face detection input source and the glasses effect
      // for the face landmark input source.
      if (USE_FACE_DETECTION_INPUT_SOURCE) {
        selectedEffectId = SELECTED_EFFECT_ID_AXIS;
      } else {
        selectedEffectId = SELECTED_EFFECT_ID_GLASSES;
      }

      // Pass the USE_FACE_DETECTION_INPUT_SOURCE flag value as an input side packet into the graph.
      Map<String, Packet> inputSidePackets = new HashMap<>();
      inputSidePackets.put(
              USE_FACE_DETECTION_INPUT_SOURCE_INPUT_SIDE_PACKET_NAME,
              processor.getPacketCreator().createBool(USE_FACE_DETECTION_INPUT_SOURCE));
      processor.setInputSidePackets(inputSidePackets);


      // This callback demonstrates how the output face geometry packet can be obtained and used
      // in an Android app. As an example, the Z-translation component of the face pose transform
      // matrix is logged for each face being equal to the approximate distance away from the camera
      // in centimeters.
      processor.addPacketCallback(
              OUTPUT_FACE_GEOMETRY_STREAM_NAME,
              (packet) -> {
//                effectSwitchingHintView.post(
//                        () ->
//                                effectSwitchingHintView.setVisibility(
//                                        USE_FACE_DETECTION_INPUT_SOURCE ? View.INVISIBLE : View.VISIBLE));

                Log.d(TAG, "Received a multi face geometry packet.");
                /*
                List<FaceGeometry> multiFaceGeometry =
                        PacketGetter.getProtoVector(packet, FaceGeometry.parser());

                StringBuilder approxDistanceAwayFromCameraLogMessage = new StringBuilder();
                for (FaceGeometry faceGeometry : multiFaceGeometry) {
                  if (approxDistanceAwayFromCameraLogMessage.length() > 0) {
                    approxDistanceAwayFromCameraLogMessage.append(' ');
                  }
                  MatrixData poseTransformMatrix = faceGeometry.getPoseTransformMatrix();
                  approxDistanceAwayFromCameraLogMessage.append(
                          -poseTransformMatrix.getPackedData(MATRIX_TRANSLATION_Z_INDEX));
                }

                Log.d(
                        TAG,
                        "[TS:"
                                + packet.getTimestamp()
                                + "] size = "
                                + multiFaceGeometry.size()
                                + "; approx. distance away from camera in cm for faces = ["
                                + approxDistanceAwayFromCameraLogMessage
                                + "]");*/
              });


      // Alongside the input camera frame, we also send the `selected_effect_id` int32 packet to
      // indicate which effect should be rendered on this frame.
      processor.setOnWillAddFrameListener(
              (timestamp) -> {
                Packet selectedEffectIdPacket = null;
                try {
                  synchronized (effectSelectionLock) {
                    selectedEffectIdPacket = processor.getPacketCreator().createInt32(selectedEffectId);
                  }

                  processor
                          .getGraph()
                          .addPacketToInputStream(
                                  SELECTED_EFFECT_ID_INPUT_STREAM_NAME, selectedEffectIdPacket, timestamp);
                } catch (RuntimeException e) {
                  Log.e(
                          TAG, "Exception while adding packet to input stream while switching effects: " + e);
                } finally {
                  if (selectedEffectIdPacket != null) {
                    selectedEffectIdPacket.release();
                  }
                }
              });


    }

  }

  @Override
  public void startCapture(int width, int height, int framerate) {
    this.width = width;
    this.height = height;


    this.surfaceTextureHelper.getSurfaceTexture();
    this.surfaceTextureHelper.startListening(new VideoSink() {
      @Override
      public void onFrame(VideoFrame videoFrame) {
        Log.i(TAG, "on video frame width:" + videoFrame.getBuffer().getWidth() + " height:" + videoFrame.getBuffer().getHeight());
        capturerObserver.onFrameCaptured(videoFrame);
      }
    });



    ApplicationInfo applicationInfo;
    try {
      applicationInfo =
              applicationContext.getPackageManager().getApplicationInfo(applicationContext.getPackageName(), PackageManager.GET_META_DATA);
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Cannot find application info: " + e);
      return;
    }

    converter =
            new ExternalTextureConverter(
                    eglManager.getContext(),
                    applicationInfo.metaData.getInt("converterNumBuffers", NUM_BUFFERS));
    converter.setFlipY(
            applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));
    converter.setConsumer(processor);


    Surface s = new Surface(surfaceTextureHelper.getSurfaceTexture());
    processor.getVideoSurfaceOutput().setSurface(s);


    startCamera();
  }


  protected void onCameraStarted(SurfaceTexture surfaceTexture) {
    Log.i(TAG, "onCameraStarted");

    previewFrameTexture = surfaceTexture;

    Size displaySize = cameraHelper.getFrameSize();
    int w = displaySize.getWidth();
    int h = displaySize.getHeight();
    if (cameraHelper.isCameraRotated()) {
      w = displaySize.getHeight();
      h = displaySize.getWidth();
    }

    this.surfaceTextureHelper.setTextureSize(w, h);
    converter.setSurfaceTextureAndAttachToGLContext(
            previewFrameTexture, w , h);
  }


  @Override
  public void stopCapture() throws InterruptedException {
    cameraHelper.stopCamera(this.applicationContext);
    this.surfaceTextureHelper.stopListening();

    processor.getVideoSurfaceOutput().setSurface(null);

    converter.close();
  }


  @NonNull
  @Override
  public Lifecycle getLifecycle() {
    return lifecycleRegistry;
  }


  private void startCamera() {
    cameraHelper = new CameraXPreviewHelper();

    cameraHelper.setOnCameraStartedListener(
            surfaceTexture -> {
              onCameraStarted(surfaceTexture);
            });

    Size size = new Size(this.width, this.height);
    cameraHelper.startCamera(this.applicationContext, this, cameraFacing, size);
  }

  public void switchCamera() {
    if (cameraFacing == CameraHelper.CameraFacing.FRONT) {
      cameraFacing = CameraHelper.CameraFacing.BACK;
    } else {
      cameraFacing = CameraHelper.CameraFacing.FRONT;
    }

    cameraHelper.stopCamera(applicationContext);
    startCamera();
  }


  @Override
  public void changeCaptureFormat(int width, int height, int framerate) {
    // Empty on purpose
  }

  @Override
  public void dispose() {

  }

  @Override
  public boolean isScreencast() {
    return false;
  }
}
