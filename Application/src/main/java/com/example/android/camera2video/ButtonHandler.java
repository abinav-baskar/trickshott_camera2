package com.example.android.camera2video;

import android.annotation.SuppressLint;
import android.app.Activity;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

public class ButtonHandler {

     private GradientDrawable orangeRoundedCorner, whiteRoundedCorner,straightCorner, roundedNoColour;
     ImageButton menuButton;
     ImageButton flipButton,saveButton;
     private Button captureButton;
     ProgressBar progressBar;
     ImageView saveButtonRing,flipButtonRing, thumbnailRing, captureRing;
     ImageView thumbnailView = null;

    public ButtonHandler(View view, Activity activity, Handler uiElementHandler) {
        findButtons(view);
        setupGradientDrawables(activity);
        setInitialButtonStates();
        setNotRecordingVisibilites();
        progressBar.setVisibility(View.GONE);

        onViewCreated(uiElementHandler);
    }

    @SuppressLint("ClickableViewAccessibility") //We have suppressed warnings about accessibility for the blind because we are cold-hearted and lazy
    void onViewCreated(final Handler uiElementHandler) {
        captureButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        buttonAnimatePressDown(captureButton);
                        return true;
                    case MotionEvent.ACTION_UP:
                        sendBooleanMessage(uiElementHandler,"captureButtonPressed",true);
                        return true;
                    case MotionEvent.ACTION_CANCEL:

                        return true;
                }
                return false;
            }
        });

        flipButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        buttonAnimatePressDown(flipButton);
                        return true;
                    case MotionEvent.ACTION_UP:
                        imageButtonAnimatePressUp(flipButton);
                        sendBooleanMessage(uiElementHandler,"flipButtonPressed",true);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });

        saveButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        buttonAnimatePressDown(saveButton);
                        return true;
                    case MotionEvent.ACTION_UP:
                        imageButtonAnimatePressUp(saveButton);
                        sendBooleanMessage(uiElementHandler,"saveButtonPressed",true);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });

        thumbnailView.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                sendBooleanMessage(uiElementHandler,"thumbnailViewPressed",true);
                /*
                if(!recordingVideo && playableUriPath != null) {
                    intent = new Intent(mvaContext, VideoPlayer.class);
                    intent.putExtra("uripath", playableUriPath);
                    intent.putExtra("orientation",orientationOfRecording);
                    mvaContext.startActivity(intent);
                }
                */
                return true;
            }
        });
    }

    private void findButtons(View view) {
        captureButton = view.findViewById(R.id.capture_button);
        saveButton = view.findViewById(R.id.save_button);
        flipButton = view.findViewById(R.id.flip_button);
        thumbnailView = view.findViewById(R.id.thumbnail);

        saveButtonRing = view.findViewById(R.id.save_ring);
        flipButtonRing = view.findViewById(R.id.flip_ring);
        thumbnailRing = view.findViewById(R.id.thumbnail_ring);
        progressBar = view.findViewById(R.id.progressBar);
        captureRing = view.findViewById(R.id.capture_ring);
    }

    private void setupGradientDrawables(Activity activity) {
        orangeRoundedCorner = new GradientDrawable();
        orangeRoundedCorner.setCornerRadius(300);
        orangeRoundedCorner.setColor(activity.getResources().getColor(R.color.colorPrimary));

        whiteRoundedCorner = new GradientDrawable();
        whiteRoundedCorner.setCornerRadius(300);
        whiteRoundedCorner.setColor(Color.WHITE);

        straightCorner = new GradientDrawable();
        straightCorner.setColor(activity.getResources().getColor(R.color.colorPrimary));

        roundedNoColour = new GradientDrawable();
        roundedNoColour.setCornerRadius(300);
    }

    void setInitialButtonStates() {
        saveButton.setBackground(whiteRoundedCorner);
        captureButton.setBackground(orangeRoundedCorner);
        flipButton.setBackground(whiteRoundedCorner);

    }

    void buttonAnimatePressDown(View b) {
        b.animate().scaleXBy(-0.1f).setDuration(200).start();
        b.animate().scaleYBy(-0.1f).setDuration(200).start();
    }



    void imageButtonAnimatePressUp(ImageButton b) {
        b.animate().cancel();
        b.animate().scaleX(1f).setDuration(100).start();
        b.animate().scaleY(1f).setDuration(100).start();
    }

    void setNotRecordingVisibilites() {
        saveButton.setVisibility(View.GONE);
        saveButtonRing.setVisibility(View.GONE);

        flipButton.setVisibility(View.VISIBLE);
        flipButtonRing.setVisibility(View.VISIBLE);
       // menuButton.setVisibility(View.VISIBLE);

       // captureRing.setVisibility(View.GONE);
    }

    void startRecordingAnimation() {
        captureButton.animate().scaleX(0.6f).setDuration(400).start();
        captureButton.animate().scaleY(0.6f).setDuration(400).withEndAction(new Runnable() {
            @Override
            public void run() {
                captureButton.setScaleX(0.55f);
                captureButton.setScaleY(0.55f);
                captureButton.setBackground(straightCorner);

                saveButton.setVisibility(View.VISIBLE);
                saveButtonRing.setVisibility(View.VISIBLE);
              //  captureRing.setVisibility(View.VISIBLE);

            }
        }).start();

        //lmenuButton.setVisibility(View.GONE);
        flipButton.setVisibility(View.GONE);
        flipButtonRing.setVisibility(View.GONE);
    }

    void endRecordingAnimation(boolean saveSegmentOnStop) {
        captureButton.setBackground(orangeRoundedCorner);
        captureButton.animate().scaleX(1f).setDuration(400).start();
        captureButton.animate().scaleY(1f).setDuration(400).start();

        setNotRecordingVisibilites();
        thumbnailRing.setVisibility(View.VISIBLE);
        thumbnailView.setVisibility(View.VISIBLE);
        if(saveSegmentOnStop) {
            progressBar.setVisibility(View.VISIBLE);
        }

    }

    private void sendBooleanMessage(Handler handler, String key, Boolean booleam) {
        Message message = Message.obtain();
        Bundle b = new Bundle(); //Data has to be in the form of a bundle, so only primitive types it deems
        b.putBoolean(key, true);
        message.setData(b);
        handler.sendMessage(message);
    }

    void setRecordingVisibilities() {

    }
    //TODO: Can we make an object? ANS: (Sep/20) FUCK YEAH WE CAN

}
