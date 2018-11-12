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

package android.multiuser.cts;

import com.android.compatibility.common.util.SystemUtil;

import android.os.UserManager;
import android.test.InstrumentationTestCase;

public class SplitSystemUserTest extends InstrumentationTestCase {

    public void testSplitSystemUserIsDisabled() throws Exception {
        // Check that ro.fw.system_user_split property is not set.
        String splitEnabledStr = trim(SystemUtil.runShellCommand(getInstrumentation(),
                "getprop ro.fw.system_user_split"));
        boolean splitEnabled = "y".equals(splitEnabledStr) || "yes".equals(splitEnabledStr)
                || "1".equals(splitEnabledStr) || "true".equals(splitEnabledStr)
                || "on".equals(splitEnabledStr);
        assertFalse("ro.fw.system_user_split must not be enabled", splitEnabled);

        // Check UserManager.isSplitSystemUser returns false as well.
        assertFalse("UserManager.isSplitSystemUser must be false", UserManager.isSplitSystemUser());
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
