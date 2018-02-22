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
 * limitations under the License.
 */

package android.inputmethodservice.cts.hostside;

import static android.inputmethodservice.cts.common.DeviceEventConstants.ACTION_DEVICE_EVENT;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.TEST_START;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_SENDER;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_TYPE;
import static android.inputmethodservice.cts.common.DeviceEventConstants.RECEIVER_COMPONENT;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.inputmethodservice.cts.common.EditTextAppConstants;
import android.inputmethodservice.cts.common.EventProviderConstants.EventTableConstants;
import android.inputmethodservice.cts.common.Ime1Constants;
import android.inputmethodservice.cts.common.Ime2Constants;
import android.inputmethodservice.cts.common.test.DeviceTestConstants;
import android.inputmethodservice.cts.common.test.ShellCommandUtils;
import android.inputmethodservice.cts.common.test.TestInfo;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(DeviceJUnit4ClassRunner.class)
public class InputMethodServiceLifecycleTest extends BaseHostJUnit4Test {

    private static final long TIMEOUT = TimeUnit.MICROSECONDS.toMillis(20000);
    private static final long POLLING_INTERVAL = TimeUnit.MICROSECONDS.toMillis(200);

    @Before
    public void setUp() throws Exception {
        // Skip whole tests when DUT has no android.software.input_methods feature.
        assumeTrue(hasDeviceFeature(ShellCommandUtils.FEATURE_INPUT_METHODS));
        cleanUpTestImes();
        shell(ShellCommandUtils.deleteContent(EventTableConstants.CONTENT_URI));
    }

    @After
    public void tearDown() throws Exception {
        shell(ShellCommandUtils.resetImes());
    }

    @Test
    public void testSwitchIme() throws Exception {
        final TestInfo testSwitchIme1ToIme2 = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS, DeviceTestConstants.TEST_SWITCH_IME1_TO_IME2);
        sendTestStartEvent(testSwitchIme1ToIme2);
        installPackage(Ime1Constants.APK, "-r");
        installPackage(Ime2Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.enableIme(Ime2Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));

        assertTrue(runDeviceTestMethod(testSwitchIme1ToIme2));
    }

    @Test
    public void testUninstallCurrentIme() throws Exception {
        final TestInfo testCreateIme1 = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS, DeviceTestConstants.TEST_CREATE_IME1);
        sendTestStartEvent(testCreateIme1);
        installPackage(Ime1Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));
        assertTrue(runDeviceTestMethod(testCreateIme1));

        uninstallPackageIfExists(Ime1Constants.PACKAGE);
        assertImeNotSelectedInSecureSettings(Ime1Constants.IME_ID, TIMEOUT);
    }

    @Test
    public void testDisableCurrentIme() throws Exception {
        final TestInfo testCreateIme1 = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS, DeviceTestConstants.TEST_CREATE_IME1);
        sendTestStartEvent(testCreateIme1);
        installPackage(Ime1Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));
        assertTrue(runDeviceTestMethod(testCreateIme1));

        shell(ShellCommandUtils.disableIme(Ime1Constants.IME_ID));
        assertImeNotSelectedInSecureSettings(Ime1Constants.IME_ID, TIMEOUT);
    }

    @Test
    public void testSetInputMethodAndSubtype() throws Exception {
        final TestInfo testSetInputMethod = new TestInfo(
                DeviceTestConstants.PACKAGE, DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_SET_INPUTMETHOD_AND_SUBTYPE);
        sendTestStartEvent(testSetInputMethod);
        installPackage(Ime1Constants.APK, "-r");
        installPackage(Ime2Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.enableIme(Ime2Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));

        assertTrue(runDeviceTestMethod(testSetInputMethod));
    }

    @Test
    public void testSwitchToNextInput() throws Exception {
        final TestInfo testSwitchInputs = new TestInfo(
                DeviceTestConstants.PACKAGE, DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_SWITCH_NEXT_INPUT);
        sendTestStartEvent(testSwitchInputs);
        installPackage(Ime1Constants.APK, "-r");
        installPackage(Ime2Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        // Make sure that there is at least one more IME that specifies
        // supportsSwitchingToNextInputMethod="true"
        shell(ShellCommandUtils.enableIme(Ime2Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));

        assertTrue(runDeviceTestMethod(testSwitchInputs));
    }

    @Test
    public void testSwitchToLastInput() throws Exception {
        final TestInfo testSwitchInputs = new TestInfo(
                DeviceTestConstants.PACKAGE, DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_SWITCH_LAST_INPUT);
        sendTestStartEvent(testSwitchInputs);
        installPackage(Ime1Constants.APK, "-r");
        installPackage(Ime2Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.enableIme(Ime2Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));

        assertTrue(runDeviceTestMethod(testSwitchInputs));
    }

    @Test
    public void testInputUnbindsOnImeStopped() throws Exception {
        final TestInfo testUnbind = new TestInfo(
                DeviceTestConstants.PACKAGE, DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_INPUT_UNBINDS_ON_IME_STOPPED);
        sendTestStartEvent(testUnbind);
        installPackage(Ime1Constants.APK, "-r");
        installPackage(Ime2Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.enableIme(Ime2Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));

        assertTrue(runDeviceTestMethod(testUnbind));
    }

    @Test
    public void testInputUnbindsOnAppStop() throws Exception {
        final TestInfo testUnbind = new TestInfo(
                DeviceTestConstants.PACKAGE, DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_INPUT_UNBINDS_ON_APP_STOPPED);
        sendTestStartEvent(testUnbind);
        installPackage(Ime1Constants.APK, "-r");
        installPackage(EditTextAppConstants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));

        assertTrue(runDeviceTestMethod(testUnbind));
    }

    private void sendTestStartEvent(final TestInfo deviceTest) throws Exception {
        final String sender = deviceTest.getTestName();
        // {@link EventType#EXTRA_EVENT_TIME} will be recorded at device side.
        shell(ShellCommandUtils.broadcastIntent(
                ACTION_DEVICE_EVENT, RECEIVER_COMPONENT,
                "--es", EXTRA_EVENT_SENDER, sender,
                "--es", EXTRA_EVENT_TYPE, TEST_START.name()));
    }

    private boolean runDeviceTestMethod(final TestInfo deviceTest) throws Exception {
        return runDeviceTests(deviceTest.testPackage, deviceTest.testClass, deviceTest.testMethod);
    }

    private String shell(final String command) throws Exception {
        return getDevice().executeShellCommand(command).trim();
    }

    private void cleanUpTestImes() throws Exception {
        uninstallPackageIfExists(Ime1Constants.PACKAGE);
        uninstallPackageIfExists(Ime2Constants.PACKAGE);
    }

    private void uninstallPackageIfExists(final String packageName) throws Exception {
        if (isPackageInstalled(getDevice(), packageName)) {
            uninstallPackage(getDevice(), packageName);
        }
    }

    /**
     * Makes sure that the given IME is not in the stored in the secure settings as the current IME.
     *
     * @param imeId IME ID to be monitored
     * @param timeout timeout in millisecond
     */
    private void assertImeNotSelectedInSecureSettings(String imeId, long timeout) throws Exception {
        while (true) {
            if (timeout < 0) {
                throw new TimeoutException(imeId + " is still the current IME even after "
                        + timeout + " msec.");
            }
            if (!imeId.equals(shell(ShellCommandUtils.getCurrentIme()))) {
                break;
            }
            Thread.sleep(POLLING_INTERVAL);
            timeout -= POLLING_INTERVAL;
        }
    }
}
