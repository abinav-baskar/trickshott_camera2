package com.example.android.camera2video;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;

public class PermissionHandler {

    static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };
    private static final String FRAGMENT_DIALOG = "dialog";
    private static final String TAG = "Camera2VideoFragment";
    static final int REQUEST_VIDEO_PERMISSIONS = 1;

    boolean hasPermissionsGranted(String[] permissions, Activity activity) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Requests permissions needed for recording video.
     */
    void requestVideoPermissions(Fragment fragment) {
        if (!shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS, fragment)) {
            FragmentCompat.requestPermissions(fragment, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }


    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions,Fragment fragment) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(fragment, permission)) {
                return true;
            }
        }
        return false;
    }
}

/*
 @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
 */
