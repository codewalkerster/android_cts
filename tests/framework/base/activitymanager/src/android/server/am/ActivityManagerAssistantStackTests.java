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
 * limitations under the License
 */

package android.server.am;

import static android.app.ActivityManager.StackId.ASSISTANT_STACK_ID;
import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FULLSCREEN_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import static android.server.am.ActivityManagerState.STATE_RESUMED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.ActivityManagerAssistantStackTests
 */
public class ActivityManagerAssistantStackTests extends ActivityManagerTestBase {

    private static final String VOICE_INTERACTION_SERVICE = "AssistantVoiceInteractionService";

    private static final String TEST_ACTIVITY = "TestActivity";
    private static final String DOCKED_ACTIVITY = "DockedActivity";
    private static final String ASSISTANT_ACTIVITY = "AssistantActivity";
    private static final String TRANSLUCENT_ASSISTANT_ACTIVITY = "TranslucentAssistantActivity";
    private static final String LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION =
            "LaunchAssistantActivityFromSession";
    private static final String LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK =
            "LaunchAssistantActivityIntoAssistantStack";
    private static final String PIP_ACTIVITY = "PipActivity";

    private static final String EXTRA_ENTER_PIP = "enter_pip";
    private static final String EXTRA_LAUNCH_NEW_TASK = "launch_new_task";
    private static final String EXTRA_FINISH_SELF = "finish_self";
    public static final String EXTRA_IS_TRANSLUCENT = "is_translucent";

    private static final String TEST_ACTIVITY_ACTION_FINISH_SELF =
            "android.server.am.TestActivity.finish_self";

    @Test
    public void testLaunchingAssistantActivityIntoAssistantStack() throws Exception {
        // Enable the assistant and launch an assistant activity
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        mAmWmState.waitForValidState(ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);

        // Ensure that the activity launched in the fullscreen assistant stack
        assertAssistantStackExists();
        assertTrue("Expected assistant stack to be fullscreen",
                mAmWmState.getAmState().getStackById(ASSISTANT_STACK_ID).isFullscreen());

        disableAssistant();
    }

    @Test
    public void testAssistantStackZOrder() throws Exception {
        // Launch a pinned stack task
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        mAmWmState.waitForValidState(PIP_ACTIVITY, PINNED_STACK_ID);
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);

        // Dock a task
        launchActivity(TEST_ACTIVITY);
        launchActivityInDockStack(DOCKED_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        // Enable the assistant and launch an assistant activity, ensure it is on top
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        mAmWmState.waitForValidState(ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();

        mAmWmState.assertFrontStack("Pinned stack should be on top.", PINNED_STACK_ID);
        mAmWmState.assertFocusedStack("Assistant stack should be focused.", ASSISTANT_STACK_ID);

        disableAssistant();
    }

    @Test
    public void testAssistantStackLaunchNewTask() throws Exception {
        enableAssistant();
        assertAssistantStackCanLaunchAndReturnFromNewTask();
        disableAssistant();
    }

    @Test
    public void testAssistantStackLaunchNewTaskWithDockedStack() throws Exception {
        // Dock a task
        launchActivity(TEST_ACTIVITY);
        launchActivityInDockStack(DOCKED_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        enableAssistant();
        assertAssistantStackCanLaunchAndReturnFromNewTask();
        disableAssistant();
    }

    private void assertAssistantStackCanLaunchAndReturnFromNewTask() throws Exception {
        // Enable the assistant and launch an assistant activity which will launch a new task
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_LAUNCH_NEW_TASK, TEST_ACTIVITY);
        disableAssistant();

        // Ensure that the fullscreen stack is on top and the test activity is now visible
        mAmWmState.waitForValidState(TEST_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedActivity("TestActivity should be resumed", TEST_ACTIVITY);
        mAmWmState.assertFrontStack("Fullscreen stack should be on top.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedStack("Fullscreen stack should be focused.",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Now, tell it to finish itself and ensure that the assistant stack is brought back forward
        executeShellCommand("am broadcast -a " + TEST_ACTIVITY_ACTION_FINISH_SELF);
        mAmWmState.waitForFocusedStack(ASSISTANT_STACK_ID);
        mAmWmState.assertFrontStack("Assistant stack should be on top.", ASSISTANT_STACK_ID);
        mAmWmState.assertFocusedStack("Assistant stack should be focused.", ASSISTANT_STACK_ID);
    }

    @Test
    public void testAssistantStackFinishToPreviousApp() throws Exception {
        // Launch an assistant activity on top of an existing fullscreen activity, and ensure that
        // the fullscreen activity is still visible and on top after the assistant activity finishes
        launchActivity(TEST_ACTIVITY);
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_FINISH_SELF, "true");
        disableAssistant();
        mAmWmState.waitForValidState(TEST_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.waitForActivityState(TEST_ACTIVITY, STATE_RESUMED);
        mAmWmState.assertFocusedActivity("TestActivity should be resumed", TEST_ACTIVITY);
        mAmWmState.assertFrontStack("Fullscreen stack should be on top.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedStack("Fullscreen stack should be focused.",
                FULLSCREEN_WORKSPACE_STACK_ID);
    }

    @Test
    public void testDisallowEnterPiPFromAssistantStack() throws Exception {
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_ENTER_PIP, "true");
        disableAssistant();
        mAmWmState.waitForValidState(ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        mAmWmState.assertDoesNotContainStack("Must not contain pinned stack.", PINNED_STACK_ID);
    }

    @Test
    public void testTranslucentAssistantActivityStackVisibility() throws Exception {
        enableAssistant();
        // Go home, launch the assistant and check to see that home is visible
        removeStacks(FULLSCREEN_WORKSPACE_STACK_ID);
        launchHomeActivity();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidState(TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertHomeActivityVisible(true);

        // Launch a fullscreen app and then launch the assistant and check to see that it is
        // also visible
        removeStacks(ASSISTANT_STACK_ID);
        launchActivity(TEST_ACTIVITY);
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidState(TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        // Go home, launch assistant, launch app into fullscreen with activity present, and go back.
        // Ensure home is visible.
        removeStacks(ASSISTANT_STACK_ID);
        launchHomeActivity();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true), EXTRA_LAUNCH_NEW_TASK,
                TEST_ACTIVITY);
        mAmWmState.waitForValidState(TEST_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertHomeActivityVisible(false);
        pressBackButton();
        mAmWmState.waitForFocusedStack(ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertHomeActivityVisible(true);

        // Launch a fullscreen and docked app and then launch the assistant and check to see that it
        // is also visible
        removeStacks(ASSISTANT_STACK_ID);
        launchActivityInDockStack(DOCKED_ACTIVITY);
        launchActivity(TEST_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidState(TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(DOCKED_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        disableAssistant();
    }

    @Test
    public void testLaunchIntoSameTask() throws Exception {
        enableAssistant();

        // Launch the assistant
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
        mAmWmState.assertFocusedStack("Expected assistant stack focused", ASSISTANT_STACK_ID);
        assertEquals(1, mAmWmState.getAmState().getStackById(ASSISTANT_STACK_ID).getTasks().size());
        final int taskId = mAmWmState.getAmState().getTaskByActivityName(ASSISTANT_ACTIVITY)
                .mTaskId;

        // Launch a new fullscreen activity
        launchActivity(TEST_ACTIVITY);
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, false);

        // Launch the assistant again and ensure that it goes into the same task
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
        mAmWmState.assertFocusedStack("Expected assistant stack focused", ASSISTANT_STACK_ID);
        assertEquals(1, mAmWmState.getAmState().getStackById(ASSISTANT_STACK_ID).getTasks().size());
        assertEquals(taskId,
                mAmWmState.getAmState().getTaskByActivityName(ASSISTANT_ACTIVITY).mTaskId);

        disableAssistant();
    }

    @Test
    public void testPinnedStackWithAssistant() throws Exception {
        if (!supportsPip() || !supportsSplitScreenMultiWindow()) return;

        enableAssistant();

        // Launch a fullscreen activity and a PIP activity, then launch the assistant, and ensure
        // that the test activity is still visible
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidState(TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(TRANSLUCENT_ASSISTANT_ACTIVITY, true);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        disableAssistant();
    }

    /**
     * Asserts that the assistant stack exists.
     */
    private void assertAssistantStackExists() throws Exception {
        mAmWmState.assertContainsStack("Must contain assistant stack.", ASSISTANT_STACK_ID);
    }

    /**
     * Asserts that the assistant stack does not exist.
     */
    private void assertAssistantStackDoesNotExist() throws Exception {
        mAmWmState.assertDoesNotContainStack("Must not contain assistant stack.",
                ASSISTANT_STACK_ID);
    }

    /**
     * Sets the system voice interaction service.
     */
    private void enableAssistant() throws Exception {
        executeShellCommand("settings put secure voice_interaction_service " +
                getActivityComponentName(VOICE_INTERACTION_SERVICE));
    }

    /**
     * Resets the system voice interaction service.
     */
    private void disableAssistant() throws Exception {
        executeShellCommand("settings delete secure voice_interaction_service " +
                getActivityComponentName(VOICE_INTERACTION_SERVICE));
    }
}
