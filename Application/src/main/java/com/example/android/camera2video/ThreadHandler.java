package com.example.android.camera2video;

import android.os.Handler;
import android.os.HandlerThread;

public class ThreadHandler {

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    HandlerThread mBackgroundThread;


    /**
     * A {@link Handler} for running tasks in the background.
     */
    Handler mBackgroundHandler;

    /**
     * Starts a background thread and its {@link Handler}.
     */
    void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
