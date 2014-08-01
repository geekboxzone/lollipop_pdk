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

package com.android.camera2.its;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingStateListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ItsService extends Service implements SensorEventListener {
    public static final String TAG = ItsService.class.getSimpleName();

    // Timeouts, in seconds.
    public static final int TIMEOUT_CAPTURE = 10;
    public static final int TIMEOUT_3A = 10;

    // State transition timeouts, in ms.
    private static final long TIMEOUT_IDLE_MS = 2000;
    private static final long TIMEOUT_STATE_MS = 500;

    private static final int MAX_CONCURRENT_READER_BUFFERS = 8;

    // Supports at most RAW+YUV+JPEG, one surface each.
    private static final int MAX_NUM_OUTPUT_SURFACES = 3;

    public static final int SERVERPORT = 6000;

    public static final String REGION_KEY = "regions";
    public static final String REGION_AE_KEY = "ae";
    public static final String REGION_AWB_KEY = "awb";
    public static final String REGION_AF_KEY = "af";
    public static final String TRIGGER_KEY = "triggers";
    public static final String TRIGGER_AE_KEY = "ae";
    public static final String TRIGGER_AF_KEY = "af";
    public static final String VIB_PATTERN_KEY = "pattern";

    private CameraManager mCameraManager = null;
    private HandlerThread mCameraThread = null;
    private BlockingCameraManager mBlockingCameraManager = null;
    private BlockingStateListener mCameraListener = null;
    private CameraDevice mCamera = null;
    private ImageReader[] mCaptureReaders = null;
    private CameraCharacteristics mCameraCharacteristics = null;

    private Vibrator mVibrator = null;

    private HandlerThread mSaveThreads[] = new HandlerThread[MAX_NUM_OUTPUT_SURFACES];
    private Handler mSaveHandlers[] = new Handler[MAX_NUM_OUTPUT_SURFACES];
    private HandlerThread mResultThread = null;
    private Handler mResultHandler = null;

    private volatile ServerSocket mSocket = null;
    private volatile SocketRunnable mSocketRunnableObj = null;
    private volatile Thread mSocketThread = null;
    private volatile Thread mSocketWriteRunnable = null;
    private volatile boolean mSocketThreadExitFlag = false;
    private volatile BlockingQueue<ByteBuffer> mSocketWriteQueue =
            new LinkedBlockingDeque<ByteBuffer>();
    private final Object mSocketWriteLock = new Object();

    private AtomicInteger mCountRawOrDng = new AtomicInteger();
    private AtomicInteger mCountRaw10 = new AtomicInteger();
    private AtomicInteger mCountJpg = new AtomicInteger();
    private AtomicInteger mCountYuv = new AtomicInteger();
    private AtomicInteger mCountCapRes = new AtomicInteger();
    private boolean mCaptureRawIsDng;
    private CaptureResult mCaptureResults[] = null;

    private volatile ConditionVariable mInterlock3A = new ConditionVariable(true);
    private volatile boolean mIssuedRequest3A = false;
    private volatile boolean mConvergedAE = false;
    private volatile boolean mConvergedAF = false;
    private volatile boolean mConvergedAWB = false;

    class MySensorEvent {
        public Sensor sensor;
        public int accuracy;
        public long timestamp;
        public float values[];
    };

    // For capturing motion sensor traces.
    private SensorManager mSensorManager = null;
    private Sensor mAccelSensor = null;
    private Sensor mMagSensor = null;
    private Sensor mGyroSensor = null;
    private volatile LinkedList<MySensorEvent> mEvents = null;
    private volatile Object mEventLock = new Object();
    private volatile boolean mEventsEnabled = false;

    private CountDownLatch mCaptureCallbackLatch;

    public interface CaptureListener {
        void onCaptureAvailable(Image capture);
    }

    public abstract class CaptureResultListener extends CameraDevice.CaptureListener {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // Get handle to camera manager.
            mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            if (mCameraManager == null) {
                throw new ItsException("Failed to connect to camera manager");
            }
            mBlockingCameraManager = new BlockingCameraManager(mCameraManager);
            mCameraListener = new BlockingStateListener();

            // Open the camera device, and get its properties.
            String[] devices;
            try {
                devices = mCameraManager.getCameraIdList();
                if (devices == null || devices.length == 0) {
                    throw new ItsException("No camera devices");
                }
            } catch (CameraAccessException e) {
                throw new ItsException("Failed to get device ID list", e);
            }

            // Args are a string, which is just the camera ID to open.
            int cameraId = 0;
            String args = intent.getDataString();
            if (args != null) {
                Log.i(TAG, String.format("Received intent args: %s", args));
                cameraId = Integer.parseInt(args);
            }
            Log.i(TAG, String.format("Opening camera %d", cameraId));

            mCameraThread = new HandlerThread("ItsCameraThread");
            try {
                mCameraThread.start();
                Handler cameraHandler = new Handler(mCameraThread.getLooper());
                mCamera = mBlockingCameraManager.openCamera(devices[cameraId],
                        mCameraListener, cameraHandler);
                mCameraCharacteristics = mCameraManager.getCameraCharacteristics(
                        devices[cameraId]);
            } catch (CameraAccessException e) {
                throw new ItsException("Failed to open camera", e);
            } catch (BlockingOpenException e) {
                throw new ItsException("Failed to open camera (after blocking)", e);
            }

            // Register for motion events.
            mEvents = new LinkedList<MySensorEvent>();
            mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mSensorManager.registerListener(this, mAccelSensor, SensorManager.SENSOR_DELAY_FASTEST);
            mSensorManager.registerListener(this, mMagSensor, SensorManager.SENSOR_DELAY_FASTEST);
            mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_FASTEST);

            // Get a handle to the system vibrator.
            mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

            // Create threads to receive images and save them.
            for (int i = 0; i < MAX_NUM_OUTPUT_SURFACES; i++) {
                mSaveThreads[i] = new HandlerThread("SaveThread" + i);
                mSaveThreads[i].start();
                mSaveHandlers[i] = new Handler(mSaveThreads[i].getLooper());
            }

            // Create a thread to receive capture results and process them.
            mResultThread = new HandlerThread("ResultThread");
            mResultThread.start();
            mResultHandler = new Handler(mResultThread.getLooper());

            // Create a thread to process commands, listening on a TCP socket.
            mSocketRunnableObj = new SocketRunnable();
            mSocketThread = new Thread(mSocketRunnableObj);
            mSocketThread.start();
        } catch (ItsException e) {
            Log.e(TAG, "Service failed to start: ", e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            mSocketThreadExitFlag = true;
            for (int i = 0; i < MAX_NUM_OUTPUT_SURFACES; i++) {
                if (mSaveThreads[i] != null) {
                    mSaveThreads[i].quit();
                    mSaveThreads[i] = null;
                }
            }
            if (mCameraThread != null) {
                mCameraThread.quitSafely();
                mCameraThread = null;
            }
            try {
                mCamera.close();
            } catch (Exception e) {
                throw new ItsException("Failed to close device");
            }
        } catch (ItsException e) {
            Log.e(TAG, "Script failed: ", e);
        }
    }

    class SocketWriteRunnable implements Runnable {

        // Use a separate thread to service a queue of objects to be written to the socket,
        // writing each sequentially in order. This is needed since different handler functions
        // (called on different threads) will need to send data back to the host script.

        public Socket mOpenSocket = null;

        public SocketWriteRunnable(Socket openSocket) {
            mOpenSocket = openSocket;
        }

        public void run() {
            Log.i(TAG, "Socket writer thread starting");
            while (true) {
                try {
                    ByteBuffer b = mSocketWriteQueue.take();
                    if (b.hasArray()) {
                        mOpenSocket.getOutputStream().write(b.array());
                    } else {
                        byte[] barray = new byte[b.capacity()];
                        b.get(barray);
                        mOpenSocket.getOutputStream().write(barray);
                    }
                    mOpenSocket.getOutputStream().flush();
                    Log.i(TAG, String.format("Wrote to socket: %d bytes", b.capacity()));
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to socket", e);
                    break;
                } catch (java.lang.InterruptedException e) {
                    Log.e(TAG, "Error writing to socket (interrupted)", e);
                    break;
                }
            }
            Log.i(TAG, "Socket writer thread terminated");
        }
    }

    class SocketRunnable implements Runnable {

        // Format of sent messages (over the socket):
        // * Serialized JSON object on a single line (newline-terminated)
        // * For byte buffers, the binary data then follows
        //
        // Format of received messages (from the socket):
        // * Serialized JSON object on a single line (newline-terminated)

        private Socket mOpenSocket = null;
        private SocketWriteRunnable mSocketWriteRunnable = null;

        public void run() {
            Log.i(TAG, "Socket thread starting");
            try {
                mSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create socket", e);
            }
            try {
                Log.i(TAG, "Waiting for client to connect to socket");
                mOpenSocket = mSocket.accept();
                if (mOpenSocket == null) {
                    Log.e(TAG, "Socket connection error");
                    return;
                }
                Log.i(TAG, "Socket connected");
            } catch (IOException e) {
                Log.e(TAG, "Socket open error: ", e);
                return;
            }
            mSocketThread = new Thread(new SocketWriteRunnable(mOpenSocket));
            mSocketThread.start();
            while (!mSocketThreadExitFlag) {
                try {
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(mOpenSocket.getInputStream()));
                    if (input == null) {
                        Log.e(TAG, "Failed to get socket input stream");
                        break;
                    }
                    String line = input.readLine();
                    if (line == null) {
                        Log.e(TAG, "Failed to read socket line");
                        break;
                    }
                    processSocketCommand(line);
                } catch (IOException e) {
                    Log.e(TAG, "Socket read error: ", e);
                    break;
                } catch (ItsException e) {
                    Log.e(TAG, "Script error: ", e);
                    break;
                }
            }
            Log.i(TAG, "Socket server loop exited");
            try {
                if (mOpenSocket != null) {
                    mOpenSocket.close();
                    mOpenSocket = null;
                }
            } catch (java.io.IOException e) {
                Log.w(TAG, "Exception closing socket");
            }
            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
            } catch (java.io.IOException e) {
                Log.w(TAG, "Exception closing socket");
            }
            Log.i(TAG, "Socket server thread exited");
        }

        public void processSocketCommand(String cmd)
                throws ItsException {
            // Each command is a serialized JSON object.
            try {
                JSONObject cmdObj = new JSONObject(cmd);
                if ("getCameraProperties".equals(cmdObj.getString("cmdName"))) {
                    doGetProps();
                } else if ("startSensorEvents".equals(cmdObj.getString("cmdName"))) {
                    doStartSensorEvents();
                } else if ("getSensorEvents".equals(cmdObj.getString("cmdName"))) {
                    doGetSensorEvents();
                } else if ("do3A".equals(cmdObj.getString("cmdName"))) {
                    do3A(cmdObj);
                } else if ("doCapture".equals(cmdObj.getString("cmdName"))) {
                    doCapture(cmdObj);
                } else if ("doVibrate".equals(cmdObj.getString("cmdName"))) {
                    doVibrate(cmdObj);
                } else {
                    throw new ItsException("Unknown command: " + cmd);
                }
            } catch (org.json.JSONException e) {
                Log.e(TAG, "Invalid command: ", e);
            }
        }

        public void sendResponse(String tag, String str, JSONObject obj, ByteBuffer bbuf)
                throws ItsException {
            try {
                JSONObject jsonObj = new JSONObject();
                jsonObj.put("tag", tag);
                if (str != null) {
                    jsonObj.put("strValue", str);
                }
                if (obj != null) {
                    jsonObj.put("objValue", obj);
                }
                if (bbuf != null) {
                    jsonObj.put("bufValueSize", bbuf.capacity());
                }
                ByteBuffer bstr = ByteBuffer.wrap(
                        (jsonObj.toString()+"\n").getBytes(Charset.defaultCharset()));
                synchronized(mSocketWriteLock) {
                    if (bstr != null) {
                        mSocketWriteQueue.put(bstr);
                    }
                    if (bbuf != null) {
                        mSocketWriteQueue.put(bbuf);
                    }
                }
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            } catch (java.lang.InterruptedException e) {
                throw new ItsException("Socket error: ", e);
            }
        }

        public void sendResponse(String tag, String str)
                throws ItsException {
            sendResponse(tag, str, null, null);
        }

        public void sendResponse(String tag, JSONObject obj)
                throws ItsException {
            sendResponse(tag, null, obj, null);
        }

        public void sendResponseCaptureBuffer(String tag, ByteBuffer bbuf, int byteStride)
                throws ItsException {
            try {
                JSONObject obj = null;
                if (byteStride > 0) {
                    obj = new JSONObject();
                    obj.put("byteStride", byteStride);
                }
                sendResponse(tag, null, obj, bbuf);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            }
        }

        public void sendResponse(LinkedList<MySensorEvent> events)
                throws ItsException {
            try {
                JSONArray accels = new JSONArray();
                JSONArray mags = new JSONArray();
                JSONArray gyros = new JSONArray();
                for (MySensorEvent event : events) {
                    JSONObject obj = new JSONObject();
                    obj.put("time", event.timestamp);
                    obj.put("x", event.values[0]);
                    obj.put("y", event.values[1]);
                    obj.put("z", event.values[2]);
                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        accels.put(obj);
                    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        mags.put(obj);
                    } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        gyros.put(obj);
                    }
                }
                JSONObject obj = new JSONObject();
                obj.put("accel", accels);
                obj.put("mag", mags);
                obj.put("gyro", gyros);
                sendResponse("sensorEvents", null, obj, null);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            }
        }

        public void sendResponse(CameraCharacteristics props)
                throws ItsException {
            try {
                JSONObject jsonObj = new JSONObject();
                jsonObj.put("cameraProperties", ItsSerializer.serialize(props));
                sendResponse("cameraProperties", null, jsonObj, null);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            }
        }

        public void sendResponseCaptureResult(CameraCharacteristics props,
                                              CaptureRequest request,
                                              CaptureResult result,
                                              ImageReader[] readers)
                throws ItsException {
            try {
                JSONObject jsonObj = new JSONObject();
                jsonObj.put("cameraProperties", ItsSerializer.serialize(props));
                jsonObj.put("captureRequest", ItsSerializer.serialize(request));
                jsonObj.put("captureResult", ItsSerializer.serialize(result));
                JSONArray jsonSurfaces = new JSONArray();
                for (int i = 0; i < readers.length; i++) {
                    JSONObject jsonSurface = new JSONObject();
                    jsonSurface.put("width", readers[i].getWidth());
                    jsonSurface.put("height", readers[i].getHeight());
                    int format = readers[i].getImageFormat();
                    if (format == ImageFormat.RAW_SENSOR) {
                        jsonSurface.put("format", "raw");
                    } else if (format == ImageFormat.RAW10) {
                        jsonSurface.put("format", "raw10");
                    } else if (format == ImageFormat.JPEG) {
                        jsonSurface.put("format", "jpeg");
                    } else if (format == ImageFormat.YUV_420_888) {
                        jsonSurface.put("format", "yuv");
                    } else {
                        throw new ItsException("Invalid format");
                    }
                    jsonSurfaces.put(jsonSurface);
                }
                jsonObj.put("outputs", jsonSurfaces);
                sendResponse("captureResults", null, jsonObj, null);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            }
        }
    }

    public ImageReader.OnImageAvailableListener
            createAvailableListener(final CaptureListener listener) {
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image i = null;
                try {
                    i = reader.acquireNextImage();
                    listener.onCaptureAvailable(i);
                } finally {
                    if (i != null) {
                        i.close();
                    }
                }
            }
        };
    }

    private ImageReader.OnImageAvailableListener
            createAvailableListenerDropper(final CaptureListener listener) {
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image i = reader.acquireNextImage();
                i.close();
            }
        };
    }

    private void doStartSensorEvents() throws ItsException {
        synchronized(mEventLock) {
            mEventsEnabled = true;
        }
        mSocketRunnableObj.sendResponse("sensorEventsStarted", "");
    }

    private void doGetSensorEvents() throws ItsException {
        synchronized(mEventLock) {
            mSocketRunnableObj.sendResponse(mEvents);
            mEvents.clear();
            mEventsEnabled = false;
        }
    }

    private void doGetProps() throws ItsException {
        mSocketRunnableObj.sendResponse(mCameraCharacteristics);
    }

    private void prepareCaptureReader(int[] widths, int[] heights, int formats[], int numSurfaces) {
        if (mCaptureReaders != null) {
            for (int i = 0; i < mCaptureReaders.length; i++) {
                if (mCaptureReaders[i] != null) {
                    mCaptureReaders[i].close();
                }
            }
        }
        mCaptureReaders = new ImageReader[numSurfaces];
        for (int i = 0; i < numSurfaces; i++) {
            mCaptureReaders[i] = ImageReader.newInstance(widths[i], heights[i], formats[i],
                    MAX_CONCURRENT_READER_BUFFERS);
        }
    }

    private void do3A(JSONObject params) throws ItsException {
        try {
            // Start a 3A action, and wait for it to converge.
            // Get the converged values for each "A", and package into JSON result for caller.

            // 3A happens on full-res frames.
            Size sizes[] = ItsUtils.getYuvOutputSizes(mCameraCharacteristics);
            int widths[] = new int[1];
            int heights[] = new int[1];
            int formats[] = new int[1];
            widths[0] = sizes[0].getWidth();
            heights[0] = sizes[0].getHeight();
            formats[0] = ImageFormat.YUV_420_888;
            int width = widths[0];
            int height = heights[0];

            prepareCaptureReader(widths, heights, formats, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(1);
            outputSurfaces.add(mCaptureReaders[0].getSurface());
            mCamera.configureOutputs(outputSurfaces);
            mCameraListener.waitForState(BlockingStateListener.STATE_BUSY,
                    TIMEOUT_STATE_MS);
            mCameraListener.waitForState(BlockingStateListener.STATE_IDLE,
                    TIMEOUT_IDLE_MS);

            // Add a listener that just recycles buffers; they aren't saved anywhere.
            ImageReader.OnImageAvailableListener readerListener =
                    createAvailableListenerDropper(mCaptureListener);
            mCaptureReaders[0].setOnImageAvailableListener(readerListener, mSaveHandlers[0]);

            // Get the user-specified regions for AE, AWB, AF.
            // Note that the user specifies normalized [x,y,w,h], which is converted below
            // to an [x0,y0,x1,y1] region in sensor coords. The capture request region
            // also has a fifth "weight" element: [x0,y0,x1,y1,w].
            MeteringRectangle[] regionAE = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,width,height,1)};
            MeteringRectangle[] regionAF = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,width,height,1)};
            MeteringRectangle[] regionAWB = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,width,height,1)};
            if (params.has(REGION_KEY)) {
                JSONObject regions = params.getJSONObject(REGION_KEY);
                if (regions.has(REGION_AE_KEY)) {
                    regionAE = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AE_KEY), true, width, height);
                }
                if (regions.has(REGION_AF_KEY)) {
                    regionAF = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AF_KEY), true, width, height);
                }
                if (regions.has(REGION_AWB_KEY)) {
                    regionAWB = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AWB_KEY), true, width, height);
                }
            }

            // By default, AE and AF both get triggered, but the user can optionally override this.
            // Also, AF won't get triggered if the lens is fixed-focus.
            boolean doAE = true;
            boolean doAF = true;
            if (params.has(TRIGGER_KEY)) {
                JSONObject triggers = params.getJSONObject(TRIGGER_KEY);
                if (triggers.has(TRIGGER_AE_KEY)) {
                    doAE = triggers.getBoolean(TRIGGER_AE_KEY);
                }
                if (triggers.has(TRIGGER_AF_KEY)) {
                    doAF = triggers.getBoolean(TRIGGER_AF_KEY);
                }
            }
            if (doAF && mCameraCharacteristics.get(
                            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) == 0) {
                // Send a dummy result back for the code that is waiting for this message to see
                // that AF has converged.
                Log.i(TAG, "Ignoring request for AF on fixed-focus camera");
                mSocketRunnableObj.sendResponse("afResult", "0.0");
                doAF = false;
            }

            mInterlock3A.open();
            mIssuedRequest3A = false;
            mConvergedAE = false;
            mConvergedAWB = false;
            mConvergedAF = false;
            long tstart = System.currentTimeMillis();
            boolean triggeredAE = false;
            boolean triggeredAF = false;

            // Keep issuing capture requests until 3A has converged.
            while (true) {

                // Block until can take the next 3A frame. Only want one outstanding frame
                // at a time, to simplify the logic here.
                if (!mInterlock3A.block(TIMEOUT_3A * 1000) ||
                        System.currentTimeMillis() - tstart > TIMEOUT_3A * 1000) {
                    throw new ItsException("3A failed to converge (timeout)");
                }
                mInterlock3A.close();

                // If not converged yet, issue another capture request.
                if ((doAE && !mConvergedAE) || !mConvergedAWB || (doAF && !mConvergedAF)) {

                    // Baseline capture request for 3A.
                    CaptureRequest.Builder req = mCamera.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW);
                    req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    req.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    req.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                            CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
                    req.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
                    req.set(CaptureRequest.CONTROL_AE_LOCK, false);
                    req.set(CaptureRequest.CONTROL_AE_REGIONS, regionAE);
                    req.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_AUTO);
                    req.set(CaptureRequest.CONTROL_AF_REGIONS, regionAF);
                    req.set(CaptureRequest.CONTROL_AWB_MODE,
                            CaptureRequest.CONTROL_AWB_MODE_AUTO);
                    req.set(CaptureRequest.CONTROL_AWB_LOCK, false);
                    req.set(CaptureRequest.CONTROL_AWB_REGIONS, regionAWB);

                    // Trigger AE first.
                    if (doAE && !triggeredAE) {
                        Log.i(TAG, "Triggering AE");
                        req.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        triggeredAE = true;
                    }

                    // After AE has converged, trigger AF.
                    if (doAF && !triggeredAF && (!doAE || (triggeredAE && mConvergedAE))) {
                        Log.i(TAG, "Triggering AF");
                        req.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        triggeredAF = true;
                    }

                    req.addTarget(mCaptureReaders[0].getSurface());

                    mIssuedRequest3A = true;
                    mCamera.capture(req.build(), mCaptureResultListener, mResultHandler);
                } else {
                    Log.i(TAG, "3A converged");
                    break;
                }
            }
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        } finally {
            mSocketRunnableObj.sendResponse("3aDone", "");
        }
    }

    private void doVibrate(JSONObject params) throws ItsException {
        try {
            if (mVibrator == null) {
                throw new ItsException("Unable to start vibrator");
            }
            JSONArray patternArray = params.getJSONArray(VIB_PATTERN_KEY);
            int len = patternArray.length();
            long pattern[] = new long[len];
            for (int i = 0; i < len; i++) {
                pattern[i] = patternArray.getLong(i);
            }
            Log.i(TAG, String.format("Starting vibrator, pattern length %d",len));
            mVibrator.vibrate(pattern, -1);
            mSocketRunnableObj.sendResponse("vibrationStarted", "");
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    private void doCapture(JSONObject params) throws ItsException {
        try {
            // Parse the JSON to get the list of capture requests.
            List<CaptureRequest.Builder> requests = ItsSerializer.deserializeRequestList(
                    mCamera, params);

            // Set the output surface(s) and listeners.
            int widths[] = new int[MAX_NUM_OUTPUT_SURFACES];
            int heights[] = new int[MAX_NUM_OUTPUT_SURFACES];
            int formats[] = new int[MAX_NUM_OUTPUT_SURFACES];
            int numSurfaces = 0;
            try {
                mCountRawOrDng.set(0);
                mCountJpg.set(0);
                mCountYuv.set(0);
                mCountRaw10.set(0);
                mCountCapRes.set(0);
                mCaptureRawIsDng = false;
                mCaptureResults = new CaptureResult[requests.size()];

                JSONArray jsonOutputSpecs = ItsUtils.getOutputSpecs(params);
                if (jsonOutputSpecs != null) {
                    numSurfaces = jsonOutputSpecs.length();
                    if (numSurfaces > MAX_NUM_OUTPUT_SURFACES) {
                        throw new ItsException("Too many output surfaces");
                    }
                    for (int i = 0; i < numSurfaces; i++) {
                        // Get the specified surface.
                        JSONObject surfaceObj = jsonOutputSpecs.getJSONObject(i);
                        String sformat = surfaceObj.optString("format");
                        Size sizes[];
                        if ("yuv".equals(sformat) || "".equals(sformat)) {
                            // Default to YUV if no format is specified.
                            formats[i] = ImageFormat.YUV_420_888;
                            sizes = ItsUtils.getYuvOutputSizes(mCameraCharacteristics);
                        } else if ("jpg".equals(sformat) || "jpeg".equals(sformat)) {
                            formats[i] = ImageFormat.JPEG;
                            sizes = ItsUtils.getJpegOutputSizes(mCameraCharacteristics);
                        } else if ("raw".equals(sformat)) {
                            formats[i] = ImageFormat.RAW_SENSOR;
                            sizes = ItsUtils.getRawOutputSizes(mCameraCharacteristics);
                        } else if ("raw10".equals(sformat)) {
                            formats[i] = ImageFormat.RAW10;
                            sizes = ItsUtils.getRawOutputSizes(mCameraCharacteristics);
                        } else if ("dng".equals(sformat)) {
                            formats[i] = ImageFormat.RAW_SENSOR;
                            sizes = ItsUtils.getRawOutputSizes(mCameraCharacteristics);
                            mCaptureRawIsDng = true;
                        } else {
                            throw new ItsException("Unsupported format: " + sformat);
                        }
                        // If the size is omitted, then default to the largest allowed size for the
                        // format.
                        widths[i] = surfaceObj.optInt("width");
                        heights[i] = surfaceObj.optInt("height");
                        if (widths[i] <= 0) {
                            if (sizes == null || sizes.length == 0) {
                                throw new ItsException(String.format(
                                        "Zero stream configs available for requested format: %s",
                                        sformat));
                            }
                            widths[i] = sizes[0].getWidth();
                        }
                        if (heights[i] <= 0) {
                            heights[i] = sizes[0].getHeight();
                        }
                    }
                } else {
                    // No surface(s) specified at all.
                    // Default: a single output surface which is full-res YUV.
                    Size sizes[] =
                            ItsUtils.getYuvOutputSizes(mCameraCharacteristics);
                    numSurfaces = 1;
                    widths[0] = sizes[0].getWidth();
                    heights[0] = sizes[0].getHeight();
                    formats[0] = ImageFormat.YUV_420_888;
                }

                prepareCaptureReader(widths, heights, formats, numSurfaces);
                List<Surface> outputSurfaces = new ArrayList<Surface>(numSurfaces);
                for (int i = 0; i < numSurfaces; i++) {
                    outputSurfaces.add(mCaptureReaders[i].getSurface());
                }
                mCamera.configureOutputs(outputSurfaces);
                mCameraListener.waitForState(BlockingStateListener.STATE_BUSY,
                        TIMEOUT_STATE_MS);
                mCameraListener.waitForState(BlockingStateListener.STATE_IDLE,
                        TIMEOUT_IDLE_MS);

                for (int i = 0; i < numSurfaces; i++) {
                    ImageReader.OnImageAvailableListener readerListener =
                            createAvailableListener(mCaptureListener);
                    mCaptureReaders[i].setOnImageAvailableListener(readerListener,mSaveHandlers[i]);
                }

                // Plan for how many callbacks need to be received throughout the duration of this
                // sequence of capture requests. There is one callback per image surface, and one
                // callback for the CaptureResult, for each capture.
                int numCaptures = requests.size();
                mCaptureCallbackLatch = new CountDownLatch(numCaptures * (numSurfaces + 1));

            } catch (CameraAccessException e) {
                throw new ItsException("Error configuring outputs", e);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error", e);
            }

            // Initiate the captures.
            for (int i = 0; i < requests.size(); i++) {
                // For DNG captures, need the LSC map to be available.
                if (mCaptureRawIsDng) {
                    requests.get(i).set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, 1);
                }

                CaptureRequest.Builder req = requests.get(i);
                for (int j = 0; j < numSurfaces; j++) {
                    req.addTarget(mCaptureReaders[j].getSurface());
                }
                mCamera.capture(req.build(), mCaptureResultListener, mResultHandler);
            }

            // Make sure all callbacks have been hit (wait until captures are done).
            try {
                if (!mCaptureCallbackLatch.await(TIMEOUT_CAPTURE, TimeUnit.SECONDS)) {
                    throw new ItsException(
                            "Timeout hit, but all callbacks not received");
                }
            } catch (InterruptedException e) {
                throw new ItsException("Interrupted: ", e);
            }
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        synchronized(mEventLock) {
            if (mEventsEnabled) {
                MySensorEvent ev2 = new MySensorEvent();
                ev2.sensor = event.sensor;
                ev2.accuracy = event.accuracy;
                ev2.timestamp = event.timestamp;
                ev2.values = new float[event.values.length];
                System.arraycopy(event.values, 0, ev2.values, 0, event.values.length);
                mEvents.add(ev2);
            }
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private final CaptureListener mCaptureListener = new CaptureListener() {
        @Override
        public void onCaptureAvailable(Image capture) {
            try {
                int format = capture.getFormat();
                if (format == ImageFormat.JPEG) {
                    Log.i(TAG, "Received JPEG capture");
                    ByteBuffer buf = capture.getPlanes()[0].getBuffer();
                    int count = mCountJpg.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("jpegImage", buf, 0);
                } else if (format == ImageFormat.YUV_420_888) {
                    Log.i(TAG, "Received YUV capture");
                    byte[] img = ItsUtils.getDataFromImage(capture);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountYuv.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("yuvImage", buf, 0);
                } else if (format == ImageFormat.RAW10) {
                    Log.i(TAG, "Received RAW10 capture");
                    int byteStride = capture.getPlanes()[0].getRowStride();
                    byte[] img = ItsUtils.getDataFromImage(capture);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountRaw10.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("raw10Image", buf, byteStride);
                } else if (format == ImageFormat.RAW_SENSOR) {
                    Log.i(TAG, "Received RAW16 capture");
                    int count = mCountRawOrDng.getAndIncrement();
                    if (! mCaptureRawIsDng) {
                        byte[] img = ItsUtils.getDataFromImage(capture);
                        ByteBuffer buf = ByteBuffer.wrap(img);
                        mSocketRunnableObj.sendResponseCaptureBuffer("rawImage", buf, 0);
                    } else {
                        // Wait until the corresponding capture result is ready.
                        while (! mSocketThreadExitFlag) {
                            if (mCaptureResults[count] != null) {
                                Log.i(TAG, "Writing capture as DNG");
                                DngCreator dngCreator = new DngCreator(
                                        mCameraCharacteristics, mCaptureResults[count]);
                                ByteArrayOutputStream dngStream = new ByteArrayOutputStream();
                                dngCreator.writeImage(dngStream, capture);
                                byte[] dngArray = dngStream.toByteArray();
                                ByteBuffer dngBuf = ByteBuffer.wrap(dngArray);
                                mSocketRunnableObj.sendResponseCaptureBuffer("dngImage", dngBuf, 0);
                                break;
                            } else {
                                Thread.sleep(1);
                            }
                        }
                    }
                } else {
                    throw new ItsException("Unsupported image format: " + format);
                }
                mCaptureCallbackLatch.countDown();
            } catch (IOException e) {
                Log.e(TAG, "Script error: ", e);
                mSocketThreadExitFlag = true;
            } catch (InterruptedException e) {
                Log.e(TAG, "Script error: ", e);
                mSocketThreadExitFlag = true;
            } catch (ItsException e) {
                Log.e(TAG, "Script error: ", e);
                mSocketThreadExitFlag = true;
            }
        }
    };

    private static float r2f(Rational r) {
        return (float)r.getNumerator() / (float)r.getDenominator();
    }

    private final CaptureResultListener mCaptureResultListener = new CaptureResultListener() {
        @Override
        public void onCaptureStarted(CameraDevice camera, CaptureRequest request, long timestamp) {
        }

        @Override
        public void onCaptureCompleted(CameraDevice camera, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                // Currently result has all 0 values.
                if (request == null || result == null) {
                    throw new ItsException("Request/result is invalid");
                }

                StringBuilder logMsg = new StringBuilder();
                logMsg.append(String.format(
                        "Capt result: AE=%d, AF=%d, AWB=%d, sens=%d, exp=%.1fms, dur=%.1fms, ",
                        result.get(CaptureResult.CONTROL_AE_STATE),
                        result.get(CaptureResult.CONTROL_AF_STATE),
                        result.get(CaptureResult.CONTROL_AWB_STATE),
                        result.get(CaptureResult.SENSOR_SENSITIVITY),
                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME).intValue() / 1000000.0f,
                        result.get(CaptureResult.SENSOR_FRAME_DURATION).intValue() / 1000000.0f));
                if (result.get(CaptureResult.COLOR_CORRECTION_GAINS) != null) {
                    logMsg.append(String.format(
                            "gains=[%.1f, %.1f, %.1f, %.1f], ",
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getRed(),
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenEven(),
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenOdd(),
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getBlue()));
                } else {
                    logMsg.append("gains=[], ");
                }
                if (result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) != null) {
                    logMsg.append(String.format(
                            "xform=[%.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f], ",
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,0)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,0)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,0)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,1)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,1)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,1)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,2)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,2)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,2))));
                } else {
                    logMsg.append("xform=[], ");
                }
                logMsg.append(String.format(
                        "foc=%.1f",
                        result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
                Log.i(TAG, logMsg.toString());

                if (result.get(CaptureResult.CONTROL_AE_STATE) != null) {
                    mConvergedAE = result.get(CaptureResult.CONTROL_AE_STATE) ==
                                              CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                   result.get(CaptureResult.CONTROL_AE_STATE) ==
                                              CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED;
                }
                if (result.get(CaptureResult.CONTROL_AF_STATE) != null) {
                    mConvergedAF = result.get(CaptureResult.CONTROL_AF_STATE) ==
                                              CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED;
                }
                if (result.get(CaptureResult.CONTROL_AWB_STATE) != null) {
                    mConvergedAWB = result.get(CaptureResult.CONTROL_AWB_STATE) ==
                                               CaptureResult.CONTROL_AWB_STATE_CONVERGED;
                }

                if (mConvergedAE) {
                    mSocketRunnableObj.sendResponse("aeResult", String.format("%d %d",
                            result.get(CaptureResult.SENSOR_SENSITIVITY).intValue(),
                            result.get(CaptureResult.SENSOR_EXPOSURE_TIME).intValue()
                            ));
                }

                if (mConvergedAF) {
                    mSocketRunnableObj.sendResponse("afResult", String.format("%f",
                            result.get(CaptureResult.LENS_FOCUS_DISTANCE)
                            ));
                }

                if (mConvergedAWB) {
                    if (result.get(CaptureResult.COLOR_CORRECTION_GAINS) != null
                            && result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) != null) {
                        mSocketRunnableObj.sendResponse("awbResult", String.format(
                                "%f %f %f %f %f %f %f %f %f %f %f %f %f",
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getRed(),
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenEven(),
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenOdd(),
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getBlue(),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,0)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,0)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,0)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,1)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,1)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,1)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,2)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,2)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,2))
                                ));
                    } else {
                        Log.i(TAG, String.format(
                                "AWB converged but NULL color correction values, gains:%b, ccm:%b",
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS) == null,
                                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) == null));
                    }
                }

                if (mIssuedRequest3A) {
                    mIssuedRequest3A = false;
                    mInterlock3A.open();
                } else {
                    int count = mCountCapRes.getAndIncrement();
                    mCaptureResults[count] = result;
                    mSocketRunnableObj.sendResponseCaptureResult(mCameraCharacteristics,
                            request, result, mCaptureReaders);
                    mCaptureCallbackLatch.countDown();
                }
            } catch (ItsException e) {
                Log.e(TAG, "Script error: ", e);
                mSocketThreadExitFlag = true;
            } catch (Exception e) {
                Log.e(TAG, "Script error: ", e);
                mSocketThreadExitFlag = true;
            }
        }

        @Override
        public void onCaptureFailed(CameraDevice camera, CaptureRequest request,
                CaptureFailure failure) {
            mCaptureCallbackLatch.countDown();
            Log.e(TAG, "Script error: capture failed");
        }
    };
}
