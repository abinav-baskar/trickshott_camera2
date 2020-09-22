package com.example.android.camera2video;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;

import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v13.app.FragmentCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


public class Camera2VideoFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    private MediaRecorderHandler mediaRecorderHandler = new MediaRecorderHandler();
    private PermissionHandler permissionHandler;
    private ThreadHandler threadHandler;
    private ButtonHandler buttonHandler;
    private FileProcessor fileProcessor;

    private Handler mainHandler = new Handler();
    Handler saveSegmentRunnableHandler = new Handler();

    long startTime,endTime;
    int elapsedTimeMillis;

    Runnable firstTimeRunnable;

    FileOutputStream fileOutputStream;

    Boolean clipSavedThisRecording = false;
    private boolean firstOfRecordings = true;

    private boolean disableMediaButtons = false;
    private boolean videoCapturing = false; //may need to rename

    public static Camera2VideoFragment newInstance() {
        return new Camera2VideoFragment();
    }

    @Override //Is this the actual onCreate method?
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.new_fragment_camera2, container, false);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mediaRecorderHandler.mTextureView = view.findViewById(R.id.texture);
        buttonHandler = new ButtonHandler(view,getActivity(),uiElementHandler);
    }

    @Override
    public void onResume() {
        super.onResume();
        permissionHandler = new PermissionHandler();
        threadHandler = new ThreadHandler();
        threadHandler.startBackgroundThread();
        mediaRecorderHandler.setupMStateCalback(getActivity(),threadHandler,uiElementHandler);
        mediaRecorderHandler.setupMSurfaceTextureListener(getActivity());
        fileProcessor = new FileProcessor(saveSegmentRunnableHandler);

        if ( mediaRecorderHandler.mTextureView.isAvailable()) {
            openCamera( mediaRecorderHandler.mTextureView.getWidth(),  mediaRecorderHandler.mTextureView.getHeight());
        } else {
            mediaRecorderHandler.mTextureView.setSurfaceTextureListener(mediaRecorderHandler.mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        mediaRecorderHandler.closeCamera();
        threadHandler.stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.info: {
                Activity activity = getActivity();
                Toast.makeText(activity, "Info Pressed", Toast.LENGTH_SHORT).show();

                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null);
                          //  .show();
                }
                break;
            }
            case R.id.flip: {
               mediaRecorderHandler.switchCamera(getActivity());
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != PermissionHandler.REQUEST_VIDEO_PERMISSIONS) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {
        if (!permissionHandler.hasPermissionsGranted(PermissionHandler.VIDEO_PERMISSIONS, getActivity())) {
            permissionHandler.requestVideoPermissions(this);
            return;
        }
        mediaRecorderHandler.openCamera(width,height,getActivity());
    }

    public void autoStartVideo() throws InterruptedException, IOException {

        mediaRecorderHandler.setUpMediaRecorder(getActivity(), fileProcessor,2,uiElementHandler);
        mediaRecorderHandler.startRecordingVideo(getActivity(),threadHandler.mBackgroundHandler,uiElementHandler, fileProcessor);
    }


    /**
     * Update the camera preview. StartPreview needs to be called in advance.
     */
    private void updatePreview() {
        if (null ==  mediaRecorderHandler.mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder( mediaRecorderHandler.mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mediaRecorderHandler.mPreviewSession.setRepeatingRequest( mediaRecorderHandler.mPreviewBuilder.build(), null, threadHandler.mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private void startRecordingVideo() {
        if (null ==  mediaRecorderHandler.mCameraDevice || ! mediaRecorderHandler.mTextureView.isAvailable() || null ==  mediaRecorderHandler.mPreviewSize) {
            Toast.makeText(getActivity(), "BAD", Toast.LENGTH_SHORT).show();
            return;
        }
        //mNextVideoAbsolutePath= mediaRecorderHandler.setUpMediaRecorder(getActivity(),mNextVideoAbsolutePath);

        //mediaRecorderHandler.mMediaRecorder.setMaxDuration(5000);
        //String str = "hi";
        // mediaRecorderHandler.mMediaRecorder.setNextOutputFile(new File(mediaRecorderHandler.getVideoFilePath(getActivity(),str))); //this is a bad way of doing it but
        videoCapturing = true;
        startTime = System.nanoTime();
        fileProcessor.onStartRecordingVideo();
        firstOfRecordings = true;
        mediaRecorderHandler.startRecordingVideo(getActivity(),threadHandler.mBackgroundHandler,uiElementHandler, fileProcessor);

    }

    private void stopRecordingVideo() {
        videoCapturing = false;
        removeHandlerCallbacks();
        elapsedTimeMillis = (int) ((System.nanoTime() - startTime) / 1000000);
        mediaRecorderHandler.stopRecordingVideo(getActivity(),uiElementHandler,true);
      //  fileProcessor.startRunnables(getActivity(), elapsedTimeMillis, true,firstOfRecordings); //TODO: Do it once we're done

        mediaRecorderHandler.startPreview(getActivity(),threadHandler.mBackgroundHandler,uiElementHandler);

    }

    private void saveSegment() {
        clipSavedThisRecording = true;
        elapsedTimeMillis = (int) ((System.nanoTime() - startTime) / 1000000);
        removeHandlerCallbacks();
        mediaRecorderHandler.stopRecordingVideo(getActivity(),uiElementHandler,false);
       // fileProcessor.startRunnables(getActivity(), elapsedTimeMillis, false,firstOfRecordings);
    }

    private void removeHandlerCallbacks() {
        if(firstOfRecordings) {
            mainHandler.removeCallbacks(firstTimeRunnable);
        }
        else {
            mainHandler.removeCallbacks(mainRunnable);
        }
    }

    Handler uiElementHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if(bundle.getBoolean("videoStarted")) {
                disableMediaButtons = false;
                buttonHandler.saveButton.setEnabled(true);
                buttonHandler.progressBar.setVisibility(View.GONE);
                /*mainHandler.postDelayed(firstTimeRunnable = new Runnable() {
                    @Override
                    public void run() {
                        startTime = System.nanoTime();
                        mediaRecorderHandler.autoStopVideo();
                        firstOfRecordings = false;
                        try {
                            autoStartVideo();
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
                        mainHandler.postDelayed(mainRunnable, fileProcessor.calcRecordTime(PermissionStatus.savedLength) );
                    }
                }, fileProcessor.calcRecordTime(PermissionStatus.savedLength) );*/
            }

            if(bundle.getBoolean("updatePreview")) { updatePreview();}

            if(bundle.getBoolean("stopVideo")) {
                Toast.makeText(getContext(), "VideoSaved", Toast.LENGTH_SHORT).show();
                startTime = System.nanoTime(); //strictly only needed when we save but is called on stop too

            }
            if(bundle.getBoolean("captureButtonPressed")) {
                if (!mediaRecorderHandler.mIsRecordingVideo && !disableMediaButtons) {
                    buttonHandler.startRecordingAnimation();
                    startRecordingVideo();

                    disableMediaButtons = true;
                } else {
                    if (!disableMediaButtons/*true*/) { //Preventing double clicks
                        buttonHandler.endRecordingAnimation(PermissionStatus.saveSegmentOnStop);

                        //ssLastClickTime = System.nanoTime();
                        stopRecordingVideo();
                    }
                }
            }
            if(bundle.getBoolean("flipButtonPressed")) {
                mediaRecorderHandler.switchCamera(getActivity());
            }
            if(bundle.getBoolean("saveButtonPressed")) {
                if(!disableMediaButtons) {
                    buttonHandler.progressBar.setVisibility(View.VISIBLE);
                    saveSegment();
                }
            }
            if(bundle.getBoolean("thumbnailViewPressed")) {

            }
            if(bundle.getBoolean("maxDurationReached")) {

            }
        }
    };

    Runnable mainRunnable = new Runnable() {
        public void run() {
            startTime = System.nanoTime();
            mediaRecorderHandler.autoStopVideo();
            try {
                autoStartVideo();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
            mainHandler.postDelayed(mainRunnable, fileProcessor.calcRecordTime(PermissionStatus.savedLength));
        }
    };

    public static void setFinalFile(File f) {

    }
}