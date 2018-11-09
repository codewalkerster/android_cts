/**
 * Copyright (C) 2018 The Android Open Source Project
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
import java.util.concurrent.TimeUnit;

@SecurityTest
public class Poc17_06 extends SecurityTestCase {

    /**
     * b/36392138
     */
    @SecurityTest(minPatchLevel = "2017-06")
    public void testPocCVE_2017_0647() throws Exception {
        AdbUtils.pushResource("/CVE-2017-0647.zip", "/data/local/tmp/CVE-2017-0647.zip",
                getDevice());
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runCommandLine(
            "dex2oat " +
            "--dex-file=/data/local/tmp/CVE-2017-0647.zip " +
            "--oat-file=/data/local/tmp/out " +
            "--base=0x50000000", getDevice());
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Zip: missed a central dir sig", logcatOut);
    }
}
