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
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.server.am.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.am.ActivityManagerDisplayTestBase.ReportedDisplayMetrics
        .getDisplayMetrics;
import static android.server.am.ActivityManagerState.STATE_RESUMED;
import static android.server.am.ActivityManagerState.STATE_STOPPED;
import static android.server.am.StateLogger.logE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.server.am.ActivityManagerState.ActivityDisplay;
import android.server.am.displayservice.DisplayHelper;
import android.support.annotation.Nullable;

import android.support.test.filters.FlakyTest;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerMultiDisplayTests
 */
public class ActivityManagerMultiDisplayTests extends ActivityManagerDisplayTestBase {
    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String VIRTUAL_DISPLAY_ACTIVITY = "VirtualDisplayActivity";
    private static final String RESIZEABLE_ACTIVITY_NAME = "ResizeableActivity";
    private static final String NON_RESIZEABLE_ACTIVITY_NAME = "NonResizeableActivity";
    private static final String SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME = "ShowWhenLockedAttrActivity";
    private static final String SECOND_PACKAGE = "android.server.am.second";
    private static final String THIRD_PACKAGE = "android.server.am.third";
    private static final ComponentName SECOND_ACTIVITY = ComponentName.createRelative(
            SECOND_PACKAGE, ".SecondActivity");
    private static final ComponentName SECOND_NO_EMBEDDING_ACTIVITY = ComponentName.createRelative(
            SECOND_PACKAGE, ".SecondActivityNoEmbedding");
    private static final ComponentName LAUNCH_BROADCAST_RECEIVER = ComponentName.createRelative(
            SECOND_PACKAGE, ".LaunchBroadcastReceiver");
    /** See AndroidManifest.xml of appSecondUid. */
    private static final String LAUNCH_BROADCAST_ACTION =
            SECOND_PACKAGE + ".LAUNCH_BROADCAST_ACTION";
    private static final ComponentName THIRD_ACTIVITY = ComponentName.createRelative(
            THIRD_PACKAGE, ".ThirdActivity");

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
    }

    /**
     * Tests launching an activity on virtual display.
     */
    @Presubmit
    @Test
    public void testLaunchActivityOnSecondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            final String logSeparator = clearLogcat();
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    TEST_ACTIVITY_NAME);

            // Check that activity is on the right display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be on the secondary display and resumed",
                    getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            // Check that activity config corresponds to display config.
            final ReportedSizes reportedSizes = getLastReportedSizesForActivity(TEST_ACTIVITY_NAME,
                    logSeparator);
            assertEquals("Activity launched on secondary display must have proper configuration",
                    CUSTOM_DENSITY_DPI, reportedSizes.densityDpi);
        }
    }

    /**
     * Tests launching a non-resizeable activity on virtual display. It should land on the
     * default display.
     */
    @Test
    public void testLaunchNonResizeableActivityOnSecondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(NON_RESIZEABLE_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(NON_RESIZEABLE_ACTIVITY_NAME));

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    NON_RESIZEABLE_ACTIVITY_NAME);

            // Check that activity is on the right display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
            final ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be on the primary display and resumed",
                    getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on the primary display", frontStackId);
        }
    }

    /**
     * Tests launching a non-resizeable activity on virtual display while split-screen is active
     * on the primary display. It should land on the primary display and dismiss docked stack.
     */
    @Test
    public void testLaunchNonResizeableActivityWithSplitScreen() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Start launching activity.
        launchActivityInSplitScreenWithRecents(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setLaunchInSplitScreen(true)
                    .createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(NON_RESIZEABLE_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(NON_RESIZEABLE_ACTIVITY_NAME));

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    NON_RESIZEABLE_ACTIVITY_NAME);

            // Check that activity is on the right display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
            final ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be on the primary display and resumed",
                    getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on the primary display", frontStackId);
            mAmWmState.assertDoesNotContainStack("Must not contain docked stack.",
                    WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        }
    }

    /**
     * Tests moving a non-resizeable activity to a virtual display. It should stay on the default
     * display with no action performed.
     */
    @Test
    public void testMoveNonResizeableActivityToSecondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            // Launch a non-resizeable activity on a primary display.
            launchActivityInNewTask(NON_RESIZEABLE_ACTIVITY_NAME);
            // Launch a resizeable activity on new secondary display to create a new stack there.
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mId);
            final int externalFrontStackId = mAmWmState.getAmState()
                    .getFrontStackId(newDisplay.mId);

            // Try to move the non-resizeable activity to new secondary display.
            moveActivityToStack(NON_RESIZEABLE_ACTIVITY_NAME, externalFrontStackId);
            mAmWmState.computeState(new WaitForValidActivityState(NON_RESIZEABLE_ACTIVITY_NAME));

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    RESIZEABLE_ACTIVITY_NAME);

            // Check that activity is in the same stack
            final int defaultFrontStackId = mAmWmState.getAmState().getFrontStackId(
                    DEFAULT_DISPLAY_ID);
            final ActivityManagerState.ActivityStack defaultFrontStack =
                    mAmWmState.getAmState().getStackById(defaultFrontStackId);
            assertEquals("Launched activity must be on the primary display and resumed",
                    getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                    defaultFrontStack.getTopTask().mRealActivity);
            mAmWmState.assertFocusedStack("Focus must remain on the secondary display",
                    externalFrontStackId);
        }
    }

    /**
     * Tests launching a non-resizeable activity on virtual display from activity there. It should
     * land on the secondary display based on the resizeability of the root activity of the task.
     */
    @Test
    public void testLaunchNonResizeableActivityFromSecondaryDisplaySameTask() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);
            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    BROADCAST_RECEIVER_ACTIVITY);

            // Check that launching activity is on the secondary display.
            int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be on the secondary display and resumed",
                    getActivityComponentName(BROADCAST_RECEIVER_ACTIVITY),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on the secondary display", frontStackId);

            // Launch non-resizeable activity from secondary display.
            executeShellCommand("am broadcast -a trigger_broadcast --ez launch_activity true "
                    + "--ez new_task true --es target_activity " + NON_RESIZEABLE_ACTIVITY_NAME);
            mAmWmState.computeState(new WaitForValidActivityState(NON_RESIZEABLE_ACTIVITY_NAME));

            // Check that non-resizeable activity is on the secondary display, because of the
            // resizeable root of the task.
            frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            frontStack = mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be on the primary display and resumed",
                    getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on the primary display", frontStackId);
        }
    }

    /**
     * Tests launching a non-resizeable activity on virtual display from activity there. It should
     * land on some different suitable display (usually - on the default one).
     */
    @Test
    public void testLaunchNonResizeableActivityFromSecondaryDisplayNewTask() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    LAUNCHING_ACTIVITY);

            // Check that launching activity is on the secondary display.
            int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be on the secondary display and resumed",
                    getActivityComponentName(LAUNCHING_ACTIVITY),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on the secondary display", frontStackId);

            // Launch non-resizeable activity from secondary display.
            getLaunchActivityBuilder().setTargetActivityName(NON_RESIZEABLE_ACTIVITY_NAME)
                    .setNewTask(true).setMultipleTask(true).execute();

            // Check that non-resizeable activity is on the primary display.
            frontStackId = mAmWmState.getAmState().getFocusedStackId();
            frontStack = mAmWmState.getAmState().getStackById(frontStackId);
            assertFalse("Launched activity must be on a different display",
                    newDisplay.mId == frontStack.mDisplayId);
            assertEquals("Launched activity must be resumed",
                    getActivityComponentName(NON_RESIZEABLE_ACTIVITY_NAME),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on a just launched activity",
                    frontStackId);
        }
    }

    /**
     * Tests launching an activity on a virtual display without special permission must not be
     * allowed.
     */
    @Test
    public void testLaunchWithoutPermissionOnVirtualDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            final String logSeparator = clearLogcat();

            // Try to launch an activity and check it security exception was triggered.
            final String broadcastTarget = "-a " + LAUNCH_BROADCAST_ACTION
                    + " -p " + LAUNCH_BROADCAST_RECEIVER.getPackageName();
            final String includeStoppedPackagesFlag = " -f 0x00000020";
            executeShellCommand("am broadcast " + broadcastTarget
                    + " --ez launch_activity true --es target_activity " + TEST_ACTIVITY_NAME
                    + " --es package_name " + componentName
                    + " --ei display_id " + newDisplay.mId
                    + includeStoppedPackagesFlag);

            assertSecurityException("LaunchBroadcastReceiver", logSeparator);

            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));
            assertFalse("Restricted activity must not be launched",
                    mAmWmState.getAmState().containsActivity(TEST_ACTIVITY_NAME));
        }
    }

    /**
     * Tests launching an activity on a virtual display without special permission must be allowed
     * for activities with same UID.
     */
    @Test
    public void testLaunchWithoutPermissionOnVirtualDisplayByOwner() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Try to launch an activity and check it security exception was triggered.
            final String broadcastTarget = "-a " + componentName + ".LAUNCH_BROADCAST_ACTION"
                    + " -p " + componentName;
            executeShellCommand("am broadcast " + broadcastTarget
                    + " --ez launch_activity true --es target_activity " + TEST_ACTIVITY_NAME
                    + " --es package_name " + componentName
                    + " --ei display_id " + newDisplay.mId);

            mAmWmState.waitForValidState(TEST_ACTIVITY_NAME);

            final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            final ActivityManagerState.ActivityStack focusedStack =
                    mAmWmState.getAmState().getStackById(externalFocusedStackId);
            assertEquals("Focused stack must be on secondary display", newDisplay.mId,
                    focusedStack.mDisplayId);

            mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                    TEST_ACTIVITY_NAME);
            assertEquals("Activity launched by owner must be on external display",
                    externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity via shell
     * command and without specifying the display id - the second activity must appear on the
     * primary display.
     */
    @Presubmit
    @Test
    public void testConsequentLaunchActivity() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    TEST_ACTIVITY_NAME);

            // Launch second activity without specifying display.
            launchActivity(LAUNCHING_ACTIVITY);
            mAmWmState.computeState(new WaitForValidActivityState(LAUNCHING_ACTIVITY));

            // Check that activity is launched in focused stack on primary display.
            mAmWmState.assertFocusedActivity("Launched activity must be focused",
                    LAUNCHING_ACTIVITY);
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
            final ActivityManagerState.ActivityStack frontStack
                    = mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be resumed in front stack",
                    getActivityComponentName(LAUNCHING_ACTIVITY), frontStack.mResumedActivity);
            assertEquals("Front stack must be on primary display",
                    DEFAULT_DISPLAY_ID, frontStack.mDisplayId);
        }
    }

    /**
     * Tests launching an activity on simulated display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    @FlakyTest(bugId = 71564456)
    @Presubmit
    @Test
    public void testConsequentLaunchActivityFromSecondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(LAUNCHING_ACTIVITY));

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be resumed",
                    LAUNCHING_ACTIVITY);

            // Launch second activity from app on secondary display without specifying display id.
            getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME).execute();
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));

            // Check that activity is launched in focused stack on external display.
            mAmWmState.assertFocusedActivity("Launched activity must be focused",
                    TEST_ACTIVITY_NAME);
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack frontStack
                    = mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be resumed in front stack",
                    getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    @Test
    public void testConsequentLaunchActivityFromVirtualDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(LAUNCHING_ACTIVITY);

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be resumed",
                    LAUNCHING_ACTIVITY);

            // Launch second activity from app on secondary display without specifying display id.
            getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME).execute();
            mAmWmState.computeState(TEST_ACTIVITY_NAME);

            // Check that activity is launched in focused stack on external display.
            mAmWmState.assertFocusedActivity("Launched activity must be focused",
                    TEST_ACTIVITY_NAME);
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack frontStack = mAmWmState.getAmState()
                    .getStackById(frontStackId);
            assertEquals("Launched activity must be resumed in front stack",
                    getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one with specifying the target display - it must appear on the secondary display.
     */
    @Test
    public void testConsequentLaunchActivityFromVirtualDisplayToTargetDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(LAUNCHING_ACTIVITY);

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be resumed",
                    LAUNCHING_ACTIVITY);

            // Launch second activity from app on secondary display specifying same display id.
            getLaunchActivityBuilder()
                    .setTargetActivity(SECOND_ACTIVITY)
                    .setDisplayId(newDisplay.mId)
                    .execute();
            mAmWmState.computeState(TEST_ACTIVITY_NAME);

            // Check that activity is launched in focused stack on external display.
            mAmWmState.assertFocusedActivity("Launched activity must be focused", SECOND_ACTIVITY);
            int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be resumed in front stack",
                    SECOND_ACTIVITY.flattenToShortString(), frontStack.mResumedActivity);

            // Launch other activity with different uid and check if it has launched successfully.
            getLaunchActivityBuilder()
                    .setUseBroadcastReceiver(LAUNCH_BROADCAST_RECEIVER, LAUNCH_BROADCAST_ACTION)
                    .setDisplayId(newDisplay.mId)
                    .setTargetActivity(THIRD_ACTIVITY)
                    .execute();
            mAmWmState.waitForValidState(new WaitForValidActivityState(THIRD_ACTIVITY));

            // Check that activity is launched in focused stack on external display.
            mAmWmState.assertFocusedActivity("Launched activity must be focused", THIRD_ACTIVITY);
            frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            frontStack = mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be resumed in front stack",
                    THIRD_ACTIVITY.flattenToShortString(), frontStack.mResumedActivity);
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity that
     * doesn't allow embedding - it should fail with security exception.
     */
    @Test
    public void testConsequentLaunchActivityFromVirtualDisplayNoEmbedding() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(LAUNCHING_ACTIVITY);

            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be resumed",
                    LAUNCHING_ACTIVITY);

            final String logSeparator = clearLogcat();

            // Launch second activity from app on secondary display specifying same display id.
            getLaunchActivityBuilder()
                    .setTargetActivity(SECOND_NO_EMBEDDING_ACTIVITY)
                    .setDisplayId(newDisplay.mId)
                    .execute();

            assertSecurityException("ActivityLauncher", logSeparator);
        }
    }

    /**
     * Tests launching an activity to secondary display from activity on primary display.
     */
    @Test
    public void testLaunchActivityFromAppToSecondaryDisplay() throws Exception {
        // Start launching activity.
        launchActivity(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity on secondary display from the app on primary display.
            getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME)
                    .setDisplayId(newDisplay.mId).execute();

            // Check that activity is launched on external display.
            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));
            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    TEST_ACTIVITY_NAME);
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Launched activity must be resumed in front stack",
                    getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        }
    }

    /**
     * Tests launching activities on secondary and then on primary display to see if the stack
     * visibility is not affected.
     */
    @Presubmit
    @Test
    public void testLaunchActivitiesAffectsVisibility() throws Exception {
        // Start launching activity.
        launchActivity(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activity on primary display and check if it doesn't affect activity on
            // secondary display.
            getLaunchActivityBuilder().setTargetActivityName(RESIZEABLE_ACTIVITY_NAME).execute();
            mAmWmState.waitForValidState(RESIZEABLE_ACTIVITY_NAME);
            mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
        }
    }

    /**
     * Test that move-task works when moving between displays.
     */
    @Presubmit
    @Test
    public void testMoveTaskBetweenDisplays() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityManagerState.ActivityStack focusedStack = mAmWmState.getAmState().getStackById(
                    defaultDisplayStackId);
            assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                    focusedStack.mDisplayId);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                    TEST_ACTIVITY_NAME);
            int focusedStackId = mAmWmState.getAmState().getFocusedStackId();
            focusedStack = mAmWmState.getAmState().getStackById(focusedStackId);
            assertEquals("Focused stack must be on secondary display",
                    newDisplay.mId, focusedStack.mDisplayId);

            // Move activity from secondary display to primary.
            moveActivityToStack(TEST_ACTIVITY_NAME, defaultDisplayStackId);
            mAmWmState.waitForFocusedStack(defaultDisplayStackId);
            mAmWmState.assertFocusedActivity("Focus must be on moved activity", TEST_ACTIVITY_NAME);
            focusedStackId = mAmWmState.getAmState().getFocusedStackId();
            focusedStack = mAmWmState.getAmState().getStackById(focusedStackId);
            assertEquals("Focus must return to primary display", DEFAULT_DISPLAY_ID,
                    focusedStack.mDisplayId);
        }
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to fullscreen stack in split-screen.
     */
    @FlakyTest(bugId = 69573940)
    @Presubmit
    @Test
    public void testStackFocusSwitchOnDisplayRemoved() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Start launching activity into docked stack.
        launchActivityInSplitScreenWithRecents(LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity(true /* splitScreen */,
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to docked stack in split-screen.
     */
    @Test
    public void testStackFocusSwitchOnDisplayRemoved2() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Setup split-screen.
        launchActivitiesInSplitScreen(RESIZEABLE_ACTIVITY_NAME, LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity(true /* splitScreen */,
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version works without split-screen.
     */
    @Test
    public void testStackFocusSwitchOnDisplayRemoved3() throws Exception {
        // Start an activity on default display to determine default stack.
        launchActivity(BROADCAST_RECEIVER_ACTIVITY);
        final int focusedStackWindowingMode = mAmWmState.getAmState().getFrontStackWindowingMode(
                DEFAULT_DISPLAY_ID);
        // Finish probing activity.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);

        tryCreatingAndRemovingDisplayWithActivity(false /* splitScreen */,
                focusedStackWindowingMode);
    }

    /**
     * Create a virtual display, launch a test activity there, destroy the display and check if test
     * activity is moved to a stack on the default display.
     */
    private void tryCreatingAndRemovingDisplayWithActivity(boolean splitScreen, int windowingMode)
            throws Exception {
        String logSeparator;
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setPublicDisplay(true)
                    .setLaunchInSplitScreen(splitScreen)
                    .createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            if (splitScreen) {
                mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);
            }

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                    TEST_ACTIVITY_NAME);
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            // Destroy virtual display.
            logSeparator = clearLogcat();
        }

        assertActivityLifecycle(TEST_ACTIVITY_NAME, false /* relaunched */, logSeparator);
        mAmWmState.waitForValidState(TEST_ACTIVITY_NAME, windowingMode, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertSanity();
        mAmWmState.assertValidBounds(true /* compareTaskAndStackBounds */);

        // Check if the focus is switched back to primary display.
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedStack(
                "Default stack on primary display must be focused after display removed",
                windowingMode, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFocusedActivity(
                "Focus must be switched back to activity on primary display",
                TEST_ACTIVITY_NAME);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     */
    @Test
    public void testStackFocusSwitchOnStackEmptied() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            final int focusedStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);
            mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                    BROADCAST_RECEIVER_ACTIVITY);

            // Lock the device, so that activity containers will be detached.
            sleepDevice();

            // Finish activity on secondary display.
            executeShellCommand(FINISH_ACTIVITY_BROADCAST);

            // Unlock and check if the focus is switched back to primary display.
            wakeUpAndUnlockDevice();
            mAmWmState.waitForFocusedStack(focusedStackId);
            mAmWmState.waitForValidState(VIRTUAL_DISPLAY_ACTIVITY);
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                    VIRTUAL_DISPLAY_ACTIVITY);
        }
    }

    /**
     * Tests that input events on the primary display take focus from the virtual display.
     */
    @Test
    public void testStackFocusSwitchOnTouchEvent() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            mAmWmState.computeState(new WaitForValidActivityState(VIRTUAL_DISPLAY_ACTIVITY));
            mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                    VIRTUAL_DISPLAY_ACTIVITY);

            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);

            mAmWmState.computeState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));
            mAmWmState.assertFocusedActivity(
                    "Activity launched on secondary display must be focused",
                    TEST_ACTIVITY_NAME);

            final ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
            final int width = displayMetrics.getSize().getWidth();
            final int height = displayMetrics.getSize().getHeight();
            executeShellCommand("input tap " + (width / 2) + " " + (height / 2));

            mAmWmState.computeState(new WaitForValidActivityState(VIRTUAL_DISPLAY_ACTIVITY));
            mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                    VIRTUAL_DISPLAY_ACTIVITY);
        }
    }

    /** Test that shell is allowed to launch on secondary displays. */
    @Test
    public void testPermissionLaunchFromShell() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityManagerState.ActivityStack focusedStack = mAmWmState.getAmState().getStackById(
                    defaultDisplayFocusedStackId);
            assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                    focusedStack.mDisplayId);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                    TEST_ACTIVITY_NAME);
            final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            focusedStack = mAmWmState.getAmState().getStackById(externalFocusedStackId);
            assertEquals("Focused stack must be on secondary display", newDisplay.mId,
                    focusedStack.mDisplayId);

            // Launch other activity with different uid and check it is launched on dynamic stack on
            // secondary display.
            final String startCmd = "am start -n " + SECOND_ACTIVITY.flattenToShortString()
                            + " --display " + newDisplay.mId;
            executeShellCommand(startCmd);

            mAmWmState.waitForValidState(new WaitForValidActivityState(SECOND_ACTIVITY));
            mAmWmState.assertFocusedActivity(
                    "Focus must be on newly launched app", SECOND_ACTIVITY);
            assertEquals("Activity launched by system must be on external display",
                    externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
        }
    }

    /** Test that launching from app that is on external display is allowed. */
    @Test
    public void testPermissionLaunchFromAppOnSecondary() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity with different uid on secondary display.
            final String startCmd = "am start -n " + SECOND_ACTIVITY.flattenToShortString()
                    + " --display " + newDisplay.mId;
            executeShellCommand(startCmd);

            mAmWmState.waitForValidState(new WaitForValidActivityState(SECOND_ACTIVITY));
            mAmWmState.assertFocusedActivity(
                    "Focus must be on newly launched app", SECOND_ACTIVITY);
            final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityManagerState.ActivityStack focusedStack
                    = mAmWmState.getAmState().getStackById(externalFocusedStackId);
            assertEquals("Focused stack must be on secondary display", newDisplay.mId,
                    focusedStack.mDisplayId);

            // Launch another activity with third different uid from app on secondary display and
            // check it is launched on secondary display.
            final String targetActivity =
                    " --es target_activity " + THIRD_ACTIVITY.getShortClassName()
                    + " --es package_name " + THIRD_ACTIVITY.getPackageName()
                    + " --ei display_id " + newDisplay.mId;
            final String includeStoppedPackagesFlag = " -f 0x00000020";
            executeShellCommand("am broadcast -a " + LAUNCH_BROADCAST_ACTION
                    + " -p " + LAUNCH_BROADCAST_RECEIVER.getPackageName()
                    + targetActivity + includeStoppedPackagesFlag);

            mAmWmState.waitForValidState(new WaitForValidActivityState(THIRD_ACTIVITY));
            mAmWmState.assertFocusedActivity("Focus must be on newly launched app", THIRD_ACTIVITY);
            assertEquals("Activity launched by app on secondary display must be on that display",
                    externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
        }
    }

    /** Tests that an activity can launch an activity from a different UID into its own task. */
    @Test
    public void testPermissionLaunchMultiUidTask() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(LAUNCHING_ACTIVITY));

            // Check that the first activity is launched onto the secondary display
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            ActivityManagerState.ActivityStack frontStack = mAmWmState.getAmState().getStackById(
                    frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityComponentName(LAUNCHING_ACTIVITY),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            // Launch an activity from a different UID into the first activity's task
            getLaunchActivityBuilder().setTargetActivity(SECOND_ACTIVITY).execute();

            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);
            frontStack = mAmWmState.getAmState().getStackById(frontStackId);
            mAmWmState.assertFocusedActivity(
                    "Focus must be on newly launched app", SECOND_ACTIVITY);
            assertEquals("Secondary display must contain 1 task", 1, frontStack.getTasks().size());
        }
    }

    /**
     * Test that launching from display owner is allowed even when the the display owner
     * doesn't have anything on the display.
     */
    @Test
    public void testPermissionLaunchFromOwner() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityManagerState.ActivityStack focusedStack
                    = mAmWmState.getAmState().getStackById(defaultDisplayFocusedStackId);
            assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                    focusedStack.mDisplayId);

            // Launch other activity with different uid on secondary display.
            final String startCmd = "am start -n " + SECOND_ACTIVITY.flattenToShortString()
                    + " --display " + newDisplay.mId;
            executeShellCommand(startCmd);

            mAmWmState.waitForValidState(new WaitForValidActivityState(SECOND_ACTIVITY));
            mAmWmState.assertFocusedActivity(
                    "Focus must be on newly launched app", SECOND_ACTIVITY);
            final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            focusedStack = mAmWmState.getAmState().getStackById(externalFocusedStackId);
            assertEquals("Focused stack must be on secondary display", newDisplay.mId,
                    focusedStack.mDisplayId);

            // Check that owner uid can launch its own activity on secondary display.
            final String broadcastAction = componentName + ".LAUNCH_BROADCAST_ACTION";
            executeShellCommand("am broadcast -a " + broadcastAction + " -p " + componentName
                    + " --ez launch_activity true --ez new_task true --ez multiple_task true"
                    + " --ei display_id " + newDisplay.mId);

            mAmWmState.waitForValidState(TEST_ACTIVITY_NAME);
            mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                    TEST_ACTIVITY_NAME);
            assertEquals("Activity launched by owner must be on external display",
                    externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
        }
    }

    /**
     * Test that launching from app that is not present on external display and doesn't own it to
     * that external display is not allowed.
     */
    @Test
    public void testPermissionLaunchFromDifferentApp() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityManagerState.ActivityStack focusedStack = mAmWmState.getAmState().getStackById(
                    defaultDisplayFocusedStackId);
            assertEquals("Focus must remain on primary display", DEFAULT_DISPLAY_ID,
                    focusedStack.mDisplayId);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                    TEST_ACTIVITY_NAME);
            final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            focusedStack = mAmWmState.getAmState().getStackById(externalFocusedStackId);
            assertEquals("Focused stack must be on secondary display", newDisplay.mId,
                    focusedStack.mDisplayId);

            final String logSeparator = clearLogcat();

            // Launch other activity with different uid and check security exception is triggered.
            final String includeStoppedPackagesFlag = " -f 0x00000020";
            executeShellCommand("am broadcast -a " + LAUNCH_BROADCAST_ACTION
                    + " -p " + LAUNCH_BROADCAST_RECEIVER.getPackageName()
                    + " --ei display_id " + newDisplay.mId
                    + includeStoppedPackagesFlag);

            assertSecurityException("LaunchBroadcastReceiver", logSeparator);

            mAmWmState.waitForValidState(false /* compareTaskAndStackBounds */, componentName,
                    new WaitForValidActivityState(TEST_ACTIVITY_NAME));
            mAmWmState.assertFocusedActivity("Focus must be on first activity", TEST_ACTIVITY_NAME);
            assertEquals("Focused stack must be on secondary display's stack",
                    externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
        }
    }

    private void assertSecurityException(String component, String logSeparator) throws Exception {
        int tries = 0;
        boolean match = false;
        final Pattern pattern = Pattern.compile(".*SecurityException launching activity.*");
        while (tries < 5 && !match) {
            String[] logs = getDeviceLogsForComponent(component, logSeparator);
            for (String line : logs) {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    match = true;
                    break;
                }
            }
            tries++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }

        assertTrue("Expected exception not found", match);
    }

    /**
     * Test that only private virtual display can show content with insecure keyguard.
     */
    @Test
    public void testFlagShowWithInsecureKeyguardOnPublicVirtualDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Try to create new show-with-insecure-keyguard public virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setPublicDisplay(true)
                    .setCanShowWithInsecureKeyguard(true)
                    .setMustBeCreated(false)
                    .createDisplay();

            // Check that the display is not created.
            assertNull(newDisplay);
        }
    }

    /**
     * Test that all activities that were on the private display are destroyed on display removal.
     */
    @FlakyTest(bugId = 63404575)
    @Presubmit
    @Test
    public void testContentDestroyOnDisplayRemoved() throws Exception {
        String logSeparator;
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new private virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activities on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
            mAmWmState.assertFocusedActivity("Launched activity must be focused",
                    TEST_ACTIVITY_NAME);
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
            mAmWmState.assertFocusedActivity("Launched activity must be focused",
                    RESIZEABLE_ACTIVITY_NAME);

            // Destroy the display and check if activities are removed from system.
            logSeparator = clearLogcat();
        }

        final String activityName1 = ActivityManagerTestBase.getActivityComponentName(
                TEST_ACTIVITY_NAME);
        final String activityName2 = ActivityManagerTestBase.getActivityComponentName(
                RESIZEABLE_ACTIVITY_NAME);
        final String windowName1 = ActivityManagerTestBase.getWindowName(TEST_ACTIVITY_NAME);
        final String windowName2 = ActivityManagerTestBase.getWindowName(RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.waitForWithAmState(
                (state) -> !state.containsActivity(activityName1)
                        && !state.containsActivity(activityName2),
                "Waiting for activity to be removed");
        mAmWmState.waitForWithWmState(
                (state) -> !state.containsWindow(windowName1)
                        && !state.containsWindow(windowName2),
                "Waiting for activity window to be gone");

        // Check AM state.
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(activityName1));
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(activityName2));
        // Check WM state.
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(windowName1));
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(windowName2));
        // Check activity logs.
        assertActivityDestroyed(TEST_ACTIVITY_NAME, logSeparator);
        assertActivityDestroyed(RESIZEABLE_ACTIVITY_NAME, logSeparator);
    }

    /**
     * Test that the update of display metrics updates all its content.
     */
    @Presubmit
    @Test
    public void testDisplayResize() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch a resizeable activity on new secondary display.
            final String initialLogSeparator = clearLogcat();
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
            mAmWmState.assertFocusedActivity("Launched activity must be focused",
                    RESIZEABLE_ACTIVITY_NAME);

            // Grab reported sizes and compute new with slight size change.
            final ReportedSizes initialSize = getLastReportedSizesForActivity(
                    RESIZEABLE_ACTIVITY_NAME,
                    initialLogSeparator);

            // Resize the docked stack, so that activity with virtual display will also be resized.
            final String logSeparator = clearLogcat();
            executeShellCommand(getResizeVirtualDisplayCommand());

            mAmWmState.waitForWithAmState(amState -> {
                try {
                    return readConfigChangeNumber(RESIZEABLE_ACTIVITY_NAME, logSeparator) == 1
                            && amState.hasActivityState(RESIZEABLE_ACTIVITY_NAME, STATE_RESUMED);
                } catch (Exception e) {
                    logE("Error waiting for valid state: " + e.getMessage());
                    return false;
                }
            }, "Wait for the configuration change to happen and for activity to be resumed.");

            mAmWmState.computeState(false /* compareTaskAndStackBounds */,
                    new WaitForValidActivityState(RESIZEABLE_ACTIVITY_NAME),
                    new WaitForValidActivityState(VIRTUAL_DISPLAY_ACTIVITY));
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true);

            // Check if activity in virtual display was resized properly.
            assertRelaunchOrConfigChanged(RESIZEABLE_ACTIVITY_NAME, 0 /* numRelaunch */,
                    1 /* numConfigChange */, logSeparator);

            final ReportedSizes updatedSize = getLastReportedSizesForActivity(
                    RESIZEABLE_ACTIVITY_NAME,
                    logSeparator);
            assertTrue(updatedSize.widthDp <= initialSize.widthDp);
            assertTrue(updatedSize.heightDp <= initialSize.heightDp);
            assertTrue(updatedSize.displayWidth == initialSize.displayWidth / 2);
            assertTrue(updatedSize.displayHeight == initialSize.displayHeight / 2);
        }
    }

    /** Read the number of configuration changes sent to activity from logs. */
    private int readConfigChangeNumber(String activityName, String logSeparator) throws Exception {
        return (new ActivityLifecycleCounts(activityName, logSeparator)).mConfigurationChangedCount;
    }

    /**
     * Tests that when an activity is launched with displayId specified and there is an existing
     * matching task on some other display - that task will moved to the target display.
     */
    @Test
    public void testMoveToDisplayOnLaunch() throws Exception {
        // Launch activity with unique affinity, so it will the only one in its task.
        launchActivity(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            // Launch something to that display so that a new stack is created. We need this to be
            // able to compare task numbers in stacks later.
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);

            final int stackNum = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY_ID)
                    .mStacks.size();
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final int taskNumOnSecondary = mAmWmState.getAmState().getStackById(frontStackId)
                    .getTasks().size();

            // Launch activity on new secondary display.
            // Using custom command here, because normally we add flags
            // {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
            // when launching on some specific display. We don't do it here as we want an existing
            // task to be used.
            final String launchCommand = "am start -n " + getActivityComponentName(
                    LAUNCHING_ACTIVITY)
                    + " --display " + newDisplay.mId;
            executeShellCommand(launchCommand);
            mAmWmState.waitForActivityState(LAUNCHING_ACTIVITY, STATE_RESUMED);

            // Check that activity is brought to front.
            mAmWmState.assertFocusedActivity("Existing task must be brought to front",
                    LAUNCHING_ACTIVITY);
            mAmWmState.assertResumedActivity("Existing task must be resumed", LAUNCHING_ACTIVITY);

            // Check that activity is on the right display.
            final ActivityManagerState.ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity must be moved to the secondary display",
                    getActivityComponentName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            // Check that task has moved from primary display to secondary.
            final int stackNumFinal = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY_ID)
                    .mStacks.size();
            assertEquals("Stack number in default stack must be decremented.", stackNum - 1,
                    stackNumFinal);
            final int taskNumFinalOnSecondary = mAmWmState.getAmState().getStackById(frontStackId)
                    .getTasks().size();
            assertEquals("Task number in stack on external display must be incremented.",
                    taskNumOnSecondary + 1, taskNumFinalOnSecondary);
        }
    }

    /**
     * Tests that when an activity is launched with displayId specified and there is an existing
     * matching task on some other display - that task will moved to the target display.
     */
    @Test
    public void testMoveToEmptyDisplayOnLaunch() throws Exception {
        // Launch activity with unique affinity, so it will the only one in its task.
        launchActivity(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            final int stackNum = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY_ID)
                    .mStacks.size();

            // Launch activity on new secondary display.
            // Using custom command here, because normally we add flags
            // {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
            // when launching on some specific display. We don't do it here as we want an existing
            // task to be used.
            final String launchCommand = "am start -n " + getActivityComponentName(
                    LAUNCHING_ACTIVITY)
                    + " --display " + newDisplay.mId;
            executeShellCommand(launchCommand);
            mAmWmState.waitForActivityState(LAUNCHING_ACTIVITY, STATE_RESUMED);

            // Check that activity is brought to front.
            mAmWmState.assertFocusedActivity("Existing task must be brought to front",
                    LAUNCHING_ACTIVITY);
            mAmWmState.assertResumedActivity("Existing task must be resumed", LAUNCHING_ACTIVITY);

            // Check that activity is on the right display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity must be moved to the secondary display",
                    getActivityComponentName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            // Check that task has moved from primary display to secondary.
            final int stackNumFinal = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY_ID)
                    .mStacks.size();
            assertEquals("Stack number in default stack must be decremented.", stackNum - 1,
                    stackNumFinal);
        }
    }

    /**
     * Tests that when primary display is rotated secondary displays are not affected.
     */
    @Test
    public void testRotationNotAffectingSecondaryScreen() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setResizeDisplay(false)
                    .createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activity on new secondary display.
            String logSeparator = clearLogcat();
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mId);
            mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                    RESIZEABLE_ACTIVITY_NAME);
            final ReportedSizes initialSizes = getLastReportedSizesForActivity(
                    RESIZEABLE_ACTIVITY_NAME, logSeparator);
            assertNotNull("Test activity must have reported initial sizes on launch", initialSizes);

            try (final RotationSession rotationSession = new RotationSession()) {
                // Rotate primary display and check that activity on secondary display is not
                // affected.

                rotateAndCheckSameSizes(rotationSession, RESIZEABLE_ACTIVITY_NAME);

                // Launch activity to secondary display when primary one is rotated.
                final int initialRotation = mAmWmState.getWmState().getRotation();
                rotationSession.set((initialRotation + 1) % 4);

                logSeparator = clearLogcat();
                launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);
                mAmWmState.waitForActivityState(TEST_ACTIVITY_NAME, STATE_RESUMED);
                mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                        TEST_ACTIVITY_NAME);
                final ReportedSizes testActivitySizes = getLastReportedSizesForActivity(
                        TEST_ACTIVITY_NAME, logSeparator);
                assertEquals(
                        "Sizes of secondary display must not change after rotation of primary "
                                + "display",
                        initialSizes, testActivitySizes);
            }
        }
    }

    private void rotateAndCheckSameSizes(RotationSession rotationSession, String activityName)
            throws Exception {
        for (int rotation = 3; rotation >= 0; --rotation) {
            final String logSeparator = clearLogcat();
            rotationSession.set(rotation);
            final ReportedSizes rotatedSizes = getLastReportedSizesForActivity(activityName,
                    logSeparator);
            assertNull("Sizes must not change after rotation", rotatedSizes);
        }
    }

    /**
     * Tests that task affinity does affect what display an activity is launched on but that
     * matching the task component root does.
     */
    @Test
    public void testTaskMatchAcrossDisplays() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(LAUNCHING_ACTIVITY));

            // Check that activity is on the secondary display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityComponentName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            executeShellCommand("am start -n " + getActivityComponentName(ALT_LAUNCHING_ACTIVITY));
            mAmWmState.waitForValidState(false /* compareTaskAndStackBounds */, componentName,
                    new WaitForValidActivityState(ALT_LAUNCHING_ACTIVITY));

            // Check that second activity gets launched on the default display despite
            // the affinity match on the secondary display.
            final int defaultDisplayFrontStackId = mAmWmState.getAmState().getFrontStackId(
                    DEFAULT_DISPLAY_ID);
            final ActivityManagerState.ActivityStack defaultDisplayFrontStack =
                    mAmWmState.getAmState().getStackById(defaultDisplayFrontStackId);
            assertEquals("Activity launched on default display must be resumed",
                    getActivityComponentName(ALT_LAUNCHING_ACTIVITY),
                    defaultDisplayFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on primary display",
                    defaultDisplayFrontStackId);

            executeShellCommand("am start -n " + getActivityComponentName(LAUNCHING_ACTIVITY));
            mAmWmState.waitForFocusedStack(frontStackId);

            // Check that the third intent is redirected to the first task due to the root
            // component match on the secondary display.
            final ActivityManagerState.ActivityStack secondFrontStack
                    = mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityComponentName(LAUNCHING_ACTIVITY),
                    secondFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on primary display", frontStackId);
            assertEquals("Focused stack must only contain 1 task",
                    1, secondFrontStack.getTasks().size());
            assertEquals("Focused task must only contain 1 activity",
                    1, secondFrontStack.getTasks().get(0).mActivities.size());
        }
    }

    /**
     * Tests that the task affinity search respects the launch display id.
     */
    @Test
    public void testLaunchDisplayAffinityMatch() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

            // Check that activity is on the secondary display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityComponentName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            // We don't want FLAG_ACTIVITY_MULTIPLE_TASK, so we can't use launchActivityOnDisplay
            executeShellCommand("am start -n "
                    + getActivityComponentName(ALT_LAUNCHING_ACTIVITY)
                    + " -f 0x10000000" // FLAG_ACTIVITY_NEW_TASK
                    + " --display " + newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(ALT_LAUNCHING_ACTIVITY));

            // Check that second activity gets launched into the affinity matching
            // task on the secondary display
            final int secondFrontStackId =
                    mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack secondFrontStack =
                    mAmWmState.getAmState().getStackById(secondFrontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityComponentName(ALT_LAUNCHING_ACTIVITY),
                    secondFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display",
                    secondFrontStackId);
            assertEquals("Focused stack must only contain 1 task",
                    1, secondFrontStack.getTasks().size());
            assertEquals("Focused task must contain 2 activities",
                    2, secondFrontStack.getTasks().get(0).mActivities.size());
        }
    }

    /**
     * Tests than a new task launched by an activity will end up on that activity's display
     * even if the focused stack is not on that activity's display.
     */
    @Test
    public void testNewTaskSameDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(new WaitForValidActivityState(BROADCAST_RECEIVER_ACTIVITY));

            // Check that the first activity is launched onto the secondary display
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityComponentName(BROADCAST_RECEIVER_ACTIVITY),
                    firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            executeShellCommand("am start -n " + getActivityComponentName(TEST_ACTIVITY_NAME));
            mAmWmState.waitForValidState(false /* compareTaskAndStackBounds */, componentName,
                    new WaitForValidActivityState(TEST_ACTIVITY_NAME));

            // Check that the second activity is launched on the default display
            final int focusedStackId = mAmWmState.getAmState().getFocusedStackId();
            final ActivityManagerState.ActivityStack focusedStack
                    = mAmWmState.getAmState().getStackById(focusedStackId);
            assertEquals("Activity launched on default display must be resumed",
                    getActivityComponentName(TEST_ACTIVITY_NAME), focusedStack.mResumedActivity);
            assertEquals("Focus must be on primary display", DEFAULT_DISPLAY_ID,
                    focusedStack.mDisplayId);

            executeShellCommand("am broadcast -a trigger_broadcast --ez launch_activity true "
                    + "--ez new_task true --es target_activity " + LAUNCHING_ACTIVITY);

            // Check that the third activity ends up in a new task in the same stack as the
            // first activity
            mAmWmState.waitForValidState(false /* compareTaskAndStackBounds */, componentName,
                    new WaitForValidActivityState(LAUNCHING_ACTIVITY));
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);
            final ActivityManagerState.ActivityStack secondFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity must be launched on secondary display",
                    getActivityComponentName(LAUNCHING_ACTIVITY),
                    secondFrontStack.mResumedActivity);
            assertEquals("Secondary display must contain 2 tasks",
                    2, secondFrontStack.getTasks().size());
        }
    }

    /**
     * Tests than an immediate launch after new display creation is handled correctly.
     */
    @Test
    public void testImmediateLaunchOnNewDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display and immediately launch an activity on it.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setLaunchActivity(TEST_ACTIVITY_NAME)
                    .createDisplay();

            // Check that activity is launched and placed correctly.
            mAmWmState.waitForActivityState(TEST_ACTIVITY_NAME, STATE_RESUMED);
            mAmWmState.assertResumedActivity("Test activity must be launched on a new display",
                    TEST_ACTIVITY_NAME);
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityManagerState.ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityComponentName(TEST_ACTIVITY_NAME), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);
        }
    }

    /**
     * Tests that turning the primary display off does not affect the activity running
     * on an external secondary display.
     */
    @Test
    public void testExternalDisplayActivityTurnPrimaryOff() throws Exception {
        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY_NAME);
        waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                "Activity launched on primary display must be resumed");

        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession();
             final PrimaryDisplayStateSession displayStateSession =
                     new PrimaryDisplayStateSession()) {
            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(true /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);

            // Check that the activity is launched onto the external display
            waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mId,
                    "Activity launched on external display must be resumed");

            displayStateSession.turnScreenOff();

            // Wait for the fullscreen stack to start sleeping, and then make sure the
            // test activity is still resumed.
            waitAndAssertActivityStopped(RESIZEABLE_ACTIVITY_NAME,
                    "Activity launched on primary display must be stopped after turning off");
            waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mId,
                    "Activity launched on external display must be resumed");
        }
    }

    /**
     * Tests that an activity can be launched on a secondary display while the primary
     * display is off.
     */
    @Test
    public void testLaunchExternalDisplayActivityWhilePrimaryOff() throws Exception {
        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY_NAME);
        waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                "Activity launched on primary display must be resumed");

        try (final PrimaryDisplayStateSession displayStateSession =
                     new PrimaryDisplayStateSession();
             final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            displayStateSession.turnScreenOff();

            // Make sure there is no resumed activity when the primary display is off
            waitAndAssertActivityStopped(RESIZEABLE_ACTIVITY_NAME,
                    "Activity launched on primary display must be stopped after turning off");
            assertEquals("Unexpected resumed activity",
                    0, mAmWmState.getAmState().getResumedActivitiesCount());

            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(true /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);

            // Check that the test activity is resumed on the external display
            waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mId,
                    "Activity launched on external display must be resumed");
        }
    }

    /**
     * Tests that turning the secondary display off stops activities running on that display.
     */
    @Test
    public void testExternalDisplayToggleState() throws Exception {
        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(false /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);

            // Check that the test activity is resumed on the external display
            waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mId,
                    "Activity launched on external display must be resumed");

            externalDisplaySession.turnDisplayOff();

            // Check that turning off the external display stops the activity
            waitAndAssertActivityStopped(TEST_ACTIVITY_NAME,
                    "Activity launched on external display must be stopped after turning off");

            externalDisplaySession.turnDisplayOn();

            // Check that turning on the external display resumes the activity
            waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mId,
                    "Activity launched on external display must be resumed");
        }
    }

    /**
     * Tests that tapping on the primary display after showing the keyguard resumes the
     * activity on the primary display.
     */
    @Test
    public void testStackFocusSwitchOnTouchEventAfterKeyguard() throws Exception {
        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY_NAME);
        waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                "Activity launched on primary display must be resumed");

        sleepDevice();

        // Make sure there is no resumed activity when the primary display is off
        waitAndAssertActivityStopped(RESIZEABLE_ACTIVITY_NAME,
                "Activity launched on primary display must be stopped after turning off");
        assertEquals("Unexpected resumed activity",
                0, mAmWmState.getAmState().getResumedActivitiesCount());

        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(true /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mId);

            // Check that the test activity is resumed on the external display
            waitAndAssertActivityResumed(TEST_ACTIVITY_NAME, newDisplay.mId,
                    "Activity launched on external display must be resumed");

            // Unlock the device and tap on the middle of the primary display
            wakeUpDevice();
            executeShellCommand("wm dismiss-keyguard");
            mAmWmState.waitForKeyguardGone();
            mAmWmState.waitForValidState(new WaitForValidActivityState(TEST_ACTIVITY_NAME));
            final ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
            final int width = displayMetrics.getSize().getWidth();
            final int height = displayMetrics.getSize().getHeight();
            executeShellCommand("input tap " + (width / 2) + " " + (height / 2));

            // Check that the activity on the primary display is resumed
            waitAndAssertActivityResumed(RESIZEABLE_ACTIVITY_NAME, DEFAULT_DISPLAY_ID,
                    "Activity launched on primary display must be resumed");
            assertEquals("Unexpected resumed activity",
                    1, mAmWmState.getAmState().getResumedActivitiesCount());
        }
    }

    private void waitAndAssertActivityResumed(String activityName, int displayId, String message)
            throws Exception {
        mAmWmState.waitForActivityState(activityName, STATE_RESUMED);

        final String fullActivityName = getActivityComponentName(activityName);
        assertEquals(message, fullActivityName, mAmWmState.getAmState().getResumedActivity());
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(displayId);
        ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals(message, fullActivityName, firstFrontStack.mResumedActivity);
        assertTrue(message,
                mAmWmState.getAmState().hasActivityState(activityName, STATE_RESUMED));
        mAmWmState.assertFocusedStack("Focus must be on external display", frontStackId);
        mAmWmState.assertVisibility(activityName, true /* visible */);
    }

    private void waitAndAssertActivityStopped(String activityName, String message)
            throws Exception {
        mAmWmState.waitForActivityState(activityName, STATE_STOPPED);

        assertTrue(message, mAmWmState.getAmState().hasActivityState(activityName,
                STATE_STOPPED));
    }

    /**
     * Tests that showWhenLocked works on a secondary display.
     */
    public void testSecondaryDisplayShowWhenLocked() throws Exception {
        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession();
             final LockCredentialSession lockCredentialSession = new LockCredentialSession()) {
            lockCredentialSession.setLockCredential();

            launchActivity(TEST_ACTIVITY_NAME);

            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(false /* showContentWhenLocked */);
            launchActivityOnDisplay(SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME, newDisplay.mId);

            gotoKeyguard();
            mAmWmState.waitForKeyguardShowingAndNotOccluded();

            mAmWmState.waitForActivityState(TEST_ACTIVITY_NAME, STATE_STOPPED);
            mAmWmState.waitForActivityState(SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME, STATE_RESUMED);

            mAmWmState.computeState(SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME);
            assertTrue("Expected resumed activity on secondary display", mAmWmState.getAmState()
                    .hasActivityState(SHOW_WHEN_LOCKED_ATTR_ACTIVITY_NAME, STATE_RESUMED));
        }
    }

    /** Assert that component received onMovedToDisplay and onConfigurationChanged callbacks. */
    private void assertMovedToDisplay(String componentName, String logSeparator) throws Exception {
        final ActivityLifecycleCounts lifecycleCounts
                = new ActivityLifecycleCounts(componentName, logSeparator);
        if (lifecycleCounts.mDestroyCount != 0) {
            fail(componentName + " has been destroyed " + lifecycleCounts.mDestroyCount
                    + " time(s), wasn't expecting any");
        } else if (lifecycleCounts.mCreateCount != 0) {
            fail(componentName + " has been (re)created " + lifecycleCounts.mCreateCount
                    + " time(s), wasn't expecting any");
        } else if (lifecycleCounts.mConfigurationChangedCount != 1) {
            fail(componentName + " has received "
                    + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, expecting " + 1);
        } else if (lifecycleCounts.mMovedToDisplayCount != 1) {
            fail(componentName + " has received "
                    + lifecycleCounts.mMovedToDisplayCount
                    + " onMovedToDisplay() calls, expecting " + 1);
        }
    }

    private static String getResizeVirtualDisplayCommand() {
        return getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY) + " -f 0x20000000" +
                " --es command resize_display";
    }

    private class ExternalDisplaySession implements AutoCloseable {

        @Nullable
        private DisplayHelper mExternalDisplayHelper;

        /**
         * Creates a private virtual display with the external and show with insecure
         * keyguard flags set.
         */
        ActivityDisplay createVirtualDisplay(boolean showContentWhenLocked)
                throws Exception {
            final List<ActivityDisplay> originalDS = getDisplaysStates();
            final int originalDisplayCount = originalDS.size();

            mExternalDisplayHelper = new DisplayHelper();
            mExternalDisplayHelper.createAndWaitForDisplay(true /* external */,
                    showContentWhenLocked);

            // Wait for the virtual display to be created and get configurations.
            final List<ActivityDisplay> ds = getDisplayStateAfterChange(originalDisplayCount + 1);
            assertEquals("New virtual display must be created", originalDisplayCount + 1,
                    ds.size());

            // Find the newly added display.
            final List<ActivityDisplay> newDisplays = findNewDisplayStates(originalDS, ds);
            return newDisplays.get(0);
        }

        void turnDisplayOff() {
            if (mExternalDisplayHelper == null) {
                new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOff();
        }

        void turnDisplayOn() {
            if (mExternalDisplayHelper == null) {
                new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOn();
        }

        @Override
        public void close() throws Exception {
            if (mExternalDisplayHelper != null) {
                mExternalDisplayHelper.releaseDisplay();
                mExternalDisplayHelper = null;
            }
        }
    }

    private static class PrimaryDisplayStateSession implements AutoCloseable {

        void turnScreenOff() {
            setPrimaryDisplayState(false);
        }

        @Override
        public void close() throws Exception {
            setPrimaryDisplayState(true);
        }

        /** Turns the primary display on/off by pressing the power key */
        private void setPrimaryDisplayState(boolean wantOn) {
            // Either KeyEvent.KEYCODE_WAKEUP or KeyEvent.KEYCODE_SLEEP
            int keycode = wantOn ? 224 : 223;
            executeShellCommand("input keyevent " + keycode);
            DisplayHelper.waitForDefaultDisplayState(wantOn);
        }
    }
}