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

package android.view.cts;

import static org.junit.Assert.fail;

import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@FlakyTest
@RunWith(AndroidJUnit4.class)
public class ChoreographerNativeTest {
    private long mChoreographerPtr;

    private static native long nativeGetChoreographer();
    private static native boolean nativePrepareChoreographerTests(long ptr);
    private static native void nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks(long ptr);
    private static native void nativeTestPostCallbackWithDelayEventuallyRunsCallbacks(long ptr);

    static {
        System.loadLibrary("ctsview_jni");
    }

    @UiThreadTest
    @Before
    public void setup() {
        mChoreographerPtr = nativeGetChoreographer();
        if (!nativePrepareChoreographerTests(mChoreographerPtr)) {
            fail("Failed to setup choreographer tests");
        }
    }

    @MediumTest
    @Test
    public void testPostCallbackWithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testPostCallbackWithDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackWithDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }
}
