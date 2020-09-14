package com.example.android.camera2video;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;

import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import java.io.IOException;


public class Camera2VideoFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    private MediaRecorderHandler mediaRecorderHandler = new MediaRecorderHandler();
    private PermissionHandler permissionHandler;
    private ThreadHandler threadHandler;

    Button mButtonVideo;
    private String mNextVideoAbsolutePath;

    public static Camera2VideoFragment newInstance() {
        return new Camera2VideoFragment();
    }

    @Override //Is this the actual onCreate method?
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mediaRecorderHandler.mTextureView = view.findViewById(R.id.texture);
        mButtonVideo = view.findViewById(R.id.video);
        mButtonVideo.setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        view.findViewById(R.id.flip).setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        permissionHandler = new PermissionHandler();
        threadHandler = new ThreadHandler();
        threadHandler.startBackgroundThread();
        mediaRecorderHandler.setupMStateCalback(getActivity(),threadHandler,uiElementHandler);
        mediaRecorderHandler.setupMSurfaceTextureListener(getActivity());

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
            case R.id.video: {
                if (mediaRecorderHandler.mIsRecordingVideo) {
                    stopRecordingVideo();
                } else {
                    startRecordingVideo();
                }
                break;
            }
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

    /**
     * Update the camera preview. StartPreview needs to be called in advance.
     */
    private void updatePreview() { //TODO maybe another message here to start a thread, but maybe that isn't needed
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
            return;
        }
        try {
            mNextVideoAbsolutePath= mediaRecorderHandler.setUpMediaRecorder(getActivity(),mNextVideoAbsolutePath);
            mediaRecorderHandler.startRecordingVideo(getActivity(),threadHandler.mBackgroundHandler,uiElementHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingVideo() {
        mButtonVideo.setText(R.string.record);// UI
        mNextVideoAbsolutePath = mediaRecorderHandler.stopRecordingVideo(getActivity(),mNextVideoAbsolutePath);
        mediaRecorderHandler.startPreview(getActivity(),threadHandler.mBackgroundHandler,uiElementHandler);
    }

    Handler uiElementHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if(bundle.getBoolean("mButtonVideo.reverseText")) {
                mButtonVideo.setText(R.string.stop); //TODO: Later we can keep an int key for open or close
            }

            if(bundle.getBoolean("updatePreview")) {
                updatePreview();
            }
        }
    };
}