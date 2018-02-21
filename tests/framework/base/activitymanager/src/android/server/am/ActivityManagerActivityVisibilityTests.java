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

import static android.app.ActivityManager.StackId.INVALID_STACK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.server.am.ActivityManagerState.STATE_PAUSED;
import static android.server.am.ActivityManagerState.STATE_RESUMED;
import static android.server.am.Components.ALWAYS_FOCUSABLE_PIP_ACTIVITY;
import static android.server.am.Components.DOCKED_ACTIVITY;
import static android.server.am.Components.LAUNCH_PIP_ON_PIP_ACTIVITY;
import static android.server.am.Components.MOVE_TASK_TO_BACK_ACTIVITY;
import static android.server.am.Components.NO_HISTORY_ACTIVITY;
import static android.server.am.Components.SWIPE_REFRESH_ACTIVITY;
import static android.server.am.Components.TEST_ACTIVITY;
import static android.server.am.Components.TRANSLUCENT_ACTIVITY;
import static android.server.am.Components.TURN_SCREEN_ON_ACTIVITY;
import static android.server.am.Components.TURN_SCREEN_ON_ATTR_ACTIVITY;
import static android.server.am.Components.TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY;
import static android.server.am.Components.TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY;
import static android.server.am.Components.TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY;
import static android.server.am.Components.TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY;
import static android.server.am.UiDeviceUtils.pressBackButton;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.support.test.filters.FlakyTest;

import org.junit.Rule;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerActivityVisibilityTests
 */
public class ActivityManagerActivityVisibilityTests extends ActivityManagerTestBase {

    @Rule
    public final DisableScreenDozeRule mDisableScreenDozeRule = new DisableScreenDozeRule();

    @Presubmit
    @Test
    public void testTranslucentActivityOnTopOfPinnedStack() throws Exception {
        assumeTrue(supportsPip());

        executeShellCommand(getAmStartCmdOverHome(LAUNCH_PIP_ON_PIP_ACTIVITY));
        mAmWmState.waitForValidState(LAUNCH_PIP_ON_PIP_ACTIVITY);
        // NOTE: moving to pinned stack will trigger the pip-on-pip activity to launch the
        // translucent activity.
        final int stackId = mAmWmState.getAmState().getStackIdByActivity(
                LAUNCH_PIP_ON_PIP_ACTIVITY);

        assertNotEquals(stackId, INVALID_STACK_ID);
        executeShellCommand(getMoveToPinnedStackCommand(stackId));

        mAmWmState.computeState(LAUNCH_PIP_ON_PIP_ACTIVITY, ALWAYS_FOCUSABLE_PIP_ACTIVITY);
        mAmWmState.assertFrontStack("Pinned stack must be the front stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertVisibility(LAUNCH_PIP_ON_PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(ALWAYS_FOCUSABLE_PIP_ACTIVITY, true);
    }

    /**
     * Asserts that the home activity is visible when a translucent activity is launched in the
     * fullscreen stack over the home activity.
     */
    @Test
    public void testTranslucentActivityOnTopOfHome() throws Exception {
        assumeTrue(hasHomeScreen());

        launchHomeActivity();
        launchActivity(ALWAYS_FOCUSABLE_PIP_ACTIVITY);

        mAmWmState.computeState(ALWAYS_FOCUSABLE_PIP_ACTIVITY);
        mAmWmState.assertFrontStack("Fullscreen stack must be the front stack.",
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertVisibility(ALWAYS_FOCUSABLE_PIP_ACTIVITY, true);
        mAmWmState.assertHomeActivityVisible(true);
    }

    /**
     * Assert that the home activity is visible if a task that was launched from home is pinned
     * and also assert the next task in the fullscreen stack isn't visible.
     */
    @Presubmit
    @Test
    public void testHomeVisibleOnActivityTaskPinned() throws Exception {
        assumeTrue(supportsPip());

        launchHomeActivity();
        launchActivity(TEST_ACTIVITY);
        launchHomeActivity();
        launchActivity(ALWAYS_FOCUSABLE_PIP_ACTIVITY);
        final int stackId = mAmWmState.getAmState().getStackIdByActivity(
                ALWAYS_FOCUSABLE_PIP_ACTIVITY);

        assertNotEquals(stackId, INVALID_STACK_ID);
        executeShellCommand(getMoveToPinnedStackCommand(stackId));

        mAmWmState.computeState(ALWAYS_FOCUSABLE_PIP_ACTIVITY);

        mAmWmState.assertVisibility(ALWAYS_FOCUSABLE_PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, false);
        mAmWmState.assertHomeActivityVisible(true);
    }

    @Presubmit
    @Test
    public void testTranslucentActivityOverDockedStack() throws Exception {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(DOCKED_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));
        launchActivity(TRANSLUCENT_ACTIVITY, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        mAmWmState.computeState(false /* compareTaskAndStackBounds */,
                new WaitForValidActivityState(TEST_ACTIVITY),
                new WaitForValidActivityState(DOCKED_ACTIVITY),
                new WaitForValidActivityState(TRANSLUCENT_ACTIVITY));
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);
        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY, true);
    }

    @Presubmit
    @FlakyTest(bugId = 72526786)
    @Test
    public void testTurnScreenOnActivity() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.sleepDevice();
            launchActivity(TURN_SCREEN_ON_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_ACTIVITY);
            mAmWmState.assertVisibility(TURN_SCREEN_ON_ACTIVITY, true);
            assertTrue(isDisplayOn());
        }
    }

    @Presubmit
    @Test
    public void testFinishActivityInNonFocusedStack() throws Exception {
        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());

        // Launch two activities in docked stack.
        launchActivityInSplitScreenWithRecents(LAUNCHING_ACTIVITY);
        getLaunchActivityBuilder().setTargetActivityName(BROADCAST_RECEIVER_ACTIVITY).execute();
        mAmWmState.computeState(BROADCAST_RECEIVER_ACTIVITY);
        mAmWmState.assertVisibility(BROADCAST_RECEIVER_ACTIVITY, true);
        // Launch something to fullscreen stack to make it focused.
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
        mAmWmState.computeState(TEST_ACTIVITY);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);
        // Finish activity in non-focused (docked) stack.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);

        mAmWmState.waitForActivityState(LAUNCHING_ACTIVITY, STATE_PAUSED);
        mAmWmState.waitForAllExitingWindows();

        mAmWmState.computeState(LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true);
        mAmWmState.assertVisibility(BROADCAST_RECEIVER_ACTIVITY, false);
    }

    @Test
    public void testFinishActivityWithMoveTaskToBackAfterPause() throws Exception {
        performFinishActivityWithMoveTaskToBack("on_pause");
    }

    @Test
    public void testFinishActivityWithMoveTaskToBackAfterStop() throws Exception {
        performFinishActivityWithMoveTaskToBack("on_stop");
    }

    private void performFinishActivityWithMoveTaskToBack(String finishPoint) throws Exception {
        // Make sure home activity is visible.
        launchHomeActivity();
        if (hasHomeScreen()) {
            mAmWmState.assertHomeActivityVisible(true /* visible */);
        }

        // Launch an activity that calls "moveTaskToBack" to finish itself.
        launchActivity(MOVE_TASK_TO_BACK_ACTIVITY, "finish_point", finishPoint);
        mAmWmState.waitForValidState(MOVE_TASK_TO_BACK_ACTIVITY);
        mAmWmState.assertVisibility(MOVE_TASK_TO_BACK_ACTIVITY, true);

        // Launch a different activity on top.
        launchActivity(BROADCAST_RECEIVER_ACTIVITY);
        mAmWmState.waitForValidState(BROADCAST_RECEIVER_ACTIVITY);
        mAmWmState.waitForActivityState(BROADCAST_RECEIVER_ACTIVITY, STATE_RESUMED);
        mAmWmState.assertVisibility(MOVE_TASK_TO_BACK_ACTIVITY, false);
        mAmWmState.assertVisibility(BROADCAST_RECEIVER_ACTIVITY, true);

        // Finish the top-most activity.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);
        //TODO: BUG: MoveTaskToBackActivity returns to the top of the stack when
        // BroadcastActivity finishes, so homeActivity is not visible afterwards

        // Home must be visible.
        if (hasHomeScreen()) {
            mAmWmState.waitForHomeActivityVisible();
            mAmWmState.assertHomeActivityVisible(true /* visible */);
        }
    }

    /**
     * Asserts that launching between reorder to front activities exhibits the correct backstack
     * behavior.
     */
    @Test
    public void testReorderToFrontBackstack() throws Exception {
        // Start with home on top
        launchHomeActivity();
        if (hasHomeScreen()) {
            mAmWmState.assertHomeActivityVisible(true /* visible */);
        }

        // Launch the launching activity to the foreground
        launchActivity(LAUNCHING_ACTIVITY);

        // Launch the alternate launching activity from launching activity with reorder to front.
        getLaunchActivityBuilder().setTargetActivityName(ALT_LAUNCHING_ACTIVITY)
                .setReorderToFront(true).execute();

        // Launch the launching activity from the alternate launching activity with reorder to
        // front.
        getLaunchActivityBuilder().setTargetActivityName(LAUNCHING_ACTIVITY)
                .setLaunchingActivityName(ALT_LAUNCHING_ACTIVITY).setReorderToFront(true)
                .execute();

        // Press back
        pressBackButton();

        mAmWmState.waitForValidState(ALT_LAUNCHING_ACTIVITY);

        // Ensure the alternate launching activity is in focus
        mAmWmState.assertFocusedActivity("Alt Launching Activity must be focused",
                ALT_LAUNCHING_ACTIVITY);
    }

    /**
     * Asserts that the activity focus and history is preserved moving between the activity and
     * home stack.
     */
    @Test
    public void testReorderToFrontChangingStack() throws Exception {
        // Start with home on top
        launchHomeActivity();
        if (hasHomeScreen()) {
            mAmWmState.assertHomeActivityVisible(true /* visible */);
        }

        // Launch the launching activity to the foreground
        launchActivity(LAUNCHING_ACTIVITY);

        // Launch the alternate launching activity from launching activity with reorder to front.
        getLaunchActivityBuilder().setTargetActivityName(ALT_LAUNCHING_ACTIVITY)
                .setReorderToFront(true).execute();

        // Return home
        launchHomeActivity();
        if (hasHomeScreen()) {
            mAmWmState.assertHomeActivityVisible(true /* visible */);
        }
        // Launch the launching activity from the alternate launching activity with reorder to
        // front.

        // Bring launching activity back to the foreground
        launchActivity(LAUNCHING_ACTIVITY);
        mAmWmState.waitForValidState(LAUNCHING_ACTIVITY);

        // Ensure the alternate launching activity is still in focus.
        mAmWmState.assertFocusedActivity("Alt Launching Activity must be focused",
                ALT_LAUNCHING_ACTIVITY);

        pressBackButton();

        mAmWmState.waitForValidState(LAUNCHING_ACTIVITY);

        // Ensure launching activity was brought forward.
        mAmWmState.assertFocusedActivity("Launching Activity must be focused",
                LAUNCHING_ACTIVITY);
    }

    /**
     * Asserts that a nohistory activity is stopped and removed immediately after a resumed activity
     * above becomes visible and does not idle.
     */
    @Test
    public void testNoHistoryActivityFinishedResumedActivityNotIdle() throws Exception {
        assumeTrue(hasHomeScreen());

        // Start with home on top
        launchHomeActivity();

        // Launch no history activity
        launchActivity(NO_HISTORY_ACTIVITY);

        // Launch an activity with a swipe refresh layout configured to prevent idle.
        launchActivity(SWIPE_REFRESH_ACTIVITY);

        pressBackButton();
        mAmWmState.waitForHomeActivityVisible();
        mAmWmState.assertHomeActivityVisible(true);
    }

    @Test
    public void testTurnScreenOnAttrNoLockScreen() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.disableLockScreen()
                    .sleepDevice();
            final LogSeparator logSeparator = clearLogcat();
            launchActivity(TURN_SCREEN_ON_ATTR_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_ATTR_ACTIVITY);
            mAmWmState.assertVisibility(TURN_SCREEN_ON_ATTR_ACTIVITY, true);
            assertTrue(isDisplayOn());
            assertSingleLaunch(TURN_SCREEN_ON_ATTR_ACTIVITY, logSeparator);
        }
    }

    @Test
    public void testTurnScreenOnAttrWithLockScreen() throws Exception {
        assumeTrue(isHandheld());

        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential()
                    .sleepDevice();
            final LogSeparator logSeparator = clearLogcat();
            launchActivity(TURN_SCREEN_ON_ATTR_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_ATTR_ACTIVITY);
            assertFalse(isDisplayOn());
            assertSingleLaunchAndStop(TURN_SCREEN_ON_ATTR_ACTIVITY, logSeparator);
        }
    }

    @Test
    public void testTurnScreenOnShowOnLockAttr() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.sleepDevice();
            mAmWmState.waitForAllStoppedActivities();
            final LogSeparator logSeparator = clearLogcat();
            launchActivity(TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY);
            mAmWmState.assertVisibility(TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY, true);
            assertTrue(isDisplayOn());
            assertSingleLaunch(TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY, logSeparator);
        }
    }

    @Test
    public void testTurnScreenOnAttrRemove() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.sleepDevice();
            mAmWmState.waitForAllStoppedActivities();
            LogSeparator logSeparator = clearLogcat();
            launchActivity(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY);
            assertTrue(isDisplayOn());
            assertSingleLaunch(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY, logSeparator);

            lockScreenSession.sleepDevice();
            mAmWmState.waitForAllStoppedActivities();
            logSeparator = clearLogcat();
            launchActivity(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY);
            assertFalse(isDisplayOn());
            assertSingleStartAndStop(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY, logSeparator);
        }
    }

    @Test
    @Presubmit
    public void testTurnScreenOnSingleTask() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.sleepDevice();
            LogSeparator logSeparator = clearLogcat();
            launchActivity(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY);
            mAmWmState.assertVisibility(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY, true);
            assertTrue(isDisplayOn());
            assertSingleLaunch(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY, logSeparator);

            lockScreenSession.sleepDevice();
            logSeparator = clearLogcat();
            launchActivity(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY);
            mAmWmState.assertVisibility(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY, true);
            assertTrue(isDisplayOn());
            assertSingleStart(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY, logSeparator);
        }
    }

    @Test
    public void testTurnScreenOnActivity_withRelayout() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.sleepDevice();
            launchActivity(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY);
            mAmWmState.computeState(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY);
            mAmWmState.assertVisibility(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY, true);

            LogSeparator logSeparator = clearLogcat();
            lockScreenSession.sleepDevice();
            mAmWmState.waitFor("Waiting for stopped state", () ->
                    lifecycleStopOccurred(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY, logSeparator));

            // Ensure there was an actual stop if the waitFor timed out.
            assertTrue(lifecycleStopOccurred(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY, logSeparator));
            assertFalse(isDisplayOn());
        }
    }

    private boolean lifecycleStopOccurred(ComponentName activityName, LogSeparator logSeparator) {
        ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);
        return lifecycleCounts.mStopCount > 0;
    }
}
