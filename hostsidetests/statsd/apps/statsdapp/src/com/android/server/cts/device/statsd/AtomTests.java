/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts.device.statsd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.support.test.InstrumentationRegistry;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AtomTests {
    private static final String TAG = AtomTests.class.getSimpleName();

    @Test
    public void testBleScanOptimized() {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC).build();
        performBleScan(scanSettings, false);
    }

    @Test
    public void testBleScanUnoptimized() {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        performBleScan(scanSettings, false);
    }

    @Test
    public void testBleScanResult() {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        // TODO: Add an extremely weak ScanFilter to allow background ble scan results.
        performBleScan(scanSettings, true);
    }

    private static void performBleScan(ScanSettings scanSettings, boolean waitForResult) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device does not support Bluetooth");
            return;
        }
        boolean bluetoothEnabledByTest = false;
        if (!bluetoothAdapter.isEnabled()) {
            if (!bluetoothAdapter.enable()) {
                Log.e(TAG, "Bluetooth is not enabled");
                return;
            }
            sleep(8_000);
            bluetoothEnabledByTest = true;
        }
        BluetoothLeScanner bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Log.e(TAG, "Cannot access BLE scanner");
            return;
        }

        CountDownLatch resultsLatch = new CountDownLatch(1);
        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.v(TAG, "called onScanResult");
                resultsLatch.countDown();
            }
            @Override
            public void onScanFailed(int errorCode) {
                Log.v(TAG, "called onScanFailed");
            }
            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                Log.v(TAG, "called onBatchScanResults");
                resultsLatch.countDown();
            }
        };

        bleScanner.startScan(null, scanSettings, scanCallback);
        if (waitForResult) {
            waitForReceiver(InstrumentationRegistry.getContext(), 59_000, resultsLatch, null);
        } else {
            sleep(2_000);
        }
        bleScanner.stopScan(scanCallback);

        // Restore adapter state at end of test
        if (bluetoothEnabledByTest) {
            bluetoothAdapter.disable();
        }
    }

    @Test
    public void testGpsScan() {
        Context context = InstrumentationRegistry.getContext();
        final LocationManager locManager = context.getSystemService(LocationManager.class);
        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.e(TAG, "GPS provider is not enabled");
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);

        final LocationListener locListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.v(TAG, "onLocationChanged: location has been obtained");
            }
            public void onProviderDisabled(String provider) {
                Log.w(TAG, "onProviderDisabled " + provider);
            }
            public void onProviderEnabled(String provider) {
                Log.w(TAG, "onProviderEnabled " + provider);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.w(TAG, "onStatusChanged " + provider + " " + status);
            }
        };

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Looper.prepare();
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 990, 0,
                        locListener);
                sleep(1_000);
                locManager.removeUpdates(locListener);
                latch.countDown();
                return null;
            }
        }.execute();

        waitForReceiver(context, 59_000, latch, null);
    }

    @Test
    /** Does two wifi scans. */
    // TODO: Copied this from BatterystatsValidation but we probably don't need to wait for results.
    public void testWifiScan() {
        Context context = InstrumentationRegistry.getContext();
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        // Sometimes a scan was already running (from a different uid), so the first scan doesn't
        // start when requested. Therefore, additionally wait for whatever scan is currently running
        // to finish, then request a scan again - at least one of these two scans should be
        // attributed to this app.
        for (int i = 0; i < 2; i++) {
            CountDownLatch onReceiveLatch = new CountDownLatch(1);
            BroadcastReceiver receiver = registerReceiver(context, onReceiveLatch, intentFilter);
            context.getSystemService(WifiManager.class).startScan();
            waitForReceiver(context, 60_000, onReceiveLatch, receiver);
        }
    }

    // ------- Helper methods

    /** Puts the current thread to sleep. */
    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while sleeping", e);
        }
    }

    /** Register receiver to determine when given action is complete. */
    private static BroadcastReceiver registerReceiver(
            Context ctx, CountDownLatch onReceiveLatch, IntentFilter intentFilter) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveLatch.countDown();
            }
        };
        // Run Broadcast receiver in a different thread since the main thread will wait.
        HandlerThread handlerThread = new HandlerThread("br_handler_thread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);
        ctx.registerReceiver(receiver, intentFilter, null, handler);
        return receiver;
    }

    /**
     * Uses the receiver to wait until the action is complete. ctx and receiver may be null if no
     * receiver is needed to be unregistered.
     */
    private static void waitForReceiver(Context ctx,
            int maxWaitTimeMs, CountDownLatch latch, BroadcastReceiver receiver) {
        try {
            boolean didFinish = latch.await(maxWaitTimeMs, TimeUnit.MILLISECONDS);
            if (didFinish) {
                Log.v(TAG, "Finished performing action");
            } else {
                // This is not necessarily a problem. If we just want to make sure a count was
                // recorded for the request, it doesn't matter if the action actually finished.
                Log.w(TAG, "Did not finish in specified time.");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while awaiting action to finish", e);
        }
        if (ctx != null && receiver != null) {
            ctx.unregisterReceiver(receiver);
        }
    }
}