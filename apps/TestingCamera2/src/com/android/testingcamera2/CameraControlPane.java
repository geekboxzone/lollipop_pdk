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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.android.testingcamera2.PaneTracker.PaneEvent;

import java.io.IOException;

/**
 *
 * Basic control pane block for the control list
 *
 */
public class CameraControlPane extends ControlPane {

    // XML attributes

    /** Name of pane tag */
    private static final String PANE_NAME = "camera_pane";

    /** Attribute: ID for pane (integer) */
    private static final String PANE_ID = "id";
    /** Attribute: ID for camera to select (String) */
    private static final String CAMERA_ID = "camera_id";

    // End XML attributes

    private static int mCameraPaneIdCounter = 0;

    /**
     * These correspond to the callbacks from
     * android.hardware.camera2.CameraDevice.StateListener, plus UNAVAILABLE for
     * when there's not a valid camera selected.
     */
    private enum CameraState {
        UNAVAILABLE,
        CLOSED,
        OPENED,
        UNCONFIGURED,
        BUSY,
        IDLE,
        ACTIVE,
        DISCONNECTED,
        ERROR
    }

    private enum CameraCall {
        NONE,
        CONFIGURE
    }

    private final int mPaneId;

    private CameraOps2 mCameraOps;
    private InfoDisplayer mInfoDisplayer;

    private Spinner mCameraSpinner;
    private ToggleButton mOpenButton;
    private Button mInfoButton;
    private TextView mStatusText;
    private Button mConfigureButton;
    private Button mStopButton;
    private Button mFlushButton;

    /**
     * All controls that should be enabled when there's a valid camera ID
     * selected
     */
    private Set<View> mBaseControls = new HashSet<View>();
    /**
     * All controls that should be enabled when camera is at least in the OPEN
     * state
     */
    private Set<View> mOpenControls = new HashSet<View>();
    /**
     * All controls that should be enabled when camera is at least in the IDLE
     * state
     */
    private Set<View> mConfiguredControls = new HashSet<View>();

    private String[] mCameraIds;
    private String mCurrentCameraId;

    private CameraState mCameraState;
    private CameraDevice mCurrentCamera;
    private CameraCall mActiveCameraCall;

    private List<Surface> mConfiguredSurfaces;
    private List<TargetControlPane> mConfiguredTargetPanes;

    /**
     * Constructor for tooling only
     */
    public CameraControlPane(Context context, AttributeSet attrs) {
        super(context, attrs, null, null);

        mPaneId = 0;
        setUpUI(context);
    }

    public CameraControlPane(TestingCamera21 tc, AttributeSet attrs, StatusListener listener) {

        super(tc, attrs, listener, tc.getPaneTracker());

        mPaneId = mCameraPaneIdCounter++;
        setUpUI(tc);
        initializeCameras(tc);

        if (mCameraIds != null) {
            switchToCamera(mCameraIds[0]);
        }
    }

    public CameraControlPane(TestingCamera21 tc, XmlPullParser configParser, StatusListener listener)
            throws XmlPullParserException, IOException {
        super(tc, null, listener, tc.getPaneTracker());

        configParser.require(XmlPullParser.START_TAG, XmlPullParser.NO_NAMESPACE, PANE_NAME);

        int paneId = getAttributeInt(configParser, PANE_ID, -1);
        if (paneId == -1) {
            mPaneId = mCameraPaneIdCounter++;
        } else {
            mPaneId = paneId;
            if (mPaneId >= mCameraPaneIdCounter) {
                mCameraPaneIdCounter = mPaneId + 1;
            }
        }

        String cameraId = getAttributeString(configParser, CAMERA_ID, null);

        configParser.next();
        configParser.require(XmlPullParser.END_TAG, XmlPullParser.NO_NAMESPACE, PANE_NAME);

        setUpUI(tc);
        initializeCameras(tc);

        boolean gotCamera = false;
        if (mCameraIds != null && cameraId != null) {
            for (int i = 0; i < mCameraIds.length; i++) {
                if (cameraId.equals(mCameraIds[i])) {
                    switchToCamera(mCameraIds[i]);
                    mCameraSpinner.setSelection(i);
                    gotCamera = true;
                }
            }
        }

        if (!gotCamera && mCameraIds != null) {
            switchToCamera(mCameraIds[0]);
        }
    }

    @Override
    public void remove() {
        closeCurrentCamera();
        super.remove();
    }

    /**
     * Get list of target panes that are currently actively configured for this
     * camera
     */
    public List<TargetControlPane> getCurrentConfiguredTargets() {
        return mConfiguredTargetPanes;
    }

    /**
     * Interface to be implemented by an application service for displaying a
     * camera's information.
     */
    public interface InfoDisplayer {
        public void showCameraInfo(String cameraId);
    }

    public CameraCharacteristics getCharacteristics() {
        if (mCurrentCameraId != null) {
            return mCameraOps.getCameraInfo(mCurrentCameraId);
        }
        return null;
    }

    public CaptureRequest.Builder getRequestBuilder(int template) {
        if (mCurrentCamera != null) {
            try {
                return mCurrentCamera.createCaptureRequest(template);
            } catch (CameraAccessException e) {
                TLog.e("Unable to build request for camera %s with template %d.", e,
                        mCurrentCameraId, template);
            }
        }
        return null;
    }

    /**
     * Send single capture to camera device.
     *
     * @param request
     * @return true if capture sent successfully
     */
    public boolean capture(CaptureRequest request) {
        if (mCurrentCamera != null) {
            try {
                mCurrentCamera.capture(request, null, null);
                return true;
            } catch (CameraAccessException e) {
                TLog.e("Unable to capture for camera %s.", e, mCurrentCameraId);
            }
        }
        return false;
    }

    public boolean repeat(CaptureRequest request) {
        if (mCurrentCamera != null) {
            try {
                mCurrentCamera.setRepeatingRequest(request, null, null);
                return true;
            } catch (CameraAccessException e) {
                TLog.e("Unable to set repeating request for camera %s.", e, mCurrentCameraId);
            }
        }
        return false;
    }

    private void setUpUI(Context context) {
        String paneName =
                String.format(Locale.US, "%s %c",
                        context.getResources().getString(R.string.camera_pane_title),
                        (char) ('A' + mPaneId));
        this.setName(paneName);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate(R.layout.camera_pane, this);

        mCameraSpinner = (Spinner) findViewById(R.id.camera_pane_camera_spinner);
        mCameraSpinner.setOnItemSelectedListener(mCameraSpinnerListener);

        mOpenButton = (ToggleButton) findViewById(R.id.camera_pane_open_button);
        mOpenButton.setOnCheckedChangeListener(mOpenButtonListener);
        mBaseControls.add(mOpenButton);

        mInfoButton = (Button) findViewById(R.id.camera_pane_info_button);
        mInfoButton.setOnClickListener(mInfoButtonListener);
        mBaseControls.add(mInfoButton);

        mStatusText = (TextView) findViewById(R.id.camera_pane_status_text);

        mConfigureButton = (Button) findViewById(R.id.camera_pane_configure_button);
        mConfigureButton.setOnClickListener(mConfigureButtonListener);
        mOpenControls.add(mConfigureButton);

        mStopButton = (Button) findViewById(R.id.camera_pane_stop_button);
        mStopButton.setOnClickListener(mStopButtonListener);
        mConfiguredControls.add(mStopButton);
        mFlushButton = (Button) findViewById(R.id.camera_pane_flush_button);
        mFlushButton.setOnClickListener(mFlushButtonListener);
        mConfiguredControls.add(mFlushButton);
    }

    private void initializeCameras(TestingCamera21 tc) {
        mCameraOps = tc.getCameraOps();
        mInfoDisplayer = (InfoDisplayer) tc;

        updateCameraList();
    }

    private void updateCameraList() {
        mCameraIds = null;
        try {
            mCameraIds = mCameraOps.getCamerasAndListen(mCameraAvailabilityListener);
            String[] cameraSpinnerItems = new String[mCameraIds.length];
            for (int i = 0; i < mCameraIds.length; i++) {
                cameraSpinnerItems[i] = String.format("Camera %s", mCameraIds[i]);
            }
            mCameraSpinner.setAdapter(new ArrayAdapter<String>(getContext(), R.layout.spinner_item,
                    cameraSpinnerItems));

        } catch (CameraAccessException e) {
            TLog.e("Exception trying to get list of cameras: " + e);
        }
    }

    private CompoundButton.OnCheckedChangeListener mOpenButtonListener =
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        // Open camera
                        mCurrentCamera = null;
                        boolean success = mCameraOps.openCamera(mCurrentCameraId, mCameraListener);
                        buttonView.setChecked(success);
                    } else {
                        // Close camera
                        closeCurrentCamera();
                    }
                }
            };

    private OnClickListener mInfoButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mInfoDisplayer.showCameraInfo(mCurrentCameraId);
        }
    };

    private OnClickListener mStopButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mCurrentCamera != null) {
                try {
                    mCurrentCamera.stopRepeating();
                } catch (CameraAccessException e) {
                    TLog.e("Unable to stop repeating request for camera %s.", e, mCurrentCameraId);
                }
            }
        }
    };

    private OnClickListener mFlushButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mCurrentCamera != null) {
                try {
                    mCurrentCamera.flush();
                } catch (CameraAccessException e) {
                    TLog.e("Unable to flush camera %s.", e, mCurrentCameraId);
                }
            }
        }
    };

    private OnClickListener mConfigureButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            List<Surface> targetSurfaces = new ArrayList<Surface>();
            List<TargetControlPane> targetPanes = new ArrayList<TargetControlPane>();
            for (TargetControlPane targetPane : mPaneTracker.getPanes(TargetControlPane.class)) {
                Surface target = targetPane.getTargetSurfaceForCameraPane(getPaneName());
                if (target != null) {
                    targetSurfaces.add(target);
                    targetPanes.add(targetPane);
                }
            }
            try {
                TLog.i("Configuring camera %s with %d surfaces", mCurrentCamera.getId(),
                        targetSurfaces.size());
                mActiveCameraCall = CameraCall.CONFIGURE;
                if (targetSurfaces.size() > 0) {
                    mCurrentCamera.configureOutputs(targetSurfaces);
                } else {
                    mCurrentCamera.configureOutputs(null);
                }
                mConfiguredSurfaces = targetSurfaces;
                mConfiguredTargetPanes = targetPanes;
            } catch (CameraAccessException e) {
                mActiveCameraCall = CameraCall.NONE;
                TLog.e("Unable to configure camera %s.", e, mCurrentCamera.getId());
            } catch (IllegalArgumentException e) {
                mActiveCameraCall = CameraCall.NONE;
                TLog.e("Unable to configure camera %s.", e, mCurrentCamera.getId());
            } catch (IllegalStateException e) {
                mActiveCameraCall = CameraCall.NONE;
                TLog.e("Unable to configure camera %s.", e, mCurrentCamera.getId());
            }
        }
    };

    private CameraDevice.StateListener mCameraListener = new CameraDevice.StateListener() {
        @Override
        public void onIdle(CameraDevice camera) {
            setCameraState(CameraState.IDLE);
        }

        @Override
        public void onActive(CameraDevice camera) {
            setCameraState(CameraState.ACTIVE);
        }

        @Override
        public void onBusy(CameraDevice camera) {
            setCameraState(CameraState.BUSY);
        }

        @Override
        public void onClosed(CameraDevice camera) {
            // Don't change state on close, tracked by callers of close()
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            setCameraState(CameraState.DISCONNECTED);
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            setCameraState(CameraState.ERROR);
        }

        @Override
        public void onOpened(CameraDevice camera) {
            mCurrentCamera = camera;
            setCameraState(CameraState.OPENED);
        }

        @Override
        public void onUnconfigured(CameraDevice camera) {
            setCameraState(CameraState.UNCONFIGURED);
        }

    };

    private void switchToCamera(String newCameraId) {
        closeCurrentCamera();

        mCurrentCameraId = newCameraId;

        if (mCurrentCameraId == null) {
            setCameraState(CameraState.UNAVAILABLE);
        } else {
            setCameraState(CameraState.CLOSED);
        }

        mPaneTracker.notifyOtherPanes(this, PaneTracker.PaneEvent.NEW_CAMERA_SELECTED);
    }

    private void closeCurrentCamera() {
        if (mCurrentCamera != null) {
            mCurrentCamera.close();
            mCurrentCamera = null;
            setCameraState(CameraState.CLOSED);
            mOpenButton.setChecked(false);
        }
    }

    private void setCameraState(CameraState newState) {
        mCameraState = newState;
        mStatusText.setText(mCameraState.toString());
        switch (mCameraState) {
            case UNAVAILABLE:
                enableBaseControls(false);
                enableOpenControls(false);
                enableConfiguredControls(false);
                mConfiguredTargetPanes = null;
                break;
            case CLOSED:
            case DISCONNECTED:
            case ERROR:
                enableBaseControls(true);
                enableOpenControls(false);
                enableConfiguredControls(false);
                mConfiguredTargetPanes = null;
                break;
            case OPENED:
            case UNCONFIGURED:
                enableBaseControls(true);
                enableOpenControls(true);
                enableConfiguredControls(false);
                mConfiguredTargetPanes = null;
                if (mActiveCameraCall == CameraCall.CONFIGURE) {
                    mPaneTracker.notifyOtherPanes(this, PaneEvent.CAMERA_CONFIGURED);
                    mActiveCameraCall = CameraCall.NONE;
                }
                break;
            case BUSY:
                enableBaseControls(true);
                enableOpenControls(false);
                enableConfiguredControls(false);
                break;
            case IDLE:
                if (mActiveCameraCall == CameraCall.CONFIGURE) {
                    mPaneTracker.notifyOtherPanes(this, PaneEvent.CAMERA_CONFIGURED);
                    mActiveCameraCall = CameraCall.NONE;
                }
                // fallthrough
            case ACTIVE:
                enableBaseControls(true);
                enableOpenControls(true);
                enableConfiguredControls(true);
                break;
        }
    }

    private void enableBaseControls(boolean enabled) {
        for (View v : mBaseControls) {
            v.setEnabled(enabled);
        }
        if (!enabled) {
            mOpenButton.setChecked(false);
        }
    }

    private void enableOpenControls(boolean enabled) {
        for (View v : mOpenControls) {
            v.setEnabled(enabled);
        }
    }

    private void enableConfiguredControls(boolean enabled) {
        for (View v : mConfiguredControls) {
            v.setEnabled(enabled);
        }
    }

    private final CameraManager.AvailabilityListener mCameraAvailabilityListener =
            new CameraManager.AvailabilityListener() {
        @Override
        public void onCameraAvailable(String cameraId) {
            updateCameraList();
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            updateCameraList();
        }
    };

    private OnItemSelectedListener mCameraSpinnerListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            String newCameraId = mCameraIds[pos];
            if (newCameraId != mCurrentCameraId) {
                switchToCamera(newCameraId);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            switchToCamera(null);
        }
    };

}
