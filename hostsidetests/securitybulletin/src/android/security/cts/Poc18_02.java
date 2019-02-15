/**
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc18_02 extends SecurityTestCase {

    /**
     *  b/31799863
     */
    @SecurityTest(minPatchLevel = "2018-02")
    public void testPocCVE_2017_6258() throws Exception {
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runPocNoOutput("CVE-2017-6258", getDevice(), 60);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Fatal signal 11 \\(SIGSEGV\\)" +
                         "[\\s\\n\\S]*>>> /system/bin/" +
                         "mediaserver <<<", logcatOut);
    }

    /**
     *  b/35269676
     */
    @SecurityTest(minPatchLevel = "2018-02")
    public void testPocCVE_2017_11041() throws Exception {
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runPocNoOutput("CVE-2017-11041", getDevice(), 60);
        //Sleep to allow crash log to propogate to logcat
        Thread.sleep(3000);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        //PoC may cause continuous crashes, reboot
        AdbUtils.runCommandLine("reboot", getDevice());
        getDevice().waitForDeviceAvailable(60);
        updateKernelStartTime();
        assertNotMatchesMultiLine("Fatal signal 11 \\(SIGSEGV\\)" +
                         "[\\s\\n\\S]*>>> /system/bin/" +
                         "mediaserver <<<", logcatOut);
    }

    /**
     * b/68953950
     */
     @SecurityTest(minPatchLevel = "2018-02")
     public void testPocCVE_2017_13232() throws Exception {
       AdbUtils.runCommandLine("logcat -c" , getDevice());
       AdbUtils.runPocNoOutput("CVE-2017-13232", getDevice(), 60);
       String logcatOutput = AdbUtils.runCommandLine("logcat -d", getDevice());
       assertNotMatchesMultiLine("APM_AudioPolicyManager: getOutputForAttr\\(\\) " +
                                 "invalid attributes: usage=.{1,15} content=.{1,15} " +
                                 "flags=.{1,15} tags=\\[A{256,}\\]", logcatOutput);
     }

    /**
     *  b/65853158
     */
    @SecurityTest(minPatchLevel = "2018-02")
    public void testPocCVE_2017_13273() throws Exception {
        AdbUtils.runCommandLine("dmesg -c" ,getDevice());
        AdbUtils.runCommandLine("setenforce 0",getDevice());
        if(containsDriver(getDevice(), "/dev/xt_qtaguid") &&
           containsDriver(getDevice(), "/proc/net/xt_qtaguid/ctrl")) {
            AdbUtils.runPoc("CVE-2017-13273", getDevice(), 60);
            String dmesgOut = AdbUtils.runCommandLine("cat /sys/fs/pstore/console-ramoops",
                              getDevice());
            assertNotMatchesMultiLine("CVE-2017-132736 Tainted:" + "[\\s\\n\\S]*" +
                 "Kernel panic - not syncing: Fatal exception in interrupt", dmesgOut);
        }
        AdbUtils.runCommandLine("setenforce 1",getDevice());
    }

    /**
     * CVE-2017-17767
     */
    @SecurityTest(minPatchLevel = "2018-02")
    public void testPocCVE_2017_17767() throws Exception {
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runPoc("CVE-2017-17767", getDevice(), 60);
        String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine(
                ">>> /system/bin/mediaserver <<<.*signal 11 \\(SIGSEGV\\)", logcat);
    }
}
