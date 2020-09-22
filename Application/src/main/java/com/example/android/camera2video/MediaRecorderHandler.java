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
import java.io.FileOutputStream;
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
    private PreviewSizeHandler previewSizeHandler = new PreviewSizeHandler();
    TextureView.SurfaceTextureListener mSurfaceTextureListener;
    private int fileBeingWrittenTo = 0;
    private long maxFileSize;
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    CameraDevice.StateCallback mStateCallback;


    public MediaRecorderHandler() {
        maxFileSize = PermissionStatus.getAvailableExternalMemorySize();
    }

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

    long getFileSizeFromDuration(int encodingBitRate, long durationMillis) {
        return durationMillis*durationMillis/1000;
    }

    void setUpMediaRecorder(final Activity activity, final FileProcessor fileProcessor, int fileType,final Handler uiElementHandler) throws IOException {
                fileBeingWrittenTo = 0;
                if (null == activity) {
                    return;
                }
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); //or defualt?

                mMediaRecorder.setOutputFile(fileProcessor.getNextFile(activity, fileBeingWrittenTo).getAbsolutePath());
                fileBeingWrittenTo = 1;

                int encodingBitRate = 10000000;
                mMediaRecorder.setVideoEncodingBitRate(encodingBitRate);
                mMediaRecorder.setVideoFrameRate(30);
                mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
              //  mMediaRecorder.setMaxFileSize(getFileSizeFromDuration(encodingBitRate,5000));
                mMediaRecorder.setMaxFileSize(maxFileSize);
                int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                switch (mSensorOrientation) {
                    case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                        mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                        break;
                    case SENSOR_ORIENTATION_INVERSE_DEGREES:
                        mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                        break;
                }

                mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                    @Override
                    public void onInfo(MediaRecorder mediaRecorder, int i, int i1) { //maxDuration auto-stops mr as well, no guarantee it's stopped yet though
                        if(i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING) { //later we'll listen for max_duration_approaching and then set next output file
                            System.out.println("DBG: Max filesize approaching");

                            try {
                                mMediaRecorder.setNextOutputFile(fileProcessor.getNextFile(activity,fileBeingWrittenTo));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else if(i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                            System.out.println("DBG: Max duration reached");
                            fileBeingWrittenTo++; //ok record number is not quite the right name but
                            fileProcessor.onNextFileUsed(fileBeingWrittenTo);

                        } else if(i == MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED) {
                            System.out.println("DBG: Next output file started");
                        }
                    }
                });
                mMediaRecorder.prepare();

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


    void startRecordingVideo(final Activity activity, final Handler mBackgroundHandler, final Handler uiElementHandler, final FileProcessor fileProcessor) {
        class startRecordingVideoRunnable implements Runnable {
            public void run() {
                if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
                    System.out.println("DBG: Returned unsueccessfuly");

                    return;
                }
                try {
                    System.out.println("DBG: StartRecordingVideo requested");
                    setUpMediaRecorder(activity,fileProcessor,1,uiElementHandler);
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

                            sendBooleanMessage(uiElementHandler,"updatePreview",true);
                            System.out.println("DBG: Video recording has started successfully");

                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // UI - set next text of button
                                    Message message = Message.obtain();
                                    Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
                                    b.putBoolean("videoStarted", true);
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


                } catch (
                        CameraAccessException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Thread t = new Thread(new startRecordingVideoRunnable());
        t.start();
    }

    void stopRecordingVideo(final Activity activity, final Handler uiElementHandler, boolean stopVideo, FileProcessor fileProcessor) {
        class stopRecordingVideoRunnable implements Runnable {
            public void run() {
                //  mButtonVideo.setText(R.string.record);
                mMediaRecorder.stop();
                mMediaRecorder.reset();

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendBooleanMessage(uiElementHandler, "stopVideo", true);
                    }
                });

                    fileProcessor.startRunnables(getActivity(), elapsedTimeMillis, false);
                    fileProcessor.onStartRecordingVideo();

                    mediaRecorderHandler.startRecordingVideo(getActivity(),threadHandler.mBackgroundHandler,uiElementHandler, fileProcessor);
                } else { //TODO: commented out temporarily
                    fileProcessor.startRunnables(getActivity(), elapsedTimeMillis, true);
                    // fileProcessor.simpleSave(getActivity());
                } //is there a pause?
                mIsRecordingVideo =false;
            }
        }
        Thread t = new Thread(new stopRecordingVideoRunnable());
        t.start();
    }

    void autoStopVideo() {
        mMediaRecorder.reset();
        mMediaRecorder.release();
    }




    @SuppressWarnings("MissingPermission")
    void openCamera(final int width, final int height, final Activity activity) {

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
                } catch (
                        CameraAccessException e) {
                    Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
                    activity.finish();
                } catch (
                        InterruptedException e) {
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
                            sendBooleanMessage(uiElementHandler,"updatePreview",true);
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




     void sendBooleanMessage(Handler handler, String key, Boolean booleam) {
        Message message = Message.obtain();
        Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
        b.putBoolean(key, true);
        message.setData(b);
        handler.sendMessage(message);
    }

}