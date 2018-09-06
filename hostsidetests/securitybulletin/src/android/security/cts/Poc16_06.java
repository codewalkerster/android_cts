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

public class Poc16_06 extends SecurityTestCase {
    /**
     *  b/27661749
     */
    @SecurityTest(minPatchLevel = "2016-06")
    public void testPocCVE_2016_2482() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-2482", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Fatal signal[\\s\\S]*/system/bin/mediaserver",
                         logcat);
    }
}
