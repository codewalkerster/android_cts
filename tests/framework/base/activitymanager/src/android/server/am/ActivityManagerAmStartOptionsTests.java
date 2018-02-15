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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerAmStartOptionsTests
 */
public class ActivityManagerAmStartOptionsTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String ENTRYPOINT_ACTIVITY_NAME = "EntryPointAliasActivity";
    private static final String SINGLE_TASK_ACTIVITY_NAME = "SingleTaskActivity";

    @Test
    public void testDashD() throws Exception {
        final String activityComponentName = getActivityComponentName(TEST_ACTIVITY_NAME);

        final String[] waitForActivityRecords = new String[] {activityComponentName};

        // Run at least 2 rounds to verify that -D works with an existing process.
        // -D could fail in this case if the force stop of process is broken.
        int prevProcId = -1;
        for (int i = 0; i < 2; i++) {
            executeShellCommand("am start -n " + getActivityComponentName(TEST_ACTIVITY_NAME) + " -D");

            mAmWmState.waitForDebuggerWindowVisible(waitForActivityRecords);
            int procId = mAmWmState.getAmState().getActivityProcId(activityComponentName);

            assertTrue("Invalid ProcId.", procId >= 0);
            if (i > 0) {
                assertTrue("Run " + i + " didn't start new proc.", prevProcId != procId);
            }
            prevProcId = procId;
        }
    }

    @Test
    public void testDashW_Direct() throws Exception {
        testDashW(SINGLE_TASK_ACTIVITY_NAME, SINGLE_TASK_ACTIVITY_NAME);
    }

    @Test
    public void testDashW_Indirect() throws Exception {
        testDashW(ENTRYPOINT_ACTIVITY_NAME, SINGLE_TASK_ACTIVITY_NAME);
    }

    private void testDashW(final String entryActivity, final String actualActivity)
            throws Exception {
        // Test cold start
        startActivityAndVerifyResult(entryActivity, actualActivity, true);

        // Test warm start
        pressHomeButton();
        startActivityAndVerifyResult(entryActivity, actualActivity, false);

        // Test "hot" start (app already in front)
        startActivityAndVerifyResult(entryActivity, actualActivity, false);
    }

    private void startActivityAndVerifyResult(final String entryActivity,
            final String actualActivity, boolean shouldStart) throws Exception {
        // See TODO below
        // final LogSeparator logSeparator = clearLogcat();

        // Pass in different data only when cold starting. This is to make the intent
        // different in subsequent warm/hot launches, so that the entrypoint alias
        // activity is always started, but the actual activity is not started again
        // because of the NEW_TASK and singleTask flags.
        final String result = executeShellCommand("am start -n " + getActivityComponentName(entryActivity) + " -W"
                + (shouldStart ? " -d about:blank" : ""));

        // Verify shell command return value
        verifyShellOutput(result, actualActivity, shouldStart);

        // TODO: Disable logcat check for now.
        // Logcat of WM or AM tag could be lost (eg. chatty if earlier events generated
        // too many lines), and make the test look flaky. We need to either use event
        // log or swith to other mechanisms. Only verify shell output for now, it should
        // still catch most failures.

        // Verify adb logcat log
        //verifyLogcat(actualActivity, shouldStart, logSeparator);
    }

    private static final Pattern sNotStartedWarningPattern = Pattern.compile(
            "Warning: Activity not started(.*)");
    private static final Pattern sStatusPattern = Pattern.compile(
            "Status: (.*)");
    private static final Pattern sActivityPattern = Pattern.compile(
            "Activity: (.*)");
    private static final String sStatusOk = "ok";

    private void verifyShellOutput(
            final String result, final String activity, boolean shouldStart) {
        boolean warningFound = false;
        String status = null;
        String reportedActivity = null;
        String componentActivityName = getActivityComponentName(activity);

        final String[] lines = result.split("\\n");
        // Going from the end of logs to beginning in case if some other activity is started first.
        for (int i = lines.length - 1; i >= 0; i--) {
            final String line = lines[i].trim();
            Matcher matcher = sNotStartedWarningPattern.matcher(line);
            if (matcher.matches()) {
                warningFound = true;
                continue;
            }
            matcher = sStatusPattern.matcher(line);
            if (matcher.matches()) {
                status = matcher.group(1);
                continue;
            }
            matcher = sActivityPattern.matcher(line);
            if (matcher.matches()) {
                reportedActivity = matcher.group(1);
                continue;
            }
        }

        assertTrue("Status " + status + " is not ok", sStatusOk.equals(status));
        assertTrue("Reported activity is " + reportedActivity + " not " + componentActivityName,
                componentActivityName.equals(reportedActivity));

        if (shouldStart && warningFound) {
            fail("Should start new activity but brought something to front.");
        } else if (!shouldStart && !warningFound){
            fail("Should bring existing activity to front but started new activity.");
        }
    }

    private static final Pattern sDisplayTimePattern =
            Pattern.compile("(.+): Displayed (.*): (\\+{0,1})([0-9]+)ms(.*)");

    void verifyLogcat(String actualActivityName, boolean shouldStart, LogSeparator logSeparator) {
        int displayCount = 0;
        String activityName = null;

        for (String line : getDeviceLogsForComponents(logSeparator, "ActivityManager")) {
            line = line.trim();

            Matcher matcher = sDisplayTimePattern.matcher(line);
            if (matcher.matches()) {
                activityName = matcher.group(2);
                // Ignore activitiy displays from other packages, we don't
                // want some random activity starts to ruin our test.
                if (!activityName.startsWith("android.server.am")) {
                    continue;
                }
                if (!shouldStart) {
                    fail("Shouldn't display anything but displayed " + activityName);
                }
                displayCount++;
            }
        }
        final String expectedActivityName = getActivityComponentName(actualActivityName);
        if (shouldStart) {
            if (displayCount != 1) {
                fail("Should display exactly one activity but displayed " + displayCount);
            } else if (!expectedActivityName.equals(activityName)) {
                fail("Should display " + expectedActivityName +
                        " but displayed " + activityName);
            }
        }
    }
}
