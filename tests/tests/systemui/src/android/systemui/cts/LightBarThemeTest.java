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

package android.systemui.cts;

import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LightBarThemeTest {

    @Rule
    public ActivityTestRule<LightBarThemeActivity> mActivityRule = new ActivityTestRule<>(
            LightBarThemeActivity.class);

    @Test
    public void testThemeSetsFlags() {
        final int flags = mActivityRule.getActivity().getWindow().getAttributes()
                .systemUiVisibility;
        Assert.assertTrue((flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0);
        Assert.assertTrue((flags & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0);
    }
}
