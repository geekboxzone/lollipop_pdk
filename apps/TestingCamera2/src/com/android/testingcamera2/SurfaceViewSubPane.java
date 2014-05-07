/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.Size;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

public class SurfaceViewSubPane extends TargetSubPane implements SurfaceHolder.Callback {

    private static final int NO_SIZE = -1;
    private SurfaceView mSurfaceView;
    private Surface mSurface;

    private Spinner mSizeSpinner;
    private Size[] mSizes;
    private int mCurrentSizeId = NO_SIZE;
    private CameraControlPane mCurrentCamera;

    public SurfaceViewSubPane(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate(R.layout.surfaceview_target_subpane, this);
        this.setOrientation(VERTICAL);

        mSurfaceView = (SurfaceView) this.findViewById(R.id.target_subpane_surface_view_view);
        mSurfaceView.getHolder().addCallback(this);
        mSizeSpinner = (Spinner) this.findViewById(R.id.target_subpane_surface_view_size_spinner);
        mSizeSpinner.setOnItemSelectedListener(mSizeSpinnerListener);
    }

    @Override
    public void setTargetCameraPane(CameraControlPane target) {
        if (target != null) {
            Size oldSize = null;
            if (mCurrentSizeId != NO_SIZE) {
                oldSize = mSizes[mCurrentSizeId];
            }

            final int MAGIC_IMPLEMENTATION_DEFINED_FORMAT = 0x22;
            List<Size> outputList = new ArrayList<Size>();
            CameraCharacteristics info = target.getCharacteristics();
            int[] availableConfigs =
                    info.get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_CONFIGURATIONS);
            for (int i = 0; i < availableConfigs.length; i += 4) {
                int type = availableConfigs[i];
                boolean isOutput =
                        availableConfigs[i + 3] ==
                        CameraCharacteristics.SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT;
                if (type == MAGIC_IMPLEMENTATION_DEFINED_FORMAT && isOutput) {
                    outputList.add(new Size(availableConfigs[i + 1], availableConfigs[i + 2]));
                }
            }
            mSizes = outputList.toArray(new Size[0]);
            // TODO: Replace above with StreamConfigurationMap

            int newSelectionId = 0;
            for (int i = 0; i < mSizes.length; i++) {
                if (mSizes[i].equals(oldSize)) {
                    newSelectionId = i;
                    break;
                }
            }
            String[] outputSizeItems = new String[mSizes.length];
            for (int i = 0; i < outputSizeItems.length; i++) {
                outputSizeItems[i] = mSizes[i].toString();
            }

            mSizeSpinner.setAdapter(new ArrayAdapter<String>(getContext(), R.layout.spinner_item,
                    outputSizeItems));
            mSizeSpinner.setSelection(newSelectionId);
        } else {
            mSizeSpinner.setAdapter(null);
            mCurrentSizeId = NO_SIZE;
        }
    }

    private void updateSizes() {
        if (mCurrentSizeId != NO_SIZE) {
            Size s = mSizes[mCurrentSizeId];
            mSurfaceView.getHolder().setFixedSize(s.getWidth(), s.getHeight());
            int width = getWidth();
            int height = width * s.getHeight() / s.getWidth();
            mSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        } else {
            // Make sure the view has some reasonable size even when there's no
            // target camera for aspect-ratio correct sizing
            int width = getWidth();
            int height = width / 2;
            mSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        }
    }

    @Override
    public Surface getOutputSurface() {
        return mSurface;
    }

    private OnItemSelectedListener mSizeSpinnerListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            mCurrentSizeId = pos;
            updateSizes();
        };

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            mCurrentSizeId = NO_SIZE;
        };
    };

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurface = holder.getSurface();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

}
