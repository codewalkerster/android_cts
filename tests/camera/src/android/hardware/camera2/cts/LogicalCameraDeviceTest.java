/*
 * Copyright 2018 The Android Open Source Project
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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.util.ArraySet;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;


import com.android.compatibility.common.util.Stat;
import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Tests exercising logical camera setup, configuration, and usage.
 */
public final class LogicalCameraDeviceTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "LogicalCameraTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int CONFIGURE_TIMEOUT = 5000; //ms

    private static final double NS_PER_MS = 1000000.0;
    private static final int MAX_IMAGE_COUNT = 3;
    private static final int NUM_FRAMES_CHECKED = 30;

    //TODO: Tighten threshold once HAL implementation is fixed.
    //b/71427920
    private static final double FRAME_DURATION_THRESHOLD = 2.0;

    /**
     * Test that passing in invalid physical camera ids in OutputConfiguragtion behaves as expected
     * for logical multi-camera and non-logical multi-camera.
     */
    public void testInvalidPhysicalCameraIdInOutputConfiguration() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                Size yuvSize = mOrderedPreviewSizes.get(0);
                // Create a YUV image reader.
                ImageReader imageReader = ImageReader.newInstance(yuvSize.getWidth(),
                        yuvSize.getHeight(), ImageFormat.YUV_420_888, /*maxImages*/1);

                CameraCaptureSession.StateCallback sessionListener =
                        mock(CameraCaptureSession.StateCallback.class);
                List<OutputConfiguration> outputs = new ArrayList<>();
                OutputConfiguration outputConfig = new OutputConfiguration(
                        imageReader.getSurface());
                outputConfig.setPhysicalCameraId(id);

                // Regardless of logical camera or non-logical camera, create a session of an
                // output configuration with invalid physical camera id, verify that the
                // createCaptureSession fails.
                outputs.add(outputConfig);
                CameraCaptureSession session =
                        CameraTestUtils.configureCameraSessionWithConfig(mCamera, outputs,
                                sessionListener, mHandler);
                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce()).
                        onConfigureFailed(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onConfigured(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onReady(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onActive(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onClosed(any(CameraCaptureSession.class));
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test for making sure that streaming from physical streams work as expected, and
     * FPS isn't slowed down.
     */
    public void testBasicPhysicalStreaming() throws Exception {

        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                if (!mStaticInfo.isLogicalMultiCamera()) {
                    Log.i(TAG, "Camera " + id + " is not a logical multi-camera, skipping");
                    continue;
                }

                assertTrue("Logical multi-camera must be LIMITED or higher",
                        mStaticInfo.isHardwareLevelAtLeastLimited());

                // Figure out preview size and physical cameras to use.
                ArrayList<String> dualPhysicalCameraIds = new ArrayList<String>();
                Size previewSize= findCommonPreviewSize(id, dualPhysicalCameraIds);
                if (previewSize == null) {
                    Log.i(TAG, "Camera " + id + ": No matching physical preview streams, skipping");
                }

                testBasicPhysicalStreamingForCamera(
                        id, dualPhysicalCameraIds, previewSize);
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test for making sure that multiple requests for physical cameras work as expected.
     */
    public void testBasicPhysicalRequests() throws Exception {

        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                if (!mStaticInfo.isLogicalMultiCamera()) {
                    Log.i(TAG, "Camera " + id + " is not a logical multi-camera, skipping");
                    continue;
                }

                assertTrue("Logical multi-camera must be LIMITED or higher",
                        mStaticInfo.isHardwareLevelAtLeastLimited());

                // Figure out yuv size and physical cameras to use.
                List<String> dualPhysicalCameraIds = new ArrayList<String>();
                Size yuvSize= findCommonPreviewSize(id, dualPhysicalCameraIds);
                if (yuvSize == null) {
                    Log.i(TAG, "Camera " + id + ": No matching physical YUV streams, skipping");
                    continue;
                }
                ArraySet<String> physicalIdSet = new ArraySet<String>(dualPhysicalCameraIds.size());
                physicalIdSet.addAll(dualPhysicalCameraIds);

                if (VERBOSE) {
                    Log.v(TAG, "Camera " + id + ": Testing YUV size of " + yuvSize.getWidth() +
                        " x " + yuvSize.getHeight());
                }
                List<CaptureRequest.Key<?>> physicalRequestKeys =
                    mStaticInfo.getCharacteristics().getAvailablePhysicalCameraRequestKeys();
                if (physicalRequestKeys == null) {
                    Log.i(TAG, "Camera " + id + ": no available physical request keys, skipping");
                    continue;
                }

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                List<ImageReader> imageReaders = new ArrayList<>();
                SimpleImageReaderListener readerListener = new SimpleImageReaderListener();
                ImageReader yuvTarget = CameraTestUtils.makeImageReader(yuvSize,
                        ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                        readerListener, mHandler);
                imageReaders.add(yuvTarget);
                OutputConfiguration config = new OutputConfiguration(yuvTarget.getSurface());
                outputConfigs.add(new OutputConfiguration(yuvTarget.getSurface()));

                CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW, physicalIdSet);
                requestBuilder.addTarget(config.getSurface());

                mSessionListener = new BlockingSessionCallback();
                mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                        mSessionListener, mHandler);

                for (int i = 0; i < MAX_IMAGE_COUNT; i++) {
                    mSession.capture(requestBuilder.build(), new SimpleCaptureCallback(), mHandler);
                    readerListener.getImage(WAIT_FOR_RESULT_TIMEOUT_MS);
                }

                if (mSession != null) {
                    mSession.close();
                }

            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Tests invalid/incorrect multiple physical capture request cases.
     */
    public void testInvalidPhysicalCameraRequests() throws Exception {

        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                assertTrue("Logical multi-camera must be LIMITED or higher",
                        mStaticInfo.isHardwareLevelAtLeastLimited());

                Size yuvSize = mOrderedPreviewSizes.get(0);
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                List<ImageReader> imageReaders = new ArrayList<>();
                SimpleImageReaderListener readerListener = new SimpleImageReaderListener();
                ImageReader yuvTarget = CameraTestUtils.makeImageReader(yuvSize,
                        ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                        readerListener, mHandler);
                imageReaders.add(yuvTarget);
                OutputConfiguration config = new OutputConfiguration(yuvTarget.getSurface());
                outputConfigs.add(new OutputConfiguration(yuvTarget.getSurface()));

                ArraySet<String> physicalIdSet = new ArraySet<String>();
                // Invalid physical id
                physicalIdSet.add("-2");

                CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW, physicalIdSet);
                requestBuilder.addTarget(config.getSurface());

                // Check for invalid setting get/set
                try {
                    requestBuilder.getPhysicalCameraKey(CaptureRequest.CONTROL_CAPTURE_INTENT, "-1");
                    fail("No exception for invalid physical camera id");
                } catch (IllegalArgumentException e) {
                    //expected
                }

                try {
                    requestBuilder.setPhysicalCameraKey(CaptureRequest.CONTROL_CAPTURE_INTENT,
                            new Integer(0), "-1");
                    fail("No exception for invalid physical camera id");
                } catch (IllegalArgumentException e) {
                    //expected
                }

                mSessionListener = new BlockingSessionCallback();
                mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                        mSessionListener, mHandler);

                try {
                    mSession.capture(requestBuilder.build(), new SimpleCaptureCallback(),
                            mHandler);
                    fail("No exception for invalid physical camera id");
                } catch (IllegalArgumentException e) {
                    //expected
                }

                if (mStaticInfo.isLogicalMultiCamera()) {
                    // Figure out yuv size to use.
                    List<String> dualPhysicalCameraIds = new ArrayList<String>();
                    Size sharedSize= findCommonPreviewSize(id, dualPhysicalCameraIds);
                    if (sharedSize == null) {
                        Log.i(TAG, "Camera " + id + ": No matching physical YUV streams, skipping");
                        continue;
                    }

                    physicalIdSet.clear();
                    physicalIdSet.addAll(dualPhysicalCameraIds);
                    requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW, physicalIdSet);
                    requestBuilder.addTarget(config.getSurface());
                    CaptureRequest request = requestBuilder.build();

                    // Streaming requests with individual physical camera settings are not
                    // supported.
                    try {
                        mSession.setRepeatingRequest(request, new SimpleCaptureCallback(),
                                mHandler);
                        fail("Streaming requests that include physical camera settings are " +
                                "supported");
                    } catch (IllegalArgumentException e) {
                        //expected
                    }

                    try {
                        ArrayList<CaptureRequest> requestList = new ArrayList<CaptureRequest>();
                        requestList.add(request);
                        mSession.setRepeatingBurst(requestList, new SimpleCaptureCallback(),
                                mHandler);
                        fail("Streaming requests that include physical camera settings are " +
                                "supported");
                    } catch (IllegalArgumentException e) {
                        //expected
                    }
                }

                if (mSession != null) {
                    mSession.close();
                }
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Find a common preview size that's supported by both the logical camera and
     * two of the underlying physical cameras.
     */
    private Size findCommonPreviewSize(String cameraId,
            List<String> dualPhysicalCameraIds) throws Exception {

        List<String> physicalCameraIds =
                mStaticInfo.getCharacteristics().getPhysicalCameraIds();
        assertTrue("Logical camera must contain at least 2 physical camera ids",
                physicalCameraIds.size() >= 2);

        List<Size> previewSizes = getSupportedPreviewSizes(
                cameraId, mCameraManager, PREVIEW_SIZE_BOUND);
        HashMap<String, List<Size>> physicalPreviewSizesMap = new HashMap<String, List<Size>>();
        HashMap<String, StreamConfigurationMap> physicalConfigs = new HashMap<>();
        for (String physicalCameraId : physicalCameraIds) {
            CameraCharacteristics properties =
                    mCameraManager.getCameraCharacteristics(physicalCameraId);
            assertNotNull("Can't get camera characteristics!", properties);
            StreamConfigurationMap configMap =
                properties.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            physicalConfigs.put(physicalCameraId, configMap);
            physicalPreviewSizesMap.put(physicalCameraId,
                    getSupportedPreviewSizes(physicalCameraId, mCameraManager, PREVIEW_SIZE_BOUND));
        }

        // Find display size from window service.
        Context context = getInstrumentation().getTargetContext();
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();

        int displayWidth = display.getWidth();
        int displayHeight = display.getHeight();

        if (displayHeight > displayWidth) {
            displayHeight = displayWidth;
            displayWidth = display.getHeight();
        }

        StreamConfigurationMap config = mStaticInfo.getCharacteristics().get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        for (Size previewSize : previewSizes) {
            dualPhysicalCameraIds.clear();
            // Skip preview sizes larger than screen size
            if (previewSize.getWidth() > displayWidth ||
                    previewSize.getHeight() > displayHeight) {
                continue;
            }

            final long minFrameDuration = config.getOutputMinFrameDuration(
                   ImageFormat.YUV_420_888, previewSize);

            ArrayList<String> supportedPhysicalCameras = new ArrayList<String>();
            for (String physicalCameraId : physicalCameraIds) {
                List<Size> physicalPreviewSizes = physicalPreviewSizesMap.get(physicalCameraId);
                if (physicalPreviewSizes.contains(previewSize)) {
                   long minDurationPhysical =
                           physicalConfigs.get(physicalCameraId).getOutputMinFrameDuration(
                           ImageFormat.YUV_420_888, previewSize);
                   if (minDurationPhysical <= minFrameDuration) {
                        dualPhysicalCameraIds.add(physicalCameraId);
                        if (dualPhysicalCameraIds.size() == 2) {
                            return previewSize;
                        }
                   }
                }
            }
        }
        return null;
    }

    /**
     * Test physical camera YUV streaming within a particular logical camera.
     *
     * Use 2 YUV streams with PREVIEW or smaller size, which is guaranteed for LIMITED device level.
     */
    private void testBasicPhysicalStreamingForCamera(String logicalCameraId,
            List<String> physicalCameraIds, Size previewSize) throws Exception {
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        List<ImageReader> imageReaders = new ArrayList<>();

        // Add 1 logical YUV stream
        ImageReader logicalTarget = CameraTestUtils.makeImageReader(previewSize,
                ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                new ImageDropperListener(), mHandler);
        imageReaders.add(logicalTarget);
        outputConfigs.add(new OutputConfiguration(logicalTarget.getSurface()));

        // Add physical YUV streams
        List<ImageReader> physicalTargets = new ArrayList<>();
        for (String physicalCameraId : physicalCameraIds) {
            ImageReader physicalTarget = CameraTestUtils.makeImageReader(previewSize,
                    ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                    new ImageDropperListener(), mHandler);
            OutputConfiguration config = new OutputConfiguration(physicalTarget.getSurface());
            config.setPhysicalCameraId(physicalCameraId);
            outputConfigs.add(config);
            physicalTargets.add(physicalTarget);
        }

        mSessionListener = new BlockingSessionCallback();
        mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                mSessionListener, mHandler);

        // Stream logical YUV stream and note down the FPS
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        requestBuilder.addTarget(logicalTarget.getSurface());

        SimpleCaptureCallback simpleResultListener =
                new SimpleCaptureCallback();
        StreamConfigurationMap config = mStaticInfo.getCharacteristics().get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final long minFrameDuration = config.getOutputMinFrameDuration(
                ImageFormat.YUV_420_888, previewSize);
        if (minFrameDuration > 0) {
            Range<Integer> targetRange = getSuitableFpsRangeForDuration(logicalCameraId,
                    minFrameDuration);
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetRange);
        }
        mSession.setRepeatingRequest(requestBuilder.build(),
                simpleResultListener, mHandler);

        // Converge AE
        waitForAeStable(simpleResultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

        if (mStaticInfo.isAeLockSupported()) {
            // Lock AE if supported.
            requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            mSession.setRepeatingRequest(requestBuilder.build(), simpleResultListener,
                    mHandler);
            waitForResultValue(simpleResultListener, CaptureResult.CONTROL_AE_STATE,
                    CaptureResult.CONTROL_AE_STATE_LOCKED, NUM_RESULTS_WAIT_TIMEOUT);
        }

        // Collect timestamps for one logical stream only.
        long prevTimestamp = -1;
        long[] logicalTimestamps = new long[NUM_FRAMES_CHECKED];
        for (int i = 0; i < NUM_FRAMES_CHECKED; i++) {
            TotalCaptureResult totalCaptureResult =
                    simpleResultListener.getTotalCaptureResult(
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
            logicalTimestamps[i] = totalCaptureResult.get(CaptureResult.SENSOR_TIMESTAMP);
        }
        // Make sure that requesting a physical camera key for a logical stream request
        // throws exception.
        try {
            TotalCaptureResult totalCaptureResult =
                    simpleResultListener.getTotalCaptureResult(
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
            long timestamp = totalCaptureResult.getPhysicalCameraKey(
                    CaptureResult.SENSOR_TIMESTAMP, physicalCameraIds.get(0));
            fail("No exception for invalid physical camera Id for TotalCaptureResult");
        } catch (IllegalArgumentException e) {
           // expected
        }

        double logicalAvgDurationMs = (logicalTimestamps[NUM_FRAMES_CHECKED-1] -
                logicalTimestamps[0])/(NS_PER_MS*(NUM_FRAMES_CHECKED-1));

        // Start requesting on both logical and physical streams
        SimpleCaptureCallback simpleResultListenerDual =
                new SimpleCaptureCallback();
        for (ImageReader physicalTarget : physicalTargets) {
            requestBuilder.addTarget(physicalTarget.getSurface());
        }
        mSession.setRepeatingRequest(requestBuilder.build(), simpleResultListenerDual,
                mHandler);

        long[] logicalTimestamps2 = new long[NUM_FRAMES_CHECKED];
        long [][] physicalTimestamps = new long[physicalTargets.size()][];
        for (int i = 0; i < physicalTargets.size(); i++) {
            physicalTimestamps[i] = new long[NUM_FRAMES_CHECKED];
        }
        for (int i = 0; i < NUM_FRAMES_CHECKED; i++) {
            TotalCaptureResult totalCaptureResult =
                    simpleResultListenerDual.getTotalCaptureResult(
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
            logicalTimestamps2[i] = totalCaptureResult.get(CaptureResult.SENSOR_TIMESTAMP);

            int index = 0;
            for (String physicalId : physicalCameraIds) {
                 physicalTimestamps[index][i] = totalCaptureResult.getPhysicalCameraKey(
                         CaptureResult.SENSOR_TIMESTAMP, physicalId);
                 index++;
            }
        }
        // Make sure that requesting an invalid physical camera key throws exception.
        try {
            String invalidStringId = "InvalidCamera";
            TotalCaptureResult totalCaptureResult =
                    simpleResultListener.getTotalCaptureResult(
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
           long timestamp = totalCaptureResult.getPhysicalCameraKey(
                    CaptureResult.SENSOR_TIMESTAMP, invalidStringId);
           fail("No exception for invalid physical camera Id for TotalCaptureResult");
        } catch (IllegalArgumentException e) {
           // expected
        }

        // Check timestamp monolithity for individual camera and across cameras
        for (int i = 0; i < NUM_FRAMES_CHECKED-1; i++) {
            assertTrue("Logical camera timestamp must monolithically increase",
                    logicalTimestamps2[i] < logicalTimestamps2[i+1]);
        }
        for (int i = 0; i < physicalCameraIds.size(); i++) {
            for (int j = 0 ; j < NUM_FRAMES_CHECKED-1; j++) {
                assertTrue("Physical camera timestamp must monolithically increase",
                        physicalTimestamps[i][j] < physicalTimestamps[i][j+1]);
                if (j > 0) {
                    assertTrue("Physical camera's timestamp N must be greater than logical " +
                            "camera's timestamp N-1",
                            physicalTimestamps[i][j] > logicalTimestamps[j-1]);
                }
                assertTrue("Physical camera's timestamp N must be less than logical camera's " +
                        "timestamp N+1", physicalTimestamps[i][j] > logicalTimestamps[j+1]);
            }
        }
        double logicalAvgDurationMs2 = (logicalTimestamps2[NUM_FRAMES_CHECKED-1] -
                logicalTimestamps2[0])/(NS_PER_MS*(NUM_FRAMES_CHECKED-1));

        mCollector.expectLessOrEqual("The average frame duration increase of all physical "
                + "streams is larger than threshold: "
                + String.format("increase = %.2f, threshold = %.2f",
                  (logicalAvgDurationMs2 - logicalAvgDurationMs)/logicalAvgDurationMs,
                  FRAME_DURATION_THRESHOLD),
                logicalAvgDurationMs*(1+FRAME_DURATION_THRESHOLD),
                logicalAvgDurationMs2);

        // Stop preview
        if (mSession != null) {
            mSession.close();
        }
    }
}
