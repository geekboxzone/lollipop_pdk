/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.testingcamera;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * A simple test application for the camera API.
 *
 * The goal of this application is to allow all camera API features to be
 * excercised, and all information provided by the API to be shown.
 */
public class TestingCamera extends Activity implements SurfaceHolder.Callback {

    /** UI elements */
    private SurfaceView mPreviewView;
    private SurfaceHolder mPreviewHolder;

    private Spinner mCameraSpinner;
    private Spinner mPreviewSizeSpinner;
    private ToggleButton mPreviewToggle;
    private Button  mTakePictureButton;

    private TextView mSnapshotText;
    private ImageView mSnapshotImg;

    /** Camera state */
    private int mCameraId = 0;
    private Camera mCamera;
    private Camera.Parameters mParams;
    private List<Camera.Size> mPreviewSizes;
    private int mPreviewSize = 0;

    private static final int CAMERA_UNINITIALIZED = 0;
    private static final int CAMERA_OPEN = 1;
    private static final int CAMERA_PREVIEW = 2;
    private static final int CAMERA_TAKE_PICTURE = 3;
    private int mState = CAMERA_UNINITIALIZED;

    /** Misc variables */

    private static final String TAG = "TestingCamera";

    // Activity methods
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        mPreviewView = (SurfaceView)findViewById(R.id.preview);
        mPreviewView.getHolder().addCallback(this);

        mCameraSpinner = (Spinner) findViewById(R.id.camera_spinner);
        mCameraSpinner.setOnItemSelectedListener(mCameraSpinnerListener);

        mPreviewSizeSpinner = (Spinner) findViewById(R.id.preview_size_spinner);
        mPreviewSizeSpinner.setOnItemSelectedListener(mPreviewSizeListener);

        mPreviewToggle = (ToggleButton) findViewById(R.id.start_preview);
        mPreviewToggle.setOnClickListener(mPreviewToggleListener);

        mTakePictureButton = (Button) findViewById(R.id.take_picture);
        mTakePictureButton.setOnClickListener(mTakePictureListener);

        int numCameras = Camera.getNumberOfCameras();
        String[] cameraNames = new String[numCameras];
        for (int i = 0; i < numCameras; i++) {
            cameraNames[i] = "Camera " + i;
        }

        mCameraSpinner.setAdapter(
                new ArrayAdapter<String>(this,
                        R.layout.spinner_item, cameraNames));
    }

    @Override
    public void onResume() {
        super.onResume();
        mPreviewHolder = null;
        setUpCamera();
    }

    @Override
    public void onPause() {
        super.onPause();

        mCamera.release();
        mState = CAMERA_UNINITIALIZED;
    }

    protected Dialog onCreateDialog(int id) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.snapshot_dialog);
        dialog.setTitle("Snapshot");

        mSnapshotText =
                (TextView) dialog.findViewById(R.id.snapshot_text);
        mSnapshotText.setText("Snapshot title");

        mSnapshotImg =
                (ImageView) dialog.findViewById(R.id.snapshot_image);

        return dialog;
    }

    // SurfaceHolder.Callback methods
    @Override
    public void surfaceChanged(SurfaceHolder holder,
            int format,
            int width,
            int height) {
        if (mPreviewHolder != null) return;

        Log.d(TAG, "Surface holder available: " + width + " x " + height);
        mPreviewHolder = holder;
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.e(TAG, "Unable to set up preview!");
        }
        resizePreview(mPreviewSizes.get(mPreviewSize).width,
                mPreviewSizes.get(mPreviewSize).height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    // UI listeners

    private AdapterView.OnItemSelectedListener mCameraSpinnerListener =
            new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,
                        View view, int pos, long id) {
                    if (mCameraId != pos) {
                        mCameraId = pos;
                        setUpCamera();
                    }
                }

                public void onNothingSelected(AdapterView parent) {

                }
            };

    private AdapterView.OnItemSelectedListener mPreviewSizeListener =
            new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,
                        View view, int pos, long id) {
                    if (pos == mPreviewSize) return;
                    Log.d(TAG, "Switching preview sizes");

                    if (mState == CAMERA_PREVIEW) {
                        mCamera.stopPreview();
                    }

                    mPreviewSize = pos;
                    int width = mPreviewSizes.get(mPreviewSize).width;
                    int height = mPreviewSizes.get(mPreviewSize).height;
                    mParams.setPreviewSize(width, height);

                    mCamera.setParameters(mParams);
                    resizePreview(width, height);

                    if (mState == CAMERA_PREVIEW) {
                        mCamera.startPreview();
                    }
                }

                public void onNothingSelected(AdapterView parent) {

                }
            };

    private View.OnClickListener mPreviewToggleListener =
            new View.OnClickListener() {
        public void onClick(View v) {
            if (mState == CAMERA_TAKE_PICTURE) {
                Log.e(TAG, "Can't change preview state while taking picture!");
                return;
            }
            if (mPreviewToggle.isChecked()) {
                Log.d(TAG, "Starting preview");

                mCamera.startPreview();
                mState = CAMERA_PREVIEW;

                mTakePictureButton.setEnabled(true);
            } else {
                Log.d(TAG, "Stopping preview");
                mCamera.stopPreview();
                mState = CAMERA_OPEN;

                mTakePictureButton.setEnabled(false);
            }
        }
    };

    private View.OnClickListener mTakePictureListener =
            new View.OnClickListener() {
        public void onClick(View v) {
            Log.d(TAG, "Taking picture");
            if (mState == CAMERA_PREVIEW) {
                mState = CAMERA_TAKE_PICTURE;

                mTakePictureButton.setEnabled(false);
                mPreviewToggle.setEnabled(false);
                mPreviewToggle.setChecked(false);

                mCamera.takePicture(mShutterCb, mRawCb, mPostviewCb, mJpegCb);
            } else {
                Log.e(TAG, "Can't take picture while not running preview!");
            }
        }
    };

    private Camera.ShutterCallback mShutterCb = new Camera.ShutterCallback() {
        public void onShutter() {
            Log.d(TAG, "Shutter cb fired");
        }
    };

    private Camera.PictureCallback mRawCb = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "Raw cb fired");
        }
    };

    private Camera.PictureCallback mPostviewCb = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "Postview cb fired");
        }
    };

    private Camera.PictureCallback mJpegCb = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "JPEG cb fired");

            showDialog(1);

            Bitmap img = BitmapFactory.decodeByteArray(data, 0, data.length);
            mSnapshotImg.setImageBitmap(img);

            mPreviewToggle.setEnabled(true);

            mState = CAMERA_OPEN;
        }
    };

    // Internal methods

    void setUpCamera() {
        Log.d(TAG, "Setting up camera " + mCameraId);
        if (mState >= CAMERA_OPEN) {
            Log.d(TAG, "Closing old camera");
            mCamera.release();
            mState = CAMERA_UNINITIALIZED;
        }
        Log.d(TAG, "Opening camera " + mCameraId);
        mCamera = Camera.open(mCameraId);
        mState = CAMERA_OPEN;

        mParams = mCamera.getParameters();
        mPreviewSizes = mParams.getSupportedPreviewSizes();

        String[] availableSizeNames = new String[mPreviewSizes.size()];
        for (int i = 0; i < mPreviewSizes.size(); i++) {
            availableSizeNames[i] =
                    Integer.toString(mPreviewSizes.get(i).width) + " x " +
                    Integer.toString(mPreviewSizes.get(i).height);
        }
        mPreviewSizeSpinner.setAdapter(
            new ArrayAdapter<String>(
                this, R.layout.spinner_item, availableSizeNames));

        mPreviewSize = 0;

        int width = mPreviewSizes.get(mPreviewSize).width;
        int height = mPreviewSizes.get(mPreviewSize).height;
        mParams.setPreviewSize(width, height);

        mCamera.setParameters(mParams);

        if (mPreviewHolder != null) {
            try {
                mCamera.setPreviewDisplay(mPreviewHolder);
            } catch(IOException e) {
                Log.e(TAG, "Unable to set up preview!");
            }
        }

        mPreviewToggle.setEnabled(true);
        mPreviewToggle.setChecked(false);
        mTakePictureButton.setEnabled(false);

        resizePreview(width, height);
        if (mPreviewToggle.isChecked()) {
            Log.d(TAG, "Starting preview" );
             mCamera.startPreview();
            mState = CAMERA_PREVIEW;
        }
    }

    void resizePreview(int width, int height) {
        if (mPreviewHolder != null) {
            int viewHeight = mPreviewView.getHeight();
            int viewWidth = (int)(((double)width)/height * viewHeight);

            mPreviewView.setLayoutParams(
                new LayoutParams(viewWidth, viewHeight));
        }

    }
}