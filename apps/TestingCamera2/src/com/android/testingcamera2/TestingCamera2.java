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
import android.hardware.photography.CameraAccessException;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class TestingCamera2 extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "TestingCamera2";
    private CameraOps mCameraOps;

    private SurfaceView mPreviewView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        mPreviewView = (SurfaceView) findViewById(R.id.preview);
        mPreviewView.getHolder().addCallback(this);

        try {
            mCameraOps = CameraOps.create(this);
        } catch(ApiFailureException e) {
            logException("Cannot create camera ops!",e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            mCameraOps.closeDevice();
        } catch (ApiFailureException e) {
            logException("Can't close device: ",e);
        }
    }

    /** SurfaceHolder.Callback methods */
    @Override
    public void surfaceChanged(SurfaceHolder holder,
            int format,
            int width,
            int height) {
        try {
            mCameraOps.minimalPreview(holder);
        } catch (ApiFailureException e) {
            logException("Can't start minimal preview: ", e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private void logException(String msg, Throwable e) {
        Log.e(TAG, msg + Log.getStackTraceString(e));
    }
}
