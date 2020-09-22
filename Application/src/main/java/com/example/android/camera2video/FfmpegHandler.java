package com.example.android.camera2video;

import android.content.Context;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;


import com.arthenica.mobileffmpeg.FFmpeg;

public class FfmpegHandler {
    FFmpeg ffmpeg;
    File mediaStorageDir = null;

    public void trimTheVideo(String mInputFilePath, String mOutputFilePath, int startMs, int endMs, Context c) {

        if (mInputFilePath != null) {

            String command = "-y -i "+mInputFilePath+" -ss "+startMs/1000+" -t "+(endMs-startMs)/1000+" -c copy "+mOutputFilePath;
            int rc = FFmpeg.execute(command);
            System.out.println("DBG trimmed lol " + rc + " with 0 implying success, 1 implying failure it seems");
            if(rc == 0) {
                FileProcessor.setFfmpegStatus(FileProcessor.ffmpegSuccess);
            }
            else if(rc == 1) {
                FileProcessor.setFfmpegStatus(FileProcessor.ffmpegFailure);
            }
            else {
                System.out.println("DBG: Something thread async while loop lol and rc is " + rc );
            }

        } else {
            System.out.println("DBG: File path is null");
        }
    }

    public void appendTheVideo(String mInputFilePath1, String mInputFilePath2, String mOutputFilePath) {

        System.out.println("DBG: Append the video is requested");
        if(mInputFilePath1 != null && mInputFilePath2 != null) {
            String list = generateList(new String[] {mInputFilePath1, mInputFilePath2});

            String command = "-f concat -safe 0 -i "+ list + " -c copy " + mOutputFilePath;
            int rc = FFmpeg.execute(command);
            System.out.println("DBG: Append results in " + rc);
            if(rc == 0) {
                FileProcessor.setFfmpegStatus(FileProcessor.ffmpegSuccess);
            }
            else if(rc == 1) {
                FileProcessor.setFfmpegStatus(FileProcessor.ffmpegFailure);
            }
        }
    }

    private static String generateList(String[] inputs) {
        File list;
        Writer writer = null;
        try {
            list = File.createTempFile("ffmpeg-list", ".txt");
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(list)));
            for (String input: inputs) {
                writer.write("file '" + input + "'\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "/";
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return list.getAbsolutePath();
    }

}

