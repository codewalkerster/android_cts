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
 * limitations under the License
 */

package android.server.am;

import static android.server.am.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import android.platform.test.annotations.Presubmit;
import android.server.am.ActivityManagerState.ActivityDisplay;
import android.util.Size;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerDisplayTests
 */
public class ActivityManagerDisplayTests extends ActivityManagerDisplayTestBase {
    private static final String TEST_ACTIVITY_NAME = "TestActivity";

    /**
     * Tests that the global configuration is equal to the default display's override configuration.
     */
    @Test
    public void testDefaultDisplayOverrideConfiguration() throws Exception {
        final List<ActivityDisplay> reportedDisplays = getDisplaysStates();
        final ActivityDisplay primaryDisplay = getDisplayState(reportedDisplays,
                DEFAULT_DISPLAY_ID);
        assertEquals("Primary display's configuration should be equal to global configuration.",
                primaryDisplay.mOverrideConfiguration, primaryDisplay.mFullConfiguration);
        assertEquals("Primary display's configuration should be equal to global configuration.",
                primaryDisplay.mOverrideConfiguration, primaryDisplay.mMergedOverrideConfiguration);
    }

    /**
     * Tests that secondary display has override configuration set.
     */
    @Test
    public void testCreateVirtualDisplayWithCustomConfig() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Find the density of created display.
            final int newDensityDpi = newDisplay.mFullConfiguration.densityDpi;
            assertEquals(CUSTOM_DENSITY_DPI, newDensityDpi);
        }
    }

    /**
     * Tests that launch on secondary display is not permitted if device has the feature disabled.
     * Activities requested to be launched on a secondary display in this case should land on the
     * default display.
     */
    @Test
    public void testMultiDisplayDisabled() throws Exception {
        // Only check devices with the feature disabled.
        assumeFalse(supportsMultiDisplay());

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));

            mAmWmState.assertFocusedActivity("Launched activity must be focused",
                    TEST_ACTIVITY_NAME);

            // Check that activity is on the right display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
            final ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be resumed",
                    getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
            assertEquals("Front stack must be on the default display", DEFAULT_DISPLAY_ID,
                    frontStack.mDisplayId);
            mAmWmState.assertFocusedStack("Focus must be on the default display", frontStackId);
        }
    }

    @Test
    public void testCreateMultipleVirtualDisplays() throws Exception {
        final List<ActivityDisplay> originalDs = getDisplaysStates();
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual displays
            virtualDisplaySession.createDisplays(3);
            getDisplayStateAfterChange(originalDs.size() + 3);
        }
        getDisplayStateAfterChange(originalDs.size());
    }

    /**
     * Test that display overrides apply correctly and won't be affected by display changes.
     * This sets overrides to display size and density, initiates a display changed event by locking
     * and unlocking the phone and verifies that overrides are kept.
     */
    @Presubmit
    @Test
    public void testForceDisplayMetrics() throws Exception {
        launchHomeActivity();

        try (final DisplayMetricsSession displayMetricsSession = new DisplayMetricsSession()) {
            // Read initial sizes.
            final ReportedDisplayMetrics originalDisplayMetrics =
                    displayMetricsSession.getInitialDisplayMetrics();

            // Apply new override values that don't match the physical metrics.
            final Size overrideSize = new Size(
                    (int) (originalDisplayMetrics.physicalSize.getWidth() * 1.5),
                    (int) (originalDisplayMetrics.physicalSize.getHeight() * 1.5));
            final Integer overrideDensity = (int) (originalDisplayMetrics.physicalDensity * 1.1);
            displayMetricsSession.overrideDisplayMetrics(overrideSize, overrideDensity);

            // Check if overrides applied correctly.
            ReportedDisplayMetrics displayMetrics = displayMetricsSession.getDisplayMetrics();
            assertEquals(overrideSize, displayMetrics.overrideSize);
            assertEquals(overrideDensity, displayMetrics.overrideDensity);

            // Lock and unlock device. This will cause a DISPLAY_CHANGED event to be triggered and
            // might update the metrics.
            sleepDevice();

            wakeUpAndUnlockDevice();
            mAmWmState.waitForHomeActivityVisible();

            // Check if overrides are still applied.
            displayMetrics = displayMetricsSession.getDisplayMetrics();
            assertEquals(overrideSize, displayMetrics.overrideSize);
            assertEquals(overrideDensity, displayMetrics.overrideDensity);
        }
    }

    private static class DisplayMetricsSession implements AutoCloseable {

        private final ReportedDisplayMetrics mInitialDisplayMetrics;

        DisplayMetricsSession() throws Exception {
            mInitialDisplayMetrics = ReportedDisplayMetrics.getDisplayMetrics();
        }

        ReportedDisplayMetrics getInitialDisplayMetrics() {
            return mInitialDisplayMetrics;
        }

        ReportedDisplayMetrics getDisplayMetrics() throws Exception {
            return ReportedDisplayMetrics.getDisplayMetrics();
        }

        void overrideDisplayMetrics(final Size size, final int density) {
            mInitialDisplayMetrics.setDisplayMetrics(size, density);
        }

        @Override
        public void close() throws Exception {
            mInitialDisplayMetrics.restoreDisplayMetrics();
        }
    }
}
