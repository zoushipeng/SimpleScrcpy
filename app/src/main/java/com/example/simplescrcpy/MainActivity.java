package com.example.simplescrcpy;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.simplescrcpy.video.ScreenEncoder;
import com.example.simplescrcpy.video.gles.EglCore;
import com.example.simplescrcpy.video.gles.FullFrameRect;
import com.example.simplescrcpy.video.gles.Texture2dProgram;
import com.example.simplescrcpy.video.gles.WindowSurface;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "MainActivity";

    private static final int port = 45450;

    private Camera mCamera;
    private int mCameraPreviewThousandFps;
    private SurfaceHolder mHolder;


    private static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 720;
    private static final int DESIRED_PREVIEW_FPS = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{ Manifest.permission.INTERNET, Manifest.permission.CAMERA}, 1);
        }

        SurfaceView mSurface = findViewById(R.id.camera);
        mHolder = mSurface.getHolder();
        mHolder.addCallback(this);

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // todo start send h264 data
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!PermissionHelper.hasCameraPermission(this)) {
            PermissionHelper.requestCameraPermission(this, false);
        } else  {
            if (mCamera == null) {
                // Ideally, the frames from the camera are at the same resolution as the input to
                // the video encoder so we don't have to scale.
                openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
            }
            try {
                startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p>
     * Sets mCameraPreviewFps to the expected frame rate (which might actually be variable).
     */
    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();

        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);

        mCamera.setParameters(parms);

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.i(TAG, "Camera config: " + previewFacts);

        AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.continuousCapture_afl);

        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if(display.getRotation() == Surface.ROTATION_0) {
            mCamera.setDisplayOrientation(90);
            layout.setAspectRatio((double) cameraPreviewSize.height / cameraPreviewSize.width);
        } else if(display.getRotation() == Surface.ROTATION_270) {
            layout.setAspectRatio((double) cameraPreviewSize.height / cameraPreviewSize.width);
            mCamera.setDisplayOrientation(180);
        } else {
            // Set the preview aspect ratio.
            layout.setAspectRatio((double) cameraPreviewSize.width / cameraPreviewSize.height);
        }
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    private void startPreview() throws IOException {
        if (mCamera != null) {
            Log.d(TAG, "starting camera preview");
            try {
                mCamera.setPreviewDisplay(mHolder);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mCamera.startPreview();
        }
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(mCamera != null){
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private static class ServerThread implements Runnable{

        private static final String TAG = "ServerThread";

        private ServerSocketChannel channel;
        private Surface mSurface;
        private int w;
        private int h;

        ServerThread(SurfaceView surface){
            try {
                mSurface = surface.getHolder().getSurface();
                w = surface.getWidth(); h = surface.getHeight();

                w = 1080;h = 1080;

                channel = ServerSocketChannel.open();
                channel.socket().setReuseAddress(true);
                channel.socket().bind(new InetSocketAddress(port));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            SocketChannel client = null;
            try {
                client = channel.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(client == null){
                Log.e(TAG, "client null. exit");
                return;
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    String address = client.getRemoteAddress().toString();
                    Log.e(TAG, address + " connected ");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                ScreenEncoder endcoder = new ScreenEncoder(false, 2000000, 30);
                endcoder.streamScreen(client, mSurface, w, h);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
