/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.app.usage.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppIdleHostTest extends DeviceTestCase {
    private static final String SETTINGS_APP_IDLE_CONSTANTS = "app_idle_constants";

    private static final String TEST_APP_PACKAGE = "android.app.usage.app";
    private static final String TEST_APP_CLASS = "TestActivity";

    private static final long ACTIVITY_LAUNCH_WAIT_MILLIS = 500;

    private static final int SB_ACTIVE = 10;
    private static final int SB_WORKING_SET = 20;
    private static final int SB_FREQUENT = 30;
    private static final int SB_RARE = 40;
    private static final int SB_NEVER = 50;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Get the device, this gives a handle to run commands and install APKs.
        mDevice = getDevice();
    }

    /**
     * Checks whether an package is idle.
     * @param appPackage The package to check for idleness.
     * @return true if the package is idle
     * @throws DeviceNotAvailableException
     */
    private boolean isAppIdle(String appPackage) throws DeviceNotAvailableException {
        String result = mDevice.executeShellCommand(String.format("am get-inactive %s", appPackage));
        return result.contains("Idle=true");
    }

    /**
     * Set the app idle settings.
     * @param settingsStr The settings string, a comma separated key=value list.
     * @throws DeviceNotAvailableException
     */
    private void setAppIdleSettings(String settingsStr) throws DeviceNotAvailableException {
        mDevice.executeShellCommand(String.format("settings put global %s \"%s\"",
                SETTINGS_APP_IDLE_CONSTANTS, settingsStr));
    }

    /**
     * Get the current app idle settings.
     * @throws DeviceNotAvailableException
     */
    private String getAppIdleSettings() throws DeviceNotAvailableException {
        String result = mDevice.executeShellCommand(String.format("settings get global %s",
                SETTINGS_APP_IDLE_CONSTANTS));
        return result.trim();
    }

    /**
     * Launch the test app for a few hundred milliseconds then launch home.
     * @throws DeviceNotAvailableException
     */
    private void startAndStopTestApp() throws DeviceNotAvailableException {
        // Launch the app.
        mDevice.executeShellCommand(
                String.format("am start -W -a android.intent.action.MAIN -n %s/%s.%s",
                        TEST_APP_PACKAGE, TEST_APP_PACKAGE, TEST_APP_CLASS));

        // Wait for some time.
        sleepUninterrupted(ACTIVITY_LAUNCH_WAIT_MILLIS);

        // Launch home.
        mDevice.executeShellCommand(
                "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME");
    }

    /**
     * Tests that the app is not idle right after it is launched.
     */
    public void testAppIsNotIdleAfterBeingLaunched() throws Exception {
        final String previousState = getAppIdleSettings();
        try {
            // Set the app idle time to something large.
            setAppIdleSettings("idle_duration=10000,wallclock_threshold=10000");
            startAndStopTestApp();
            assertFalse(isAppIdle(TEST_APP_PACKAGE));
        } finally {
            setAppIdleSettings(previousState);
        }
    }

    private void setAppStandbyBucket(String packageName, int bucket) throws Exception {
        mDevice.executeShellCommand(
                String.format("am set-standby-bucket %s %s", packageName, bucket));
    }

    private int getAppStandbyBucket(String packageName) throws Exception {
        String bucketString = mDevice.executeShellCommand(
                String.format("am get-standby-bucket %s", packageName));
        System.err.println(bucketString);
        try {
            return Integer.parseInt(bucketString.trim());
        } catch (NumberFormatException nfe) {
        }
        return -1;
    }

    public void testSetAppStandbyBucket() throws Exception {
        // Set to ACTIVE
        setAppStandbyBucket(TEST_APP_PACKAGE, SB_ACTIVE);
        assertEquals(SB_ACTIVE, getAppStandbyBucket(TEST_APP_PACKAGE));
        // set to WORKING_SET
        setAppStandbyBucket(TEST_APP_PACKAGE, 20);
        assertEquals(20, getAppStandbyBucket(TEST_APP_PACKAGE));
    }

    public void testCantSetOwnStandbyBucket() throws Exception {
        setAppStandbyBucket("com.android.shell", 40);
        assertNotEquals(40, getAppStandbyBucket("com.android.shell"));
    }

    public void testOutOfBoundsStandbyBucket() throws Exception {
        setAppStandbyBucket(TEST_APP_PACKAGE, SB_ACTIVE);
        assertEquals(SB_ACTIVE, getAppStandbyBucket(TEST_APP_PACKAGE));
        // Try lower than min
        setAppStandbyBucket(TEST_APP_PACKAGE, SB_ACTIVE - 1);
        assertEquals(SB_ACTIVE, getAppStandbyBucket(TEST_APP_PACKAGE));
        // Try higher than max
        setAppStandbyBucket(TEST_APP_PACKAGE, 50 + 1);
        assertEquals(SB_ACTIVE, getAppStandbyBucket(TEST_APP_PACKAGE));
    }

    private static void sleepUninterrupted(long timeMillis) {
        boolean interrupted;
        do {
            try {
                Thread.sleep(timeMillis);
                interrupted = false;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        } while (interrupted);
    }
}
