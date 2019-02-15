/**
 * Copyright (C) 2019 The Android Open Source Project
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
public class Poc17_12 extends SecurityTestCase {

  /**
   * CVE-2017-14904
   */
  @SecurityTest(minPatchLevel = "2017-12")
  public void testPocCVE_2017_14904() throws Exception {
    AdbUtils.runCommandLine("logcat -c", getDevice());
    AdbUtils.runPocNoOutput("CVE-2017-14904", getDevice(), 60);
    String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
    assertNotMatchesMultiLine(
        ">>> /system/bin/mediaserver <<<.*signal 11 \\(SIGSEGV\\)", logcat);
  }
}
