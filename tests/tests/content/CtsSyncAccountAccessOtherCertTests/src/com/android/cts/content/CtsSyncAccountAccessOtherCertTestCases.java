/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.content;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncRequest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Tests whether a sync adapter can access accounts.
 */
@RunWith(AndroidJUnit4.class)
public class CtsSyncAccountAccessOtherCertTestCases {
    private static final long SYNC_TIMEOUT_MILLIS = 20000; // 20 sec
    private static final long UI_TIMEOUT_MILLIS = 5000; // 5 sec
    private static final String LOG_TAG =
            CtsSyncAccountAccessOtherCertTestCases.class.getSimpleName();

    private static final Pattern PERMISSION_REQUESTED = Pattern.compile(
            "Permission Requested|Permission requested");
    private static final Pattern ALLOW_SYNC = Pattern.compile("ALLOW|Allow");
    public static final String TOKEN_TYPE_REMOVE_ACCOUNTS = "TOKEN_TYPE_REMOVE_ACCOUNTS";

    @Rule
    public final TestRule mFlakyTestRule = new FlakyTestRule(3);

    @Before
    public void setUp() throws Exception {
        allowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @After
    public void tearDown() throws Exception {
        disallowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @Test
    public void testAccountAccess_otherCertAsAuthenticatorCanNotSeeAccount() throws Exception {
        assumeTrue(hasDataConnection());
        assumeTrue(hasNotificationSupport());

        // If running in a test harness the Account Manager never denies access to an account. Hence
        // the permission request will not trigger. b/72114924
        assumeFalse(ActivityManager.isRunningInTestHarness());

        Intent intent = new Intent(getContext(), StubActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        AccountManager accountManager = getContext().getSystemService(AccountManager.class);
        Bundle result = accountManager.addAccount("com.stub", null, null, null, activity,
                null, null).getResult();

        Account addedAccount = new Account(
                result.getString(AccountManager.KEY_ACCOUNT_NAME),
                result.getString(AccountManager.KEY_ACCOUNT_TYPE));

        waitForSyncManagerAccountChangeUpdate();

        try {
            AbstractThreadedSyncAdapter adapter = SyncAdapter.setNewDelegate();

            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_PRIORITY, true);
            extras.getBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
            SyncRequest request = new SyncRequest.Builder()
                    .setSyncAdapter(null, "com.android.cts.stub.provider")
                    .syncOnce()
                    .setExtras(extras)
                    .setExpedited(true)
                    .setManual(true)
                    .build();
            ContentResolver.requestSync(request);

            verify(adapter, timeout(SYNC_TIMEOUT_MILLIS).times(0)).onPerformSync(any(), any(),
                    any(), any(), any());

            UiDevice uiDevice = getUiDevice();
            if (isWatch()) {
                UiObject2 notification = findPermissionNotificationInStream(uiDevice);
                notification.click();
            } else {
                uiDevice.openNotification();
                try {
                    UiObject2 permissionRequest = uiDevice.wait(
                            Until.findObject(By.text(PERMISSION_REQUESTED)), UI_TIMEOUT_MILLIS);

                    permissionRequest.click();
                } catch (Throwable t) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    getUiDevice().dumpWindowHierarchy(os);

                    Log.w(LOG_TAG, "Window hierarchy:");
                    for (String line : os.toString("UTF-8").split("\n")) {
                        Log.w(LOG_TAG, line);

                        // Do not overwhelm logging
                        Thread.sleep(10);
                    }

                    throw  t;
                }
            }

            uiDevice.wait(Until.findObject(By.text(ALLOW_SYNC)), UI_TIMEOUT_MILLIS).click();

            ContentResolver.requestSync(request);

            verify(adapter, timeout(SYNC_TIMEOUT_MILLIS)).onPerformSync(any(), any(), any(), any(),
                    any());
        } finally {
            // Ask the differently signed authenticator to drop all accounts
            accountManager.getAuthToken(addedAccount, TOKEN_TYPE_REMOVE_ACCOUNTS,
                    null, false, null, null);
            activity.finish();
        }
    }

    private UiObject2 findPermissionNotificationInStream(UiDevice uiDevice) {
        uiDevice.pressHome();
        swipeUp(uiDevice);
        if (uiDevice.hasObject(By.text(PERMISSION_REQUESTED))) {
          return uiDevice.findObject(By.text(PERMISSION_REQUESTED));
        }
        for (int i = 0; i < 100; i++) {
          if (!swipeUp(uiDevice)) {
            // We have reached the end of the stream and not found the target.
            break;
          }
          if (uiDevice.hasObject(By.text(PERMISSION_REQUESTED))) {
            return uiDevice.findObject(By.text(PERMISSION_REQUESTED));
          }
        }
        return null;
    }

    private boolean swipeUp(UiDevice uiDevice) {
        int width = uiDevice.getDisplayWidth();
        int height = uiDevice.getDisplayHeight();
        return uiDevice.swipe(
            width / 2 /* startX */,
            height - 1 /* startY */,
            width / 2 /* endX */,
            1 /* endY */,
            50 /* numberOfSteps */);
    }

    private boolean isWatch() {
        return (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH;
    }

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private UiDevice getUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private void waitForSyncManagerAccountChangeUpdate() {
        // Wait for the sync manager to be notified for the new account.
        // Unfortunately, there is no way to detect this event, sigh...
        SystemClock.sleep(SYNC_TIMEOUT_MILLIS);
    }

    private boolean hasDataConnection() {
        ConnectivityManager connectivityManager = getContext().getSystemService(
                ConnectivityManager.class);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private boolean hasNotificationSupport() {
        final PackageManager manager = getContext().getPackageManager();
        return !manager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                && !manager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED);
    }

    private void allowSyncAdapterRunInBackgroundAndDataInBackground() throws IOException {
        // Allow us to run in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist +" + getContext().getPackageName());
        // Allow us to use data in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy add restrict-background-whitelist " + Process.myUid());
    }

    private void disallowSyncAdapterRunInBackgroundAndDataInBackground() throws IOException {
        // Allow us to run in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist -" + getContext().getPackageName());
        // Allow us to use data in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy remove restrict-background-whitelist " + Process.myUid());
    }
}
