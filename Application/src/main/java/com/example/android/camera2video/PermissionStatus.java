package com.example.android.camera2video;

import android.app.Activity;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public class PermissionStatus {

    public static boolean assembleHighlightsReel = false; //TODO: CHanged to false, if it orks now this is the problem
    public static boolean saveSegmentOnStop = true;
    public static int savedLength = 10*1000;

    private static final long DEFAULT_STORAGE_TO_USE = 21000000;

    void getPermissions() {

    }

        public static long getAvailableExternalMemorySize() {
            if (externalMemoryAvailable()) {
                File path = Environment.getExternalStorageDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSizeLong();
                long availableBlocks = stat.getAvailableBlocksLong();
                long memoryLeft = availableBlocks*blockSize;
                System.out.println("DBG: Calculated memory to be "+memoryLeft); //this is accurate and in bytes (1/millionth of an mb)
             //   return (memoryLeft/3); //we'll divide by 3 right?
                    return DEFAULT_STORAGE_TO_USE;
            } else {
                System.out.println("DBG: Couldn't calculate memory, returned ");
                return DEFAULT_STORAGE_TO_USE;
            }
        }


    private static boolean externalMemoryAvailable() {
        return android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }


}
