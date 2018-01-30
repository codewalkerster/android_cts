/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.slice.cts;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.app.slice.Slice;
import android.app.slice.SliceManager;
import android.app.slice.SliceManager.SliceCallback;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.verification.Timeout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class SliceManagerTest {

    private static final Uri BASE_URI = Uri.parse("content://android.slice.cts.local/main");
    private final Context mContext = InstrumentationRegistry.getContext();
    private final SliceManager mSliceManager = mContext.getSystemService(SliceManager.class);

    private String mSetupLauncher;

    @Before
    public void setup() {
        LocalSliceProvider.sProxy = mock(SliceProvider.class);
        try {
            mSetupLauncher = getDefaultLauncher();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mSliceManager.unpinSlice(BASE_URI);
        } catch (Exception e) {
        }
    }

    @After
    public void teardown() throws Exception {
        try {
            mSliceManager.unpinSlice(BASE_URI);
        } catch (Exception e) {
        }
        if (mSetupLauncher != null) {
            setLauncher(mSetupLauncher);
        }
    }

    @Test(expected = SecurityException.class)
    public void testNoAccess() {
        mSliceManager.pinSlice(BASE_URI, Collections.emptyList());
        fail();
    }

    @Test
    public void testPinSlice() throws Exception {
        setLauncher(new ComponentName(mContext.getPackageName(), Launcher.class.getName())
                .flattenToString());
        mSliceManager.pinSlice(BASE_URI, Collections.emptyList());

        verify(LocalSliceProvider.sProxy, timeout(2000)).onSlicePinned(eq(BASE_URI));
    }

    @Test
    public void testUnpinSlice() throws Exception {
        setLauncher(new ComponentName(mContext.getPackageName(), Launcher.class.getName())
                .flattenToString());

        mSliceManager.pinSlice(BASE_URI, Collections.emptyList());

        verify(LocalSliceProvider.sProxy, timeout(2000)).onSlicePinned(eq(BASE_URI));

        mSliceManager.unpinSlice(BASE_URI);

        verify(LocalSliceProvider.sProxy, timeout(2000)).onSliceUnpinned(eq(BASE_URI));
    }

    @Test
    public void testRegisterPin() {
        SliceCallback callback = mock(SliceCallback.class);

        mSliceManager.registerSliceCallback(BASE_URI, Collections.emptyList(), callback);
        verify(LocalSliceProvider.sProxy, timeout(2000)).onSlicePinned(eq(BASE_URI));

        mSliceManager.unregisterSliceCallback(BASE_URI, callback);
        verify(LocalSliceProvider.sProxy, timeout(2000)).onSliceUnpinned(eq(BASE_URI));
    }

    @Test
    public void testCallback() {
        SliceCallback callback = mock(SliceCallback.class);

        mSliceManager.registerSliceCallback(BASE_URI, Collections.emptyList(),
                command -> command.run(), callback);
        verify(LocalSliceProvider.sProxy, timeout(2000)).onSlicePinned(eq(BASE_URI));

        try {
            Slice s = new Slice.Builder(BASE_URI).build();
            when(LocalSliceProvider.sProxy.onBindSlice(any(), any())).thenReturn(s);

            mContext.getContentResolver().notifyChange(BASE_URI, null);
            verify(callback, new Timeout(2000, atLeastOnce())).onSliceUpdated(any());
        } finally {
            mSliceManager.unregisterSliceCallback(BASE_URI, callback);
            verify(LocalSliceProvider.sProxy, timeout(2000)).onSliceUnpinned(eq(BASE_URI));
        }
    }

    public static String getDefaultLauncher() throws Exception {
        final String PREFIX = "Launcher: ComponentInfo{";
        final String POSTFIX = "}";
        for (String s : runShellCommand("cmd shortcut get-default-launcher")) {
            if (s.startsWith(PREFIX) && s.endsWith(POSTFIX)) {
                return s.substring(PREFIX.length(), s.length() - POSTFIX.length());
            }
        }
        throw new Exception("Default launcher not found");
    }

    public static void setLauncher(String component) throws Exception {
        runShellCommand("cmd package set-home-activity --user "
                + getInstrumentation().getContext().getUserId() + " " + component);
    }

    public static ArrayList<String> runShellCommand(String command) throws Exception {
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command);

        ArrayList<String> ret = new ArrayList<>();
        // Read the input stream fully.
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)))) {
            String line;
            while ((line = r.readLine()) != null) {
                ret.add(line);
            }
        }
        return ret;
    }

    public static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

}