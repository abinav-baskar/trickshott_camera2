package com.example.android.camera2video;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileProcessor {

    File file0=null;
    File file1=null;
    File file2 = null;

    private File finalFile, fileToBeTrimmed;

    private FfmpegHandler ffmpegHandler;

    static final int ffmpegIdle = 0;
    static final int ffmpegActive = 1;
    static final int ffmpegSuccess = 2;
    static final int ffmpegFailure = 3;
    static int ffmpegStatus = ffmpegIdle;

    boolean firstOfHighlightsReel=true;

    private static int recordLength = 0;
    private static int elapsedTimeMillis = 0;
    boolean firstOfRecordings = true;
    int recordNumber;


    boolean stopVideo;

    Handler handler;
    Activity activity;

    FileProcessor(Handler h) {
        handler = h;
        ffmpegHandler = new FfmpegHandler();
    }


    void onStartRecordingVideo() {
        firstOfHighlightsReel = true;
        firstOfRecordings = true;
    }

    File getNextFile(Activity activity, int m_recordNumber) {
        recordNumber = m_recordNumber;
        switch(recordNumber) {
            case 0:
                file0 = getMediaFile(activity, "file0");
                return file0;
            case 1:
                file1 = getMediaFile(activity, "file1");
                return file1;
            default:
                file0 = file1;
                file1 = file2; //< we can't do this now can we
                file2 = getMediaFile(activity, "file2");
                return file2;

                /*
                Once we're in the cycle, the distribution is as follows:
                [0-80%]: file0: empty, file1: to be kepy, file2: currenlty written to
                [90-100%] {before action}: file0: to be deleted, file1: to be kept, file2: to be written to
                [90-100%] {after action}: file0: holding info, file1: being written to, file2: to be written to
                 */
        }
    }

    void onNextFileUsed(int fileBeingWrittenTo) {
        if(fileBeingWrittenTo>2) {
            file1.delete();
        }
    }

    void simpleSave(Activity activity) {
        addFileToGallery(file1,activity);
        addFileToGallery(file2, activity);
    }

    /*example.android.camera2video E/AndroidRuntime: FATAL EXCEPTION: Thread-4
    Process: com.example.android.camera2video, PID: 25252
    java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String java.io.File.getAbsolutePath()' on a null object reference
    at com.example.android.camera2video.FileProcessor$2.run(FileProcessor.java:167)
    at java.lang.Thread.run(Thread.java:919)*/

    void deletePreviousFile() {

    }

    void startRunnables(Activity m_activity, int m_elapsedTimeMillis, boolean m_stopVideo) {
        Thread saveSegmentThread;
        activity = m_activity;
        stopVideo = m_stopVideo;
        elapsedTimeMillis = m_elapsedTimeMillis;

        if(firstOfHighlightsReel) {
            finalFile = null; //or a new file?
        }

        switch(recordNumber) {
            case 1:
                if (elapsedTimeMillis < PermissionStatus.savedLength) {
                    System.out.println("DBG: With Nothing");
                    saveSegmentThread = new Thread(stopVideoWithoutTrim); //using file0 always
                    saveSegmentThread.start();
                } else {
                    System.out.println("DBG: With Trim, as elapsedTimeMillis is "+elapsedTimeMillis+" while savedLength is ofc "+PermissionStatus.savedLength);
                    fileToBeTrimmed = file0;
                    saveSegmentThread = new Thread(stopVideoWithTrim);
                    saveSegmentThread.start();
                }
            case 2:


        }

        if (firstOfRecordings) {
            if (elapsedTimeMillis < PermissionStatus.savedLength) { //Stopping in first 10 seconds
                System.out.println("DBG: With Nothing");
                saveSegmentThread = new Thread(stopVideoWithoutTrim);
                saveSegmentThread.start();
            } else { //recordLength > savedLength, still in firstRecording but need to trim
                System.out.println("DBG: With Trim, as elapsedTimeMillis is "+elapsedTimeMillis+" while savedLength is ofc "+PermissionStatus.savedLength);
                fileToBeTrimmed = file1;
                saveSegmentThread = new Thread(stopVideoWithTrim);
                saveSegmentThread.start();
            }
        } else {
            if (elapsedTimeMillis < PermissionStatus.savedLength) { //into second recording, and need to trim and append
                System.out.println("DBG: With Append");
                saveSegmentThread = new Thread(stopVideoWithAppend);
                saveSegmentThread.start();
            } else { //Just trim is fine
                System.out.println("DBG: With Trim");
                fileToBeTrimmed = file2;
                saveSegmentThread = new Thread(stopVideoWithTrim);
                saveSegmentThread.start();

            }
        }
    }

    void fartRunnables(Activity m_activity, int m_elapsedTimeMillis, boolean m_stopVideo, boolean firstOfRecordings) {
        Thread saveSegmentThread;
        activity = m_activity;
        stopVideo = m_stopVideo;
        elapsedTimeMillis = m_elapsedTimeMillis;

        if(firstOfHighlightsReel) {
            finalFile = null; //or a new file?
        }

        if (firstOfRecordings) {
            if (elapsedTimeMillis < PermissionStatus.savedLength) { //Stopping in first 10 seconds
                System.out.println("DBG: With Nothing");
                saveSegmentThread = new Thread(stopVideoWithoutTrim);
                saveSegmentThread.start();
            } else { //recordLength > savedLength, still in firstRecording but need to trim
                System.out.println("DBG: With Trim, as elapsedTimeMillis is "+elapsedTimeMillis+" while savedLength is ofc "+PermissionStatus.savedLength);
                fileToBeTrimmed = file1;
                saveSegmentThread = new Thread(stopVideoWithTrim);
                saveSegmentThread.start();
            }
        } else {
            if (elapsedTimeMillis < PermissionStatus.savedLength) { //into second recording, and need to trim and append
                System.out.println("DBG: With Append");
                saveSegmentThread = new Thread(stopVideoWithAppend);
                saveSegmentThread.start();
            } else { //Just trim is fine
                System.out.println("DBG: With Trim");
                fileToBeTrimmed = file2;
                saveSegmentThread = new Thread(stopVideoWithTrim);
                saveSegmentThread.start();

            }
        }
    }

    private Runnable stopVideoWithAppend = new Runnable() {
        @Override
        public void run() {
            int startTime = recordLength-1000-PermissionStatus.savedLength+elapsedTimeMillis; //-1000 because each video is a good second shorter
            int endTime = recordLength-1000;

            File tempOutputFile = createTempMediaStore(activity);
            ffmpegStatus = ffmpegActive;
            System.out.println("DBG: Trim video requested");
            ffmpegHandler.trimTheVideo(file1.getAbsolutePath(), tempOutputFile.getAbsolutePath(), startTime, endTime, activity);
            System.out.println("DBG+ " + tempOutputFile.getAbsolutePath());
            while(ffmpegStatus==ffmpegActive) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }//A second runnable is only needed if you want to affect UI
            }
            System.out.println("DBG: We believe trim video is done");

            File segmentFile = getMediaFile(activity,"finalFile");
            ffmpegStatus = ffmpegActive;
            System.out.println("DBG: Append video requested with temp: " + tempOutputFile.getAbsolutePath() + " and file2 " + file2.getAbsolutePath());
            ffmpegHandler.appendTheVideo(tempOutputFile.getAbsolutePath(), file2.getAbsolutePath(), segmentFile.getAbsolutePath());

            while(ffmpegStatus == ffmpegActive) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("DBG: Append video finish expected");

            useFile(segmentFile);
            if(file1 != null) {
                file1.delete();
            }
            if(file2 != null) {
                file2.delete();
            }
            tempOutputFile.delete();

        }
    };

    private Runnable stopVideoWithTrim = new Runnable() {
        @Override
        public void run() {
            System.out.println("DBG: Stopping video with trim");
            int endTime = elapsedTimeMillis;
            int startTime = endTime - PermissionStatus.savedLength;

            File trimmedFile = getMediaFile(activity,"");
            // ffmpegStatus = ffmpegActive;
         ffmpegHandler.trimTheVideo(file1.getAbsolutePath(), trimmedFile.getAbsolutePath(), startTime, endTime, activity);
            while(ffmpegStatus == ffmpegActive) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {//TODO: issue is sometimes we trim file2 not file1
                    e.printStackTrace();
                }
            }
            useFile(trimmedFile);
            if(file1 != null) {
                file1.delete();
            }
        }
    };

    private Runnable stopVideoWithoutTrim = new Runnable() {
        @Override
        public void run() {
            System.out.println("DBG: Stopping video without trim");
            useFile(file1);
        }
    };


    private void useFile(File newlyCreatedFile) {

        if(PermissionStatus.assembleHighlightsReel) {
            finalFile = appendToHighlightsReel(newlyCreatedFile);
        }
        else {
            finalFile = newlyCreatedFile;
        }
        if(stopVideo || !PermissionStatus.assembleHighlightsReel ) {
            Uri uri = SaveMedia.getOutputMediaFileUri(finalFile, activity);
            SaveMedia.galleryAddPic(finalFile,uri, activity);

            if(stopVideo) {
                Message message = Message.obtain();
                Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
                b.putString("key_playableUriPath",uri.toString());
                message.setData(b);
                handler.sendMessage(message);

                Camera2VideoFragment.setFinalFile(finalFile);

            }
        }
    }

    private File appendToHighlightsReel(File newlyCreatedFile) {
        if(finalFile !=null) {
            File existingSaves = finalFile;
            finalFile = getMediaFile(activity, "finalFile");
            ffmpegStatus = ffmpegActive;
            System.out.println("DBG: Append video requested in useFile");
            ffmpegHandler.appendTheVideo(existingSaves.getAbsolutePath(), newlyCreatedFile.getAbsolutePath(), finalFile.getAbsolutePath());
            while (ffmpegStatus == ffmpegActive) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("DBG: Append video finish expected");
            newlyCreatedFile.delete();
            firstOfHighlightsReel = false;
            addFileToGallery(finalFile,activity); //activity is defined where?
            return finalFile;
        }
        else {
            return newlyCreatedFile;
        }
    }

    static void setFfmpegStatus(int status) {
        ffmpegStatus = status;
    }


    public void addFileToGallery(File file,Activity activity) {
        Uri uri = SaveMedia.getOutputMediaFileUri(file, activity);
        SaveMedia.galleryAddPic(file,uri, activity);
        Message message = Message.obtain();
        Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
        b.putString("key_playableUriPath",uri.toString());
        message.setData(b);
        handler.sendMessage(message);

        //    ManualVideoActivity.setPlayableUriPath(uri.toString());
        Camera2VideoFragment.setFinalFile(file);
    }

    public void makeFinalFileNull() {
        finalFile = null;
    }

    int calcRecordTime(int savedLength) {
        return 40*1000;
    }


    private static File getMediaFile(Context c, String addedText) {
        File mediaStorageDir =  c.getExternalFilesDir(null);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (addedText != null) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + "_" + addedText + ".mp4");
        } else {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        }
        return mediaFile;
    }


    private static File createTempMediaStore(Context c) {
        File m_mediaStorageDir = c.getExternalFilesDir(null);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = null;
        if (m_mediaStorageDir != null) {
            mediaFile = new File(m_mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + "_tempVideo.mp4");
        }
        return mediaFile;
    }

}
