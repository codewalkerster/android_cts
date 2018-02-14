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

package android.server.am;

import static android.app.ActivityManager.SPLIT_SCREEN_CREATE_MODE_BOTTOM_OR_RIGHT;
import static android.app.ActivityManager.SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.am.Components.TestActivity.TEST_ACTIVITY_ACTION_FINISH_SELF;
import static android.server.am.WindowManagerState.TRANSIT_WALLPAPER_OPEN;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.support.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerSplitScreenTests
 */
public class ActivityManagerSplitScreenTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String NON_RESIZEABLE_ACTIVITY_NAME = "NonResizeableActivity";
    private static final String DOCKED_ACTIVITY_NAME = "DockedActivity";
    private static final String NO_RELAUNCH_ACTIVITY_NAME = "NoRelaunchActivity";
    private static final String SINGLE_INSTANCE_ACTIVITY_NAME = "SingleInstanceActivity";
    private static final String SINGLE_TASK_ACTIVITY_NAME = "SingleTaskActivity";

    private static final int TASK_SIZE = 600;
    private static final int STACK_SIZE = 300;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
    }

    @Test
    public void testMinimumDeviceSize() throws Exception {
        mAmWmState.assertDeviceDefaultDisplaySize(
                "Devices supporting multi-window must be larger than the default minimum"
                        + " task size");
    }

    @Test
    @Presubmit
    @FlakyTest(bugId = 71792393)
    public void testStackList() throws Exception {
        launchActivity(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertContainsStack("Must contain home stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertDoesNotContainStack("Must not contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    @Presubmit
    public void testDockActivity() throws Exception {
        launchActivityInSplitScreenWithRecents(TEST_ACTIVITY_NAME);
        mAmWmState.assertContainsStack("Must contain home stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    @Presubmit
    public void testNonResizeableNotDocked() throws Exception {
        launchActivityInSplitScreenWithRecents(NON_RESIZEABLE_ACTIVITY_NAME);

        mAmWmState.assertContainsStack("Must contain home stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);
        mAmWmState.assertDoesNotContainStack("Must not contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFrontStack("Fullscreen stack must be front stack.",
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    @Presubmit
    public void testLaunchToSide() throws Exception {
        launchActivitiesInSplitScreen(LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    @Presubmit
    public void testLaunchToSideMultiWindowCallbacks() throws Exception {
        // Launch two activities in split-screen mode.
        launchActivitiesInSplitScreen(LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);

        // Exit split-screen mode and ensure we only get 1 multi-window mode changed callback.
        final String logSeparator = clearLogcat();
        removeStacksInWindowingModes(WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        final ActivityLifecycleCounts lifecycleCounts = waitForOnMultiWindowModeChanged(
                TEST_ACTIVITY_NAME, logSeparator);
        assertEquals(1, lifecycleCounts.mMultiWindowModeChangedCount);
    }

    @Test
    @Presubmit
    @FlakyTest(bugId = 72956284)
    public void testNoUserLeaveHintOnMultiWindowModeChanged() throws Exception {
        launchActivity(TEST_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN);

        // Move to docked stack.
        String logSeparator = clearLogcat();
        setActivityTaskWindowingMode(TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        ActivityLifecycleCounts lifecycleCounts = waitForOnMultiWindowModeChanged(
                TEST_ACTIVITY_NAME, logSeparator);
        assertEquals("mMultiWindowModeChangedCount",
                1, lifecycleCounts.mMultiWindowModeChangedCount);
        assertEquals("mUserLeaveHintCount", 0, lifecycleCounts.mUserLeaveHintCount);

        // Make sure docked stack is focused. This way when we dismiss it later fullscreen stack
        // will come up.
        launchActivity(TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);

        // Move activity back to fullscreen stack.
        logSeparator = clearLogcat();
        setActivityTaskWindowingMode(TEST_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN);
        lifecycleCounts = waitForOnMultiWindowModeChanged(TEST_ACTIVITY_NAME, logSeparator);
        assertEquals("mMultiWindowModeChangedCount",
                1, lifecycleCounts.mMultiWindowModeChangedCount);
        assertEquals("mUserLeaveHintCount", 0, lifecycleCounts.mUserLeaveHintCount);
    }

    @Test
    @Presubmit
    public void testLaunchToSideAndBringToFront() throws Exception {
        launchActivitiesInSplitScreen(LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME);

        int taskNumberInitial = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY_NAME);

        // Launch another activity to side to cover first one.
        launchActivity(
                NO_RELAUNCH_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
        int taskNumberCovered = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        assertEquals("Fullscreen stack must have one task added.",
                taskNumberInitial + 1, taskNumberCovered);
        mAmWmState.assertFocusedActivity("Launched to side covering activity must be in front.",
                NO_RELAUNCH_ACTIVITY_NAME);

        // Launch activity that was first launched to side. It should be brought to front.
        getLaunchActivityBuilder()
                .setTargetActivityName(TEST_ACTIVITY_NAME)
                .setToSide(true)
                .setWaitForLaunched(true)
                .execute();
        int taskNumberFinal = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        assertEquals("Task number in fullscreen stack must remain the same.",
                taskNumberCovered, taskNumberFinal);
        mAmWmState.assertFocusedActivity("Launched to side covering activity must be in front.",
                TEST_ACTIVITY_NAME);
    }

    @Test
    @Presubmit
    @FlakyTest(bugId = 71792393)
    public void testLaunchToSideMultiple() throws Exception {
        launchActivitiesInSplitScreen(LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME);

        int taskNumberInitial = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        assertNotNull("Launched to side activity must be in fullscreen stack.",
                mAmWmState.getAmState().getTaskByActivityName(
                        TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY));

        // Try to launch to side same activity again.
        getLaunchActivityBuilder().setToSide(true).execute();
        final String[] waitForActivitiesVisible =
                new String[] {TEST_ACTIVITY_NAME, LAUNCHING_ACTIVITY};
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberFinal = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        assertEquals("Task number mustn't change.", taskNumberInitial, taskNumberFinal);
        mAmWmState.assertFocusedActivity("Launched to side activity must remain in front.",
                TEST_ACTIVITY_NAME);
        assertNotNull("Launched to side activity must remain in fullscreen stack.",
                mAmWmState.getAmState().getTaskByActivityName(
                        TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY));
    }

    @Test
    @Presubmit
    public void testLaunchToSideSingleInstance() throws Exception {
        launchTargetToSide(SINGLE_INSTANCE_ACTIVITY_NAME, false);
    }

    @Test
    public void testLaunchToSideSingleTask() throws Exception {
        launchTargetToSide(SINGLE_TASK_ACTIVITY_NAME, false);
    }

    @Presubmit
    @FlakyTest(bugId = 71792393)
    @Test
    public void testLaunchToSideMultipleWithDifferentIntent() throws Exception {
        launchTargetToSide(TEST_ACTIVITY_NAME, true);
    }

    private void launchTargetToSide(String targetActivityName, boolean taskCountMustIncrement)
            throws Exception {
        final LaunchActivityBuilder targetActivityLauncher = getLaunchActivityBuilder()
                .setTargetActivityName(targetActivityName)
                .setToSide(true)
                .setRandomData(true)
                .setMultipleTask(false);

        // TODO(b/70618153): A workaround to allow activities to launch in split-screen leads to
        // the target being launched directly. Options such as LaunchActivityBuilder#setRandomData
        // are not respected.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivityName(LAUNCHING_ACTIVITY),
                targetActivityLauncher);

        final WaitForValidActivityState[] waitForActivitiesVisible =
                new WaitForValidActivityState[] {
                    new WaitForValidActivityState.Builder(targetActivityName).build(),
                    new WaitForValidActivityState.Builder(LAUNCHING_ACTIVITY).build()
                };

        mAmWmState.computeState(waitForActivitiesVisible);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        int taskNumberInitial = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        assertNotNull("Launched to side activity must be in fullscreen stack.",
                mAmWmState.getAmState().getTaskByActivityName(
                        targetActivityName, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY));

        // Try to launch to side same activity again with different data.
        targetActivityLauncher.execute();
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberSecondLaunch = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        if (taskCountMustIncrement) {
            assertEquals("Task number must be incremented.", taskNumberInitial + 1,
                    taskNumberSecondLaunch);
        } else {
            assertEquals("Task number must not change.", taskNumberInitial,
                    taskNumberSecondLaunch);
        }
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);
        assertNotNull("Launched to side activity must be launched in fullscreen stack.",
                mAmWmState.getAmState().getTaskByActivityName(
                        targetActivityName, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY));

        // Try to launch to side same activity again with different random data. Note that null
        // cannot be used here, since the first instance of TestActivity is launched with no data
        // in order to launch into split screen.
        targetActivityLauncher.execute();
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberFinal = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        if (taskCountMustIncrement) {
            assertEquals("Task number must be incremented.", taskNumberSecondLaunch + 1,
                    taskNumberFinal);
        } else {
            assertEquals("Task number must not change.", taskNumberSecondLaunch,
                    taskNumberFinal);
        }
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);
        assertNotNull("Launched to side activity must be launched in fullscreen stack.",
                mAmWmState.getAmState().getTaskByActivityName(
                        targetActivityName, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY));
    }

    @Presubmit
    @Test
    public void testLaunchToSideMultipleWithFlag() throws Exception {
        launchActivitiesInSplitScreen(LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME);
        int taskNumberInitial = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        assertNotNull("Launched to side activity must be in fullscreen stack.",
                mAmWmState.getAmState().getTaskByActivityName(
                        TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY));

        // Try to launch to side same activity again, but with Intent#FLAG_ACTIVITY_MULTIPLE_TASK.
        getLaunchActivityBuilder().setToSide(true).setMultipleTask(true).execute();
        final String[] waitForActivitiesVisible =
                new String[] {LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME};
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberFinal = mAmWmState.getAmState().getStandardTaskCountByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        assertEquals("Task number must be incremented.", taskNumberInitial + 1,
                taskNumberFinal);
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY_NAME);
        assertNotNull("Launched to side activity must remain in fullscreen stack.",
                mAmWmState.getAmState().getTaskByActivityName(
                        TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY));
    }

    @Test
    public void testRotationWhenDocked() throws Exception {
        launchActivitiesInSplitScreen(LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);

        // Rotate device single steps (90°) 0-1-2-3.
        // Each time we compute the state we implicitly assert valid bounds.
        String[] waitForActivitiesVisible =
            new String[] {LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME};
        try (final RotationSession rotationSession = new RotationSession()) {
            for (int i = 0; i < 4; i++) {
                rotationSession.set(i);
                mAmWmState.computeState(waitForActivitiesVisible);
            }
            // Double steps (180°) We ended the single step at 3. So, we jump directly to 1 for
            // double step. So, we are testing 3-1-3 for one side and 0-2-0 for the other side.
            rotationSession.set(ROTATION_90);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_270);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_0);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_180);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_0);
            mAmWmState.computeState(waitForActivitiesVisible);
        }
    }

    @Test
    @Presubmit
    public void testRotationWhenDockedWhileLocked() throws Exception {
        launchActivitiesInSplitScreen(LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME);
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);

        String[] waitForActivitiesVisible =
                new String[] {LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME};
        try (final RotationSession rotationSession = new RotationSession();
             final LockScreenSession lockScreenSession = new LockScreenSession()) {
            for (int i = 0; i < 4; i++) {
                lockScreenSession.sleepDevice();
                rotationSession.set(i);
                lockScreenSession.wakeUpDevice()
                        .unlockDevice();
                mAmWmState.computeState(waitForActivitiesVisible);
            }
        }
    }

    @Test
    public void testMinimizedFromEachDockedSide() throws Exception {
        try (final RotationSession rotationSession = new RotationSession()) {
            for (int i = 0; i < 2; i++) {
                rotationSession.set(i);
                launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME);
                if (!mAmWmState.isScreenPortrait() && isTablet()) {
                    // Test minimize to the right only on tablets in landscape
                    removeStacksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
                    launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME,
                            SPLIT_SCREEN_CREATE_MODE_BOTTOM_OR_RIGHT);
                }
                removeStacksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
            }
        }
    }

    @Test
    @Presubmit
    public void testRotationWhileDockMinimized() throws Exception {
        launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME);

        // Rotate device single steps (90°) 0-1-2-3.
        // Each time we compute the state we implicitly assert valid bounds in minimized mode.
        String[] waitForActivitiesVisible = new String[] {TEST_ACTIVITY_NAME};
        try (final RotationSession rotationSession = new RotationSession()) {
            for (int i = 0; i < 4; i++) {
                rotationSession.set(i);
                mAmWmState.computeState(waitForActivitiesVisible);
            }

            // Double steps (180°) We ended the single step at 3. So, we jump directly to 1 for
            // double step. So, we are testing 3-1-3 for one side and 0-2-0 for the other side in
            // minimized mode.
            rotationSession.set(ROTATION_90);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_270);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_0);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_180);
            mAmWmState.computeState(waitForActivitiesVisible);
            rotationSession.set(ROTATION_0);
            mAmWmState.computeState(waitForActivitiesVisible);
        }
    }

    @Test
    public void testMinimizeAndUnminimizeThenGoingHome() throws Exception {
        // Rotate the screen to check that minimize, unminimize, dismiss the docked stack and then
        // going home has the correct app transition
        try (final RotationSession rotationSession = new RotationSession()) {
            for (int i = 0; i < 4; i++) {
                rotationSession.set(i);
                launchActivityInDockStackAndMinimize(DOCKED_ACTIVITY_NAME);

                // Unminimize the docked stack
                pressAppSwitchButton();
                waitForDockNotMinimized();
                assertDockNotMinimized();

                // Dismiss the dock stack
                launchActivity(TEST_ACTIVITY_NAME,
                        WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
                setActivityTaskWindowingMode(DOCKED_ACTIVITY_NAME,
                        WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
                mAmWmState.computeState(new String[]{DOCKED_ACTIVITY_NAME});

                // Go home and check the app transition
                assertNotSame(TRANSIT_WALLPAPER_OPEN, mAmWmState.getWmState().getLastTransition());
                pressHomeButton();
                mAmWmState.computeState(true);
                assertEquals(TRANSIT_WALLPAPER_OPEN, mAmWmState.getWmState().getLastTransition());
            }
        }
    }

    @Test
    @Presubmit
    public void testFinishDockActivityWhileMinimized() throws Exception {
        launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME);

        executeShellCommand("am broadcast -a " + TEST_ACTIVITY_ACTION_FINISH_SELF);
        waitForDockNotMinimized();
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, false);
        assertDockNotMinimized();
    }

    @Test
    @Presubmit
    public void testDockedStackToMinimizeWhenUnlocked() throws Exception {
        launchActivityInSplitScreenWithRecents(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.sleepDevice()
                    .wakeUpDevice()
                    .unlockDevice();
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));
            assertDockMinimized();
        }
    }

    @Test
    public void testMinimizedStateWhenUnlockedAndUnMinimized() throws Exception {
        launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME);

        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.sleepDevice()
                    .wakeUpDevice()
                    .unlockDevice();
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));

            // Unminimized back to splitscreen
            pressAppSwitchButton();
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));
        }
    }

    @Test
    @Presubmit
    public void testResizeDockedStack() throws Exception {
        launchActivitiesInSplitScreen(DOCKED_ACTIVITY_NAME, TEST_ACTIVITY_NAME);
        resizeDockedStack(STACK_SIZE, STACK_SIZE, TASK_SIZE, TASK_SIZE);
        mAmWmState.computeState(false /* compareTaskAndStackBounds */,
                new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(DOCKED_ACTIVITY_NAME).build());
        mAmWmState.assertContainsStack("Must contain secondary split-screen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain primary split-screen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        assertEquals(new Rect(0, 0, STACK_SIZE, STACK_SIZE),
                mAmWmState.getAmState().getStandardStackByWindowingMode(
                        WINDOWING_MODE_SPLIT_SCREEN_PRIMARY).getBounds());
        mAmWmState.assertDockedTaskBounds(TASK_SIZE, TASK_SIZE, DOCKED_ACTIVITY_NAME);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
    }

    @Test
    public void testActivityLifeCycleOnResizeDockedStack() throws Exception {
        final WaitForValidActivityState[] waitTestActivityName =
                new WaitForValidActivityState[] {new WaitForValidActivityState.Builder(
                        TEST_ACTIVITY_NAME).build()};
        launchActivity(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(waitTestActivityName);
        final Rect fullScreenBounds = mAmWmState.getWmState().getStandardStackByWindowingMode(
                WINDOWING_MODE_FULLSCREEN).getBounds();

        setActivityTaskWindowingMode(TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        mAmWmState.computeState(waitTestActivityName);
        launchActivity(NO_RELAUNCH_ACTIVITY_NAME,
                WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);

        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(NO_RELAUNCH_ACTIVITY_NAME).build());
        final Rect initialDockBounds = mAmWmState.getWmState().getStandardStackByWindowingMode(
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY) .getBounds();

        final String logSeparator = clearLogcat();

        Rect newBounds = computeNewDockBounds(fullScreenBounds, initialDockBounds, true);
        resizeDockedStack(newBounds.width(), newBounds.height(), newBounds.width(), newBounds.height());
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(NO_RELAUNCH_ACTIVITY_NAME).build());

        // We resize twice to make sure we cross an orientation change threshold for both
        // activities.
        newBounds = computeNewDockBounds(fullScreenBounds, initialDockBounds, false);
        resizeDockedStack(newBounds.width(), newBounds.height(), newBounds.width(), newBounds.height());
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(NO_RELAUNCH_ACTIVITY_NAME).build());
        assertActivityLifecycle(TEST_ACTIVITY_NAME, true /* relaunched */, logSeparator);
        assertActivityLifecycle(NO_RELAUNCH_ACTIVITY_NAME, false /* relaunched */, logSeparator);
    }

    private Rect computeNewDockBounds(
            Rect fullscreenBounds, Rect dockBounds, boolean reduceSize) {
        final boolean inLandscape = fullscreenBounds.width() > dockBounds.width();
        // We are either increasing size or reducing it.
        final float sizeChangeFactor = reduceSize ? 0.5f : 1.5f;
        final Rect newBounds = new Rect(dockBounds);
        if (inLandscape) {
            // In landscape we change the width.
            newBounds.right = (int) (newBounds.left + (newBounds.width() * sizeChangeFactor));
        } else {
            // In portrait we change the height
            newBounds.bottom = (int) (newBounds.top + (newBounds.height() * sizeChangeFactor));
        }

        return newBounds;
    }

    @Test
    @Presubmit
    public void testStackListOrderLaunchDockedActivity() throws Exception {
        launchActivityInSplitScreenWithRecents(TEST_ACTIVITY_NAME);

        final int homeStackIndex = mAmWmState.getStackIndexByActivityType(ACTIVITY_TYPE_HOME);
        final int recentsStackIndex = mAmWmState.getStackIndexByActivityType(ACTIVITY_TYPE_RECENTS);
        assertTrue("Recents stack should be on top of home stack",
                recentsStackIndex < homeStackIndex);
    }

    @Test
    @Presubmit
    public void testStackListOrderOnSplitScreenDismissed() throws Exception {
        launchActivitiesInSplitScreen(DOCKED_ACTIVITY_NAME, TEST_ACTIVITY_NAME);

        setActivityTaskWindowingMode(DOCKED_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(
                DOCKED_ACTIVITY_NAME).setWindowingMode(WINDOWING_MODE_FULLSCREEN).build());

        final int homeStackIndex = mAmWmState.getStackIndexByActivityType(ACTIVITY_TYPE_HOME);
        final int prevSplitScreenPrimaryIndex =
                mAmWmState.getAmState().getStackIndexByActivityName(DOCKED_ACTIVITY_NAME);
        final int prevSplitScreenSecondaryIndex =
                mAmWmState.getAmState().getStackIndexByActivityName(TEST_ACTIVITY_NAME);

        final int expectedHomeStackIndex =
                (prevSplitScreenPrimaryIndex > prevSplitScreenSecondaryIndex
                        ? prevSplitScreenPrimaryIndex : prevSplitScreenSecondaryIndex) - 1;
        assertTrue("Home stack needs to be directly behind the top stack",
                expectedHomeStackIndex == homeStackIndex);
    }

    private void launchActivityInDockStackAndMinimize(String activityName) throws Exception {
        launchActivityInDockStackAndMinimize(activityName, SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT);
    }

    private void launchActivityInDockStackAndMinimize(String activityName, int createMode)
            throws Exception {
        launchActivityInSplitScreenWithRecents(activityName, createMode);
        pressHomeButton();
        waitForAndAssertDockMinimized();
    }

    private void assertDockMinimized() {
        assertTrue(mAmWmState.getWmState().isDockedStackMinimized());
    }

    private void waitForAndAssertDockMinimized() throws Exception {
        waitForDockMinimized();
        assertDockMinimized();
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFocusedStack("Home activity should be focused in minimized mode",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);
    }

    private void assertDockNotMinimized() {
        assertFalse(mAmWmState.getWmState().isDockedStackMinimized());
    }

    private void waitForDockMinimized() throws Exception {
        mAmWmState.waitForWithWmState(state -> state.isDockedStackMinimized(),
                "***Waiting for Dock stack to be minimized");
    }

    private void waitForDockNotMinimized() throws Exception {
        mAmWmState.waitForWithWmState(state -> !state.isDockedStackMinimized(),
                "***Waiting for Dock stack to not be minimized");
    }
}
