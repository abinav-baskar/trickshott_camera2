package com.example.android.camera2video;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MediaRecorderHandler {

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private PermissionHandler permissionHandler = new PermissionHandler();
    private PreviewSizeHandler previewSizeHandler = new PreviewSizeHandler();
    TextureView.SurfaceTextureListener mSurfaceTextureListener;
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    CameraDevice.StateCallback mStateCallback;


    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    void setupMSurfaceTextureListener(final Activity activity) {

        mSurfaceTextureListener
                = new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                                  int width, int height) {

                openCamera(width, height,activity);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                    int width, int height) {
                previewSizeHandler.configureTransform(width, height,activity,mTextureView,mPreviewSize);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        };

    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    void setupMStateCalback(final Activity activity, final ThreadHandler threadHandler, final Handler uiElementHandler ) {
        mStateCallback = new CameraDevice.StateCallback() {

            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                mCameraDevice = cameraDevice;
                startPreview(activity, threadHandler.mBackgroundHandler, uiElementHandler);

                mCameraOpenCloseLock.release();
                if (null != mTextureView) {
                    previewSizeHandler.configureTransform(mTextureView.getWidth(), mTextureView.getHeight(), activity, mTextureView, mPreviewSize);
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
                if (null != activity) {
                    activity.finish();
                }
            }
        };
    }

    String setUpMediaRecorder(final Activity activity, String mNextVideoAbsolutePath) throws IOException {
        if (null == activity) {
            return null;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = this.getVideoFilePath(activity); //Bub added 'this'
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
        return mNextVideoAbsolutePath;
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }

    MediaRecorder mMediaRecorder; //
    Size mVideoSize; //
    Size mPreviewSize; //
    CaptureRequest.Builder mPreviewBuilder; //
    CameraDevice mCameraDevice; //
    AutoFitTextureView mTextureView; //
    CameraCaptureSession mPreviewSession; //
    int mSensorOrientation; //
    List<Surface> surfaces;
    Boolean mIsRecordingVideo = false;
    /**
     * An int that refers to the current or intended open camera. 0 is back camera, 1 is front (selfie)
     */

    private int cameraLensDirection=CameraCharacteristics.LENS_FACING_FRONT;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    void switchCamera(Activity activity) {
        if (cameraLensDirection == CameraCharacteristics.LENS_FACING_BACK) {
            cameraLensDirection = CameraCharacteristics.LENS_FACING_FRONT;
            closeCamera();
            reopenCamera(activity);

        } else if (cameraLensDirection == CameraCharacteristics.LENS_FACING_FRONT) {
            cameraLensDirection = CameraCharacteristics.LENS_FACING_BACK;
            closeCamera();
            reopenCamera(activity);
        }
    }

    private void reopenCamera(Activity activity) {
        if ( mTextureView.isAvailable()) {
            openCamera( mTextureView.getWidth(),  mTextureView.getHeight(),activity);
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }


    void startRecordingVideo(final Activity activity, Handler mBackgroundHandler, final Handler uiElementHandler) {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            //  setUpMediaRecorder();

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;

                    updateUiPreview(uiElementHandler);

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI - set next text of button
                            Message message = Message.obtain();
                            Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
                            b.putBoolean("mButtonVideo.reverseText", true);
                            message.setData(b);
                            uiElementHandler.sendMessage(message);

                            mIsRecordingVideo = true;

                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    String stopRecordingVideo(final Activity activity, String mNextVideoAbsolutePath) {
        //mIsRecordingVideo = false;
        //  mButtonVideo.setText(R.string.record);
        mIsRecordingVideo = false;
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        if (null != activity) {
            Toast.makeText(activity, "Video saved: " + mNextVideoAbsolutePath,
                    Toast.LENGTH_SHORT).show();
        }
        return null;
    }


    @SuppressWarnings("MissingPermission")
    void openCamera(int width, int height, Activity activity) {
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String cameraId = manager.getCameraIdList()[cameraLensDirection];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            //mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mVideoSize = previewSizeHandler.chooseVideoSize(map.getOutputSizes(MediaRecorder.class));

            mPreviewSize = previewSizeHandler.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = activity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            //configureTransform(width, height);
            previewSizeHandler.configureTransform(width, height, activity, mTextureView, mPreviewSize);


            mMediaRecorder = new MediaRecorder();

            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    void startPreview(final Activity activity, Handler mBackgroundHandler, final Handler uiElementHandler) {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updateUiPreview(uiElementHandler);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            if (null != activity) {
                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void updateUiPreview(Handler uiElementHandler) {
        Message message = Message.obtain();
        Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
        b.putBoolean("updatePreview", true);
        message.setData(b);
        uiElementHandler.sendMessage(message);
    }

}


/*
Message message = Message.obtain();
            Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
            b.putBoolean("",uri.toString());
            message.setData(b);
            handler.sendMessage(message);
 */

/*
   Handler ssrHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
        }
    };
 */