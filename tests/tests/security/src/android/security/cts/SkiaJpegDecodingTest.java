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

package android.security.cts;

import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;

public class SkiaJpegDecodingTest extends
        ActivityInstrumentationTestCase2<SkiaJpegDecodingActivity> {
    private Activity mActivity;

    public SkiaJpegDecodingTest() {
        super(SkiaJpegDecodingActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        assertNotNull("Failed to get the activity instance", mActivity);
    }

    public void testLibskiaOverFlowJpegProcessing() {
        /**
         * This is an empty test case. The activity attached with this class
         * loads a corrupt JPEG Image on an ImageView. On a patched device
         * this will work but on an unpatched device it will crash.
         */
    }

    @Override
    protected void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }
        super.tearDown();
    }
}
