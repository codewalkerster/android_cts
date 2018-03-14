/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.wifi.rtt.cts;

import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.WifiRttManager;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wi-Fi RTT CTS test: range to all available Access Points which support IEEE 802.11mc.
 */
public class WifiRttTest extends TestBase {
    // Max number of scan retries to do while searching for APs supporting IEEE 802.11mc
    private static final int MAX_NUM_SCAN_RETRIES_SEARCHING_FOR_IEEE80211MC_AP = 2;

    // Number of RTT measurements per AP
    private static final int NUM_OF_RTT_ITERATIONS = 10;

    // Maximum failure rate of RTT measurements (percentage)
    private static final int MAX_FAILURE_RATE_PERCENT = 10;

    // Maximum variation from the average measurement (measures consistency)
    private static final int MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM = 1000;

    // Minimum valid RSSI value
    private static final int MIN_VALID_RSSI = -100;

    /**
     * Test Wi-Fi RTT ranging operation:
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11mc)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    public void testRangingToTestAp() throws InterruptedException {
        if (!shouldTestWifiRtt(getContext())) {
            return;
        }

        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = scanForTestAp(SSID_OF_TEST_AP,
                MAX_NUM_SCAN_RETRIES_SEARCHING_FOR_IEEE80211MC_AP);
        assertTrue("Cannot find test AP", testAp != null);

        // Perform RTT operations
        RangingRequest request = new RangingRequest.Builder().addAccessPoint(testAp).build();
        List<RangingResult> allResults = new ArrayList<>();
        int numFailures = 0;
        int distanceSum = 0;
        int distanceMin = 0;
        int distanceMax = 0;
        int[] statuses = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceStdDevMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] rssis = new int[NUM_OF_RTT_ITERATIONS];
        for (int i = 0; i < NUM_OF_RTT_ITERATIONS; ++i) {
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback on iteration " + i,
                    callback.waitForCallback());

            List<RangingResult> currentResults = callback.getResults();
            assertTrue("Wi-Fi RTT results: null results (onRangingFailure) on iteration " + i,
                    currentResults != null);
            assertTrue("Wi-Fi RTT results: unexpected # of results (expect 1) on iteration " + i,
                    currentResults.size() == 1);
            RangingResult result = currentResults.get(0);
            assertTrue("Wi-Fi RTT results: invalid result (wrong BSSID) entry on iteration " + i,
                    result.getMacAddress().toString().equals(testAp.BSSID));

            allResults.add(result);
            int status = result.getStatus();
            statuses[i] = status;
            if (status == RangingResult.STATUS_SUCCESS) {
                distanceSum += result.getDistanceMm();
                if (i == 0) {
                    distanceMin = result.getDistanceMm();
                    distanceMax = result.getDistanceMm();
                } else {
                    distanceMin = Math.min(distanceMin, result.getDistanceMm());
                    distanceMax = Math.max(distanceMax, result.getDistanceMm());
                }

                assertTrue("Wi-Fi RTT results: invalid RSSI on iteration " + i,
                        result.getRssi() >= MIN_VALID_RSSI);

                distanceMms[i - numFailures] = result.getDistanceMm();
                distanceStdDevMms[i - numFailures] = result.getDistanceStdDevMm();
                rssis[i - numFailures] = result.getRssi();
            } else {
                numFailures++;
            }
        }

        // Save results to log
        int numGoodResults = NUM_OF_RTT_ITERATIONS - numFailures;
        DeviceReportLog reportLog = new DeviceReportLog(TAG, "testRangingToTestAp");
        reportLog.addValues("status_codes", statuses, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_mm", Arrays.copyOf(distanceMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_stddev_mm", Arrays.copyOf(distanceStdDevMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("rssi_dbm", Arrays.copyOf(rssis, numGoodResults), ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.submit();

        // Analyze results
        assertTrue("Wi-Fi RTT failure rate exceeds threshold",
                numFailures <= NUM_OF_RTT_ITERATIONS * MAX_FAILURE_RATE_PERCENT / 100);
        if (numFailures != NUM_OF_RTT_ITERATIONS) {
            double distanceAvg = distanceSum / (NUM_OF_RTT_ITERATIONS - numFailures);
            assertTrue("Wi-Fi RTT: Variation (max direction) exceeds threshold",
                    (distanceMax - distanceAvg) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
            assertTrue("Wi-Fi RTT: Variation (min direction) exceeds threshold",
                    (distanceAvg - distanceMin) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
        }
    }

    /**
     * Validate that on Wi-Fi RTT availability change we get a broadcast + the API returns
     * correct status.
     */
    public void testAvailabilityStatusChange() throws Exception {
        if (!shouldTestWifiRtt(getContext())) {
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED);

        // 1. Disable Wi-Fi
        WifiRttBroadcastReceiver receiver1 = new WifiRttBroadcastReceiver();
        mContext.registerReceiver(receiver1, intentFilter);
        mWifiManager.setWifiEnabled(false);

        assertTrue("Timeout waiting for Wi-Fi RTT to change status",
                receiver1.waitForStateChange());
        assertFalse("Wi-Fi RTT is available (should not be)", mWifiRttManager.isAvailable());

        // 2. Enable Wi-Fi
        WifiRttBroadcastReceiver receiver2 = new WifiRttBroadcastReceiver();
        mContext.registerReceiver(receiver2, intentFilter);
        mWifiManager.setWifiEnabled(true);

        assertTrue("Timeout waiting for Wi-Fi RTT to change status",
                receiver2.waitForStateChange());
        assertTrue("Wi-Fi RTT is not available (should be)", mWifiRttManager.isAvailable());
    }

    /**
     * Validate that when a request contains more range operations than allowed (by API) that we
     * get an exception.
     */
    public void testRequestTooLarge() {
        if (!shouldTestWifiRtt(getContext())) {
            return;
        }

        RangingRequest.Builder builder = new RangingRequest.Builder();
        for (int i = 0; i < RangingRequest.getMaxPeers() + 1; ++i) {
            ScanResult dummy = new ScanResult();
            dummy.BSSID = "00:01:02:03:04:05";
            builder.addAccessPoint(dummy);
        }

        try {
            mWifiRttManager.startRanging(builder.build(), mExecutor, new ResultCallback());
        } catch (IllegalArgumentException e) {
            return;
        }

        assertTrue(
                "Did not receive expected IllegalArgumentException when tried to range to too "
                        + "many peers",
                false);
    }
}