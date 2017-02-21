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

package android.server.cts;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.KeyguardLockedTests
 */
public class KeyguardLockedTests extends KeyguardTestBase {

    private static final String PIP_ACTIVITY = "PipActivity";
    private static final String PIP_ACTIVITY_ACTION_ENTER_PIP =
            "android.server.cts.PipActivity.enter_pip";
    private static final String EXTRA_SHOW_OVER_KEYGUARD = "show_over_keyguard";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setLockCredential();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        removeLockCredential();
    }

    public void testLockAndUnlock() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
        unlockDeviceWithCredential();
        mAmWmState.waitForKeyguardGone(mDevice);
        assertKeyguardGone();
    }

    public void testDismissKeyguard() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
        launchActivity("DismissKeyguardActivity");
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone(mDevice);
        assertKeyguardGone();
        mAmWmState.assertVisibility("DismissKeyguardActivity", true);
    }

    public void testDismissKeyguard_whileOccluded() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedActivity" });
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        launchActivity("DismissKeyguardActivity");
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone(mDevice);
        assertKeyguardGone();
        mAmWmState.assertVisibility("DismissKeyguardActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedActivity", false);
    }

    public void testDismissKeyguard_fromShowWhenLocked_notAllowed() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedActivity" });
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguard true");
        enterAndConfirmLockCredential();

        // Make sure we stay on Keyguard.
        assertShowingAndOccluded();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
    }

    public void testDismissKeyguardActivity_method() throws Exception {
        if (!isHandheld()) {
            return;
        }
        clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardMethodActivity");
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone(mDevice);
        mAmWmState.computeState(mDevice, new String[] { "DismissKeyguardMethodActivity"});
        mAmWmState.assertVisibility("DismissKeyguardMethodActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat();
    }

    public void testDismissKeyguardActivity_method_cancelled() throws Exception {
        if (!isHandheld()) {
            return;
        }
        clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardMethodActivity");
        pressBackButton();
        assertOnDismissCancelledInLogcat();
        mAmWmState.computeState(mDevice, new String[] {});
        mAmWmState.assertVisibility("DismissKeyguardMethodActivity", false);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        unlockDeviceWithCredential();
    }

    public void testEnterPipOverKeyguard() throws Exception {
        if (!isHandheld() || !supportsPip()) {
            return;
        }

        // Go to the keyguard
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);

        // Enter PiP on an activity on top of the keyguard, and ensure that it prompts the user for
        // their credentials and does not enter picture-in-picture yet
        launchActivity(PIP_ACTIVITY, EXTRA_SHOW_OVER_KEYGUARD, "true");
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForKeyguardShowingAndOccluded(mDevice);
        assertShowingAndOccluded();
        mAmWmState.assertDoesNotContainStack("Must not contain pinned stack.", PINNED_STACK_ID);

        // Enter the credentials and ensure that the activity actually entered picture-in-picture
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone(mDevice);
        assertKeyguardGone();
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
    }
}
