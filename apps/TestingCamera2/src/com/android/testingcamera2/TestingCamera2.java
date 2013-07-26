/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.testingcamera2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import java.nio.ByteBuffer;

public class TestingCamera2 extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "TestingCamera2";
    private CameraOps mCameraOps;

    private SurfaceView mPreviewView;
    private ImageView mStillView;

    private SurfaceHolder mCurrentPreviewHolder = null;

    private Button mInfoButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);

        mPreviewView = (SurfaceView) findViewById(R.id.preview_view);
        mPreviewView.getHolder().addCallback(this);

        mStillView = (ImageView) findViewById(R.id.still_view);

        mInfoButton  = (Button) findViewById(R.id.info_button);
        mInfoButton.setOnClickListener(mInfoButtonListener);


        try {
            mCameraOps = CameraOps.create(this);
        } catch(ApiFailureException e) {
            logException("Cannot create camera ops!",e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mCameraOps.minimalPreviewConfig(mPreviewView.getHolder());
            mCurrentPreviewHolder = mPreviewView.getHolder();
        } catch (ApiFailureException e) {
            logException("Can't configure preview surface: ",e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            mCameraOps.closeDevice();
        } catch (ApiFailureException e) {
            logException("Can't close device: ",e);
        }
        mCurrentPreviewHolder = null;
    }

    /** SurfaceHolder.Callback methods */
    @Override
    public void surfaceChanged(SurfaceHolder holder,
            int format,
            int width,
            int height) {
        if (mCurrentPreviewHolder != null && holder == mCurrentPreviewHolder) {
            try {
                mCameraOps.minimalPreview(holder);
            } catch (ApiFailureException e) {
                logException("Can't start minimal preview: ", e);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private Button.OnClickListener mInfoButtonListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            final Handler uiHandler = new Handler();
            AsyncTask.execute(new Runnable() {
                public void run() {
                    try {
                        mCameraOps.minimalJpegCapture(mCaptureListener, uiHandler);
                        if (mCurrentPreviewHolder != null) {
                            mCameraOps.minimalPreview(mCurrentPreviewHolder);
                        }
                    } catch (ApiFailureException e) {
                        logException("Can't take a JPEG! ", e);
                    }
                }
            });
        }
    };

    private CameraOps.CaptureListener mCaptureListener = new CameraOps.CaptureListener() {
        public void onCaptureAvailable(Image capture) {
            if (capture.getFormat() != ImageFormat.JPEG) {
                Log.e(TAG, "Unexpected format: " + capture.getFormat());
                return;
            }
            ByteBuffer jpegBuffer = capture.getPlanes()[0].getBuffer();
            byte[] jpegData = new byte[jpegBuffer.capacity()];
            jpegBuffer.get(jpegData);

            Bitmap b = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
            mStillView.setImageBitmap(b);
        }
    };

    private void logException(String msg, Throwable e) {
        Log.e(TAG, msg + Log.getStackTraceString(e));
    }
}
