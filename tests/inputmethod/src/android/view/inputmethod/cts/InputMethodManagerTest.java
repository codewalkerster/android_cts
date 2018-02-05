/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class InputMethodManagerTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private Instrumentation mInstrumentation;
    private Context mContext;
    private InputMethodManager mImManager;

    @Rule
    public ActivityTestRule<InputMethodCtsActivity> mActivityRule =
            new ActivityTestRule<>(InputMethodCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mImManager = mContext.getSystemService(InputMethodManager.class);
    }

    @After
    public void teardown() {
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void testInputMethodManager() throws Throwable {
        final InputMethodCtsActivity activity = mActivityRule.getActivity();
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS));

        Window window = activity.getWindow();
        final EditText view = window.findViewById(R.id.entry);

        PollingCheck.waitFor(1000, view::hasWindowFocus);

        mActivityRule.runOnUiThread(view::requestFocus);
        mInstrumentation.waitForIdleSync();
        assertTrue(view.isFocused());

        BaseInputConnection connection = new BaseInputConnection(view, false);

        PollingCheck.waitFor(mImManager::isActive);

        assertTrue(mImManager.isAcceptingText());
        assertTrue(mImManager.isActive(view));

        assertFalse(mImManager.isFullscreenMode());
        connection.reportFullscreenMode(true);
        // Only IMEs are allowed to report full-screen mode.  Calling this method from the
        // application should have no effect.
        assertFalse(mImManager.isFullscreenMode());

        mActivityRule.runOnUiThread(() -> {
            IBinder token = view.getWindowToken();

            // Show and hide input method.
            assertTrue(mImManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT));
            assertTrue(mImManager.hideSoftInputFromWindow(token, 0));

            Handler handler = new Handler();
            ResultReceiver receiver = new ResultReceiver(handler);
            assertTrue(mImManager.showSoftInput(view, 0, receiver));
            receiver = new ResultReceiver(handler);
            assertTrue(mImManager.hideSoftInputFromWindow(token, 0, receiver));

            // status: hide to show to hide
            mImManager.toggleSoftInputFromWindow(token, 0, InputMethodManager.HIDE_NOT_ALWAYS);
            mImManager.toggleSoftInputFromWindow(token, 0, InputMethodManager.HIDE_NOT_ALWAYS);

            List<InputMethodInfo> enabledImList = mImManager.getEnabledInputMethodList();
            List<InputMethodInfo> imList = mImManager.getInputMethodList();
            if (imList != null && enabledImList != null) {
                assertTrue(imList.size() >= enabledImList.size());
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testShowInputMethodPicker() throws Exception {
        TestActivity.startSync(activity -> {
            final View view = new View(activity);
            view.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return view;
        });

        // Make sure that InputMethodPicker is not shown in the initial state.
        mContext.sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        waitOnMainUntil(() -> !mImManager.isInputMethodPickerShown(), TIMEOUT,
                "InputMethod picker should be closed");

        // Test InputMethodManager#showInputMethodPicker() works as expected.
        mImManager.showInputMethodPicker();
        waitOnMainUntil(() -> mImManager.isInputMethodPickerShown(), TIMEOUT,
                "InputMethod picker should be shown");

        // Make sure that InputMethodPicker can be closed with ACTION_CLOSE_SYSTEM_DIALOGS
        mContext.sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        waitOnMainUntil(() -> !mImManager.isInputMethodPickerShown(), TIMEOUT,
                "InputMethod picker should be closed");
    }
}
