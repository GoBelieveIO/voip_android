package com.beetle.voip;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.TextureFrameConsumer;
import com.google.mediapipe.framework.TextureFrame;
import com.google.mediapipe.glutil.EglManager;

import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

public class FrameViewRenderer  extends SurfaceView implements SurfaceHolder.Callback, VideoSink {
    private static final String TAG = "FrameViewRenderer";
    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    private static final int NUM_BUFFERS = 2;

    FrameProcessor processor;
    EglManager eglManager;
    TextureConverter converter;
    private final RendererCommon.VideoLayoutMeasure videoLayoutMeasure =
            new RendererCommon.VideoLayoutMeasure();

    // Accessed only on the main thread.
    private int rotatedFrameWidth;
    private int rotatedFrameHeight;
    private int frameRotation;

    private boolean enableFixedSize;
    private int surfaceWidth;
    private int surfaceHeight;


    public FrameViewRenderer(Context context) {
        super(context);
        getHolder().addCallback(this);
    }

    public FrameViewRenderer(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }


    public void init(Context applicationContext, EglManager eglManager) {
        enableFixedSize = false;
        rotatedFrameWidth = 0;
        rotatedFrameHeight = 0;

        this.eglManager = eglManager;
        //this.eglManager = new EglManager(null);

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
                        "face_detection_mobile_gpu.binarypb",
                        "input_video",
                        "output_video");
        processor
                .getVideoSurfaceOutput()
                .setFlipY(
                        applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));

        processor.setConsumer(new TextureFrameConsumer() {
            @Override
            public void onNewFrame(TextureFrame frame) {
                Log.i(TAG, "ttt:" + frame.getWidth() + " h:" + frame.getHeight());
                frame.release();
            }
        });

        converter =
                new TextureConverter(
                        eglManager.getContext(),
                        applicationInfo.metaData.getInt("converterNumBuffers", NUM_BUFFERS));
        converter.setFlipY(
                applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));
        converter.setConsumer(processor);

    }

    // View layout interface.
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        ThreadUtils.checkIsOnMainThread();
        Point size =
                videoLayoutMeasure.measure(widthSpec, heightSpec, rotatedFrameWidth, rotatedFrameHeight);
        setMeasuredDimension(size.x, size.y);
        //logD("onMeasure(). New size: " + size.x + "x" + size.y);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        ThreadUtils.checkIsOnMainThread();
        updateSurfaceSize();
    }

    private void updateSurfaceSize() {
        ThreadUtils.checkIsOnMainThread();
        if (enableFixedSize && rotatedFrameWidth != 0 && rotatedFrameHeight != 0 && getWidth() != 0
                && getHeight() != 0) {
            final float layoutAspectRatio = getWidth() / (float) getHeight();
            final float frameAspectRatio = rotatedFrameWidth / (float) rotatedFrameHeight;
            final int drawnFrameWidth;
            final int drawnFrameHeight;
            if (frameAspectRatio > layoutAspectRatio) {
                drawnFrameWidth = (int) (rotatedFrameHeight * layoutAspectRatio);
                drawnFrameHeight = rotatedFrameHeight;
            } else {
                drawnFrameWidth = rotatedFrameWidth;
                drawnFrameHeight = (int) (rotatedFrameWidth / layoutAspectRatio);
            }
            // Aspect ratio of the drawn frame and the view is the same.
            final int width = Math.min(getWidth(), drawnFrameWidth);
            final int height = Math.min(getHeight(), drawnFrameHeight);
            Log.i(TAG, "updateSurfaceSize. Layout size: " + getWidth() + "x" + getHeight() + ", frame size: "
                    + rotatedFrameWidth + "x" + rotatedFrameHeight + ", requested surface size: " + width
                    + "x" + height + ", old surface size: " + surfaceWidth + "x" + surfaceHeight);
            if (width != surfaceWidth || height != surfaceHeight) {
                surfaceWidth = width;
                surfaceHeight = height;
                getHolder().setFixedSize(width, height);
            }
        } else {
            surfaceWidth = surfaceHeight = 0;
            getHolder().setSizeFromLayout();
        }
    }

    // VideoSink interface.
    @Override
    public void onFrame(VideoFrame frame) {
        Log.i(TAG, "renderer on frame w:" + frame.getRotatedWidth() + " h:" + frame.getRotatedHeight() + " rotation:" + frame.getRotation());
        if (rotatedFrameWidth != frame.getRotatedWidth()
                || rotatedFrameHeight != frame.getRotatedHeight()
                || frameRotation != frame.getRotation()) {

            VideoFrame.Buffer buffer = frame.getBuffer();
            if (buffer instanceof VideoFrame.TextureBuffer) {
                VideoFrame.TextureBuffer texture = (VideoFrame.TextureBuffer)buffer;
                converter.setSurfaceTexture(texture.getTextureId(), frame.getRotatedWidth(), frame.getRotatedHeight());
            }
            postOrRun(new Runnable() {
                @Override
                public void run() {
                    rotatedFrameWidth = frame.getRotatedWidth();
                    rotatedFrameHeight = frame.getRotatedHeight();
                    frameRotation = frame.getRotation();

                    updateSurfaceSize();
                    requestLayout();
                }
            });
            return;
        }


        VideoFrame.Buffer buffer = frame.getBuffer();
        /*
        if (buffer instanceof VideoFrame.I420Buffer) {
            VideoFrame.I420Buffer i420 = (VideoFrame.I420Buffer)buffer;
            FrameProcessor.YUVFrame yuvFrame = new FrameProcessor.YUVFrame();

            int width = i420.getWidth();
            int height = i420.getHeight();
            int uv_width = (width + 1) / 2;
            int uv_height = (height + 1) / 2;
            int y_stride = (width + 15) & ~15;
            int uv_stride = (uv_width + 15) & ~15;
            int y_size = y_stride * height;
            int uv_size = uv_stride * uv_height;


            yuvFrame.yBuffer = i420.getDataY();
            yuvFrame.uBuffer = i420.getDataU();
            yuvFrame.vBuffer = i420.getDataV();
            yuvFrame.width = i420.getWidth();
            yuvFrame.height = i420.getHeight();
            yuvFrame.timestamp = frame.getTimestampNs()/1000;

            processor.onNewFrame(yuvFrame, new ReleaseFrame(frame));


            Log.i(TAG, "renderer frame finished");
        }*/

        if (buffer instanceof VideoFrame.TextureBuffer) {
            VideoFrame.TextureBuffer texture = (VideoFrame.TextureBuffer)buffer;
            Log.i(TAG, "texture id:" + texture.getTextureId() + " w:" + texture.getWidth() + " h:" + texture.getHeight());
            converter.onFrame(frame);
        }
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
        ThreadUtils.checkIsOnMainThread();
        surfaceWidth = surfaceHeight = 0;
        updateSurfaceSize();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        processor.getVideoSurfaceOutput().setSurface(null);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}


    private void postOrRun(Runnable r) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            r.run();
        } else {
            post(r);
        }
    }



    static class ReleaseFrame {
        VideoFrame frame;
        public ReleaseFrame(VideoFrame f) {
            f.retain();
            this.frame = f;

        }
        public void release() {
            Log.i(TAG, "release video frame");
            this.frame.release();
        }
    }

}
