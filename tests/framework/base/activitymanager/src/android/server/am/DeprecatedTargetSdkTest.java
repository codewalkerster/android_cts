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

package android.server.am;

import static org.junit.Assert.assertTrue;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Ensure that compatibility dialog is shown when launching an application
 * targeting a deprecated version of SDK.
 * <p>Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:DeprecatedTargetSdkTest
 */
@RunWith(AndroidJUnit4.class)
public class DeprecatedTargetSdkTest extends ActivityManagerTestBase {
    private static final String AM_START_COMMAND = "am start -n %s/%s.%s";
    private static final String AM_FORCE_STOP = "am force-stop %s";

    private static final int ACTIVITY_TIMEOUT_MILLIS = 1000;
    private static final int WINDOW_TIMEOUT_MILLIS = 1000;

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // Ensure app process is stopped.
        forceStopPackage("android.server.am.deprecatedsdk");
        forceStopPackage("android.server.am");
    }

    @Test
    public void testCompatibilityDialog() throws Exception {
        // Launch target app.
        startActivity("android.server.am.deprecatedsdk", "MainActivity");
        verifyWindowDisplayed("MainActivity", ACTIVITY_TIMEOUT_MILLIS);
        verifyWindowDisplayed("DeprecatedTargetSdkVersionDialog", WINDOW_TIMEOUT_MILLIS);

        // Go back to dismiss the warning dialog.
        executeShellCommand("input keyevent 4");

        // Go back again to formally stop the app. If we just kill the process, it'll attempt to
        // resume rather than starting from scratch (as far as ActivityStack is concerned) and it
        // won't invoke the warning dialog.
        executeShellCommand("input keyevent 4");
    }

    private void forceStopPackage(String packageName) {
        final String forceStopCmd = String.format(AM_FORCE_STOP, packageName);
        executeShellCommand(forceStopCmd);
    }

    private void startActivity(String packageName, String activityName){
        executeShellCommand(getStartCommand(packageName, activityName));
    }

    private String getStartCommand(String packageName, String activityName) {
        return String.format(AM_START_COMMAND, packageName, packageName, activityName);
    }

    private void verifyWindowDisplayed(String windowName, long timeoutMillis) {
        boolean success = false;

        // Verify that compatibility dialog is shown within 1000ms.
        final long timeoutTimeMillis = System.currentTimeMillis() + timeoutMillis;
        while (!success && System.currentTimeMillis() < timeoutTimeMillis) {
            final String output = executeShellCommand("dumpsys window");
            success = output.contains(windowName);
        }

        assertTrue(windowName + " was not displayed", success);
    }
}