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

package android.server.am;

import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.server.am.StateLogger.log;

import static org.junit.Assume.assumeTrue;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.ActivityManagerReplaceWindowTests
 */
public class ActivityManagerReplaceWindowTests extends ActivityManagerTestBase {

    private static final String SLOW_CREATE_ACTIVITY_NAME = "SlowCreateActivity";
    private static final String NO_RELAUNCH_ACTIVITY_NAME = "NoRelaunchActivity";

    private List<String> mTempWindowTokens = new ArrayList();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue("Skipping test: no multi-window support", supportsSplitScreenMultiWindow());
    }

    @Test
    public void testReplaceWindow_Dock_Relaunch() throws Exception {
        testReplaceWindow_Dock(true);
    }

    @Test
    public void testReplaceWindow_Dock_NoRelaunch() throws Exception {
        testReplaceWindow_Dock(false);
    }

    private void testReplaceWindow_Dock(boolean relaunch) throws Exception {
        final String activityName =
                relaunch ? SLOW_CREATE_ACTIVITY_NAME : NO_RELAUNCH_ACTIVITY_NAME;
        final String windowName = getWindowName(activityName);
        final String amStartCmd = getAmStartCmd(activityName);

        executeShellCommand(amStartCmd);

        // Sleep 2 seconds, then check if the window is started properly.
        // SlowCreateActivity will do a sleep inside its onCreate() to simulate a
        // slow-starting app. So instead of relying on WindowManagerState's
        // retrying mechanism, we do an explicit sleep to avoid excess spews
        // from WindowManagerState.
        if (SLOW_CREATE_ACTIVITY_NAME.equals(activityName)) {
            Thread.sleep(2000);
        }

        log("==========Before Docking========");
        final String oldToken = getWindowToken(windowName, activityName);

        // Move to docked stack
        setActivityTaskWindowingMode(activityName, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);

        // Sleep 5 seconds, then check if the window is replaced properly.
        Thread.sleep(5000);

        log("==========After Docking========");
        final String newToken = getWindowToken(windowName, activityName);

        // For both relaunch and not relaunch case, we'd like the window to be kept.
        Assert.assertEquals("Window replaced while docking.", oldToken, newToken);
    }

    private String getWindowToken(String windowName, String activityName)
            throws Exception {
        mAmWmState.computeState(new WaitForValidActivityState.Builder(activityName).build());

        mAmWmState.assertVisibility(activityName, true);

        mAmWmState.getWmState().getMatchingWindowTokens(windowName, mTempWindowTokens);

        Assert.assertEquals("Should have exactly one window for the activity.",
                1, mTempWindowTokens.size());

        return mTempWindowTokens.get(0);
    }
}
