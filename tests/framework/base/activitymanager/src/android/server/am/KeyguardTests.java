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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.server.am.WindowManagerState.WindowState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:KeyguardTests
 */
public class KeyguardTests extends KeyguardTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(isHandheld());
        assertFalse(isUiModeLockedToVrHeadset());

        // Set screen lock (swipe)
        setLockDisabled(false);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        tearDownLockCredentials();
    }

    @Test
    public void testKeyguardHidesActivity() throws Exception {
        launchActivity("TestActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "TestActivity").build());
        mAmWmState.assertVisibility("TestActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        mAmWmState.assertVisibility("TestActivity", false);
        unlockDevice();
    }

    @Test
    public void testShowWhenLockedActivity() throws Exception {
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertKeyguardShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether dialogs from SHOW_WHEN_LOCKED activities are also visible if Keyguard is
     * showing.
     */
    @Test
    public void testShowWhenLockedActivity_withDialog() throws Exception {
        launchActivity("ShowWhenLockedWithDialogActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("ShowWhenLockedWithDialogActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedWithDialogActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedWithDialogActivity", true);
        assertTrue(mAmWmState.getWmState().allWindowsVisible(
                getWindowName("ShowWhenLockedWithDialogActivity")));
        mAmWmState.assertKeyguardShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether multiple SHOW_WHEN_LOCKED activities are shown if the topmost is translucent.
     */
    @Test
    public void testMultipleShowWhenLockedActivities() throws Exception {
        launchActivity("ShowWhenLockedActivity");
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("ShowWhenLockedActivity").build(),
                new WaitForValidActivityState.Builder("ShowWhenLockedTranslucentActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        mAmWmState.assertKeyguardShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED_ACTIVITY, the wallpaper should also be showing.
     */
    @Test
    public void testTranslucentShowWhenLockedActivity() throws Exception {
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("ShowWhenLockedTranslucentActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        assertWallpaperShowing();
        mAmWmState.assertKeyguardShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED activity, the activity behind should not be shown.
     */
    @Test
    public void testTranslucentDoesntRevealBehind() throws Exception {
        launchActivity("TestActivity");
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("TestActivity").build(),
                new WaitForValidActivityState.Builder("ShowWhenLockedTranslucentActivity").build());
        mAmWmState.assertVisibility("TestActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        mAmWmState.assertVisibility("TestActivity", false);
        mAmWmState.assertKeyguardShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    @Test
    public void testDialogShowWhenLockedActivity() throws Exception {
        launchActivity("ShowWhenLockedDialogActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedDialogActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedDialogActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedDialogActivity", true);
        assertWallpaperShowing();
        mAmWmState.assertKeyguardShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Test that showWhenLocked activity is fullscreen when shown over keyguard
     */
    @Test
    public void testShowWhenLockedActivityWhileSplit() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivityName(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivityName("ShowWhenLockedActivity")
                        .setRandomData(true)
                        .setMultipleTask(false)
        );
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(
                new WaitForValidActivityState.Builder("ShowWhenLockedActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertKeyguardShowingAndOccluded();
        mAmWmState.assertDoesNotContainStack("Activity must be full screen.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity occludes Keyguard.
     */
    @Test
    public void testDismissKeyguardActivity() throws Exception {
        gotoKeyguard();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardActivity");
        mAmWmState.waitForKeyguardShowingAndOccluded();
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "DismissKeyguardActivity").build());
        mAmWmState.assertVisibility("DismissKeyguardActivity", true);
        mAmWmState.assertKeyguardShowingAndOccluded();
    }

    @Test
    public void testDismissKeyguardActivity_method() throws Exception {
        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardMethodActivity");
        mAmWmState.waitForKeyguardGone();
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "DismissKeyguardMethodActivity").build());
        mAmWmState.assertVisibility("DismissKeyguardMethodActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat(logSeparator);
    }

    @Test
    public void testDismissKeyguardActivity_method_notTop() throws Exception {
        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("BroadcastReceiverActivity");
        launchActivity("TestActivity");
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguardMethod true");
        assertOnDismissErrorInLogcat(logSeparator);
    }

    @Test
    public void testDismissKeyguardActivity_method_turnScreenOn() throws Exception {
        final String logSeparator = clearLogcat();
        sleepDevice();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("TurnScreenOnDismissKeyguardActivity");
        mAmWmState.waitForKeyguardGone();
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "TurnScreenOnDismissKeyguardActivity").build());
        mAmWmState.assertVisibility("TurnScreenOnDismissKeyguardActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat(logSeparator);
    }

    @Test
    public void testDismissKeyguard_fromShowWhenLocked_notAllowed() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedActivity" ).build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertKeyguardShowingAndOccluded();
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguard true");
        mAmWmState.assertKeyguardShowingAndOccluded();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
    }

    @Test
    public void testKeyguardLock() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity("KeyguardLockActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "KeyguardLockActivity" ).build());
        mAmWmState.assertVisibility("KeyguardLockActivity", true);
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
    }

    @Test
    public void testUnoccludeRotationChange() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        executeShellCommand(getAmStartCmd("ShowWhenLockedActivity"));
        mAmWmState.computeState(new WaitForValidActivityState("ShowWhenLockedActivity"));
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        try (final RotationSession rotationSession = new RotationSession()) {
            rotationSession.set(ROTATION_90);
            pressHomeButton();
            mAmWmState.waitForKeyguardShowingAndNotOccluded();
            mAmWmState.waitForDisplayUnfrozen();
            mAmWmState.assertSanity();
            mAmWmState.assertHomeActivityVisible(false);
            mAmWmState.assertKeyguardShowingAndNotOccluded();
            mAmWmState.assertVisibility("ShowWhenLockedActivity", false);
        }
    }

    private void assertWallpaperShowing() {
        WindowState wallpaper =
                mAmWmState.getWmState().findFirstWindowWithType(TYPE_WALLPAPER);
        assertNotNull(wallpaper);
        assertTrue(wallpaper.isShown());
    }

    @Test
    public void testDismissKeyguardAttrActivity_method_turnScreenOn() throws Exception {
        final String activityName = "TurnScreenOnAttrDismissKeyguardActivity";
        sleepDevice();

        final String logSeparator = clearLogcat();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity(activityName);
        mAmWmState.waitForKeyguardGone();
        mAmWmState.assertVisibility(activityName, true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat(logSeparator);
        assertTrue(isDisplayOn());
    }

    @Test
    public void testDismissKeyguardAttrActivity_method_turnScreenOn_withSecureKeyguard() throws Exception {
        final String activityName = "TurnScreenOnAttrDismissKeyguardActivity";

        setLockCredential();
        sleepDevice();

        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity(activityName);
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertVisibility(activityName, false);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertTrue(isDisplayOn());
    }

    @Test
    public void testScreenOffWhileOccludedStopsActivity() throws Exception {
        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity("ShowWhenLockedAttrActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedAttrActivity" ).build());
        mAmWmState.assertVisibility("ShowWhenLockedAttrActivity", true);
        mAmWmState.assertKeyguardShowingAndOccluded();
        sleepDevice();
        assertSingleLaunchAndStop("ShowWhenLockedAttrActivity", logSeparator);
    }

    @Test
    public void testScreenOffCausesSingleStop() throws Exception {
        final String logSeparator = clearLogcat();
        launchActivity("TestActivity");
        mAmWmState.assertVisibility("TestActivity", true);
        sleepDevice();
        assertSingleLaunchAndStop("TestActivity", logSeparator);
    }
}
