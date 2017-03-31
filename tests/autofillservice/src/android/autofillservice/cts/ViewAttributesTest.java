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
 * limitations under the License.
 */

package android.autofillservice.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.support.annotation.NonNull;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ViewAttributesTest extends AutoFillServiceTestCase {
    @Rule
    public final ActivityTestRule<ViewAttributesTestActivity> mActivityRule =
            new ActivityTestRule<>(ViewAttributesTestActivity.class);

    private ViewAttributesTestActivity mActivity;
    private EditText mFirstLevelDefault;
    private EditText mFirstLevelManual;
    private EditText mFirstLevelAuto;
    private EditText mFirstLevelInherit;
    private EditText mManualContainerInherit;
    private EditText mManualContainerDefault;
    private EditText mManualContainerManual;
    private EditText mManualContainerAuto;
    private OneTimeTextWatcher mFirstLevelDefaultTextWatcher;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
        mFirstLevelDefault = (EditText) mActivity.findViewById(R.id.firstLevelDefault);
        mFirstLevelManual = (EditText) mActivity.findViewById(R.id.firstLevelManual);
        mFirstLevelAuto = (EditText) mActivity.findViewById(R.id.firstLevelAuto);
        mFirstLevelInherit = (EditText) mActivity.findViewById(R.id.firstLevelInherit);
        mManualContainerDefault = (EditText) mActivity.findViewById(R.id.manualContainerDefault);
        mManualContainerManual = (EditText) mActivity.findViewById(R.id.manualContainerManual);
        mManualContainerAuto = (EditText) mActivity.findViewById(R.id.manualContainerAuto);
        mManualContainerInherit = (EditText) mActivity.findViewById(R.id.manualContainerInherit);
    }

    private void runOnUiThreadSync(@NonNull Runnable r) throws InterruptedException {
        RuntimeException exceptionOnUiThread[] = {null};

        synchronized (this) {
            mActivity.runOnUiThread(() -> {
                synchronized (this) {
                    try {
                        r.run();
                    } catch (RuntimeException e) {
                        exceptionOnUiThread[0] = e;
                    }
                    this.notify();
                }
            });

            wait();
        }

        if (exceptionOnUiThread[0] != null) {
            throw exceptionOnUiThread[0];
        }
    }

    /**
     * Sets the expectation for an auto-fill request, so it can be asserted through
     * {@link #assertAutoFilled()} later.
     */
    void expectAutoFill() {
        mFirstLevelDefaultTextWatcher = new OneTimeTextWatcher("firstLevelDefault",
                mFirstLevelDefault, "filled");
        mFirstLevelDefault.addTextChangedListener(mFirstLevelDefaultTextWatcher);
    }

    /**
     * Asserts the activity was auto-filled with the values passed to
     * {@link #expectAutoFill()}.
     */
    void assertAutoFilled() throws Exception {
        mFirstLevelDefaultTextWatcher.assertAutoFilled();
    }

    /**
     * Try to auto-fill by moving the focus to {@code field}. If the field should trigger an
     * auto-fill the auto-fill UI should show up.
     *
     * @param field    The field to move the focus to
     * @param expectUI If the auto-fill UI is expected to show up
     *
     * @throws Exception If something unexpected happened
     */
    private void checkFieldBehavior(@NonNull EditText field, boolean expectUI) throws Exception {
        if (expectUI) {
            assertThat(field.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);
        } else {
            assertThat(field.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_MANUAL);
        }

        // Make sure the requestFocus triggers a change
        if (field == mFirstLevelManual) {
            runOnUiThreadSync(() -> mFirstLevelDefault.requestFocus());
        } else {
            runOnUiThreadSync(() -> mFirstLevelManual.requestFocus());
        }

        enableService();

        try {
            sReplier.addResponse(new CannedFillResponse.Builder()
                    .addDataset(new CannedFillResponse.CannedDataset.Builder()
                            .setField("firstLevelDefault", "filled")
                            .setField("firstLevelManual", "filled")
                            .setField("firstLevelAuto", "filled")
                            .setField("firstLevelInherit", "filled")
                            .setField("manualContainerDefault", "filled")
                            .setField("manualContainerManual", "filled")
                            .setField("manualContainerAuto", "filled")
                            .setField("manualContainerInherit", "filled")
                            .setPresentation(createPresentation("dataset"))
                            .build())
                    .build());

            expectAutoFill();

            runOnUiThreadSync(() -> field.requestFocus());

            Throwable exceptionDuringAutoFillTrigger = null;
            try {
                sReplier.getNextFillRequest();
                sUiBot.selectDataset("dataset");
            } catch (Throwable e) {
                if (expectUI) {
                    throw e;
                } else {
                    exceptionDuringAutoFillTrigger = e;
                }
            }

            if (expectUI) {
                assertAutoFilled();
            } else {
                assertThat(exceptionDuringAutoFillTrigger).isNotNull();
            }
        } finally {
            disableService();
        }
    }

    @Test
    public void checkDefaultValue() {
        assertThat(mFirstLevelDefault.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_INHERIT);
    }

    @Test
    public void checkInheritValue() {
        assertThat(mFirstLevelInherit.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_INHERIT);
    }

    @Test
    public void checkAutoValue() {
        assertThat(mFirstLevelAuto.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);
    }

    @Test
    public void checkManualValue() {
        assertThat(mFirstLevelManual.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_MANUAL);
    }

    @Test
    public void checkNestedDefaultValue() {
        assertThat(mManualContainerDefault.getAutofillMode()).isEqualTo(
                View.AUTOFILL_MODE_INHERIT);
    }

    @Test
    public void checkNestedInheritValue() {
        assertThat(mManualContainerInherit.getAutofillMode()).isEqualTo(
                View.AUTOFILL_MODE_INHERIT);
    }

    @Test
    public void checkNestedAutoValue() {
        assertThat(mManualContainerAuto.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);
    }

    @Test
    public void checkNestedManualValue() {
        assertThat(mManualContainerManual.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_MANUAL);
    }

    @Test
    public void checkDefaultBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelDefault, true);
    }

    @Test
    public void checkInheritBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelInherit, true);
    }

    @Test
    public void checkAutoBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelAuto, true);
    }

    @Test
    public void checkManualBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelManual, false);
    }

    @Test
    public void checkNestedDefaultBehavior() throws Exception {
        checkFieldBehavior(mManualContainerDefault, false);
    }

    @Test
    public void checkNestedInheritBehavior() throws Exception {
        checkFieldBehavior(mManualContainerInherit, false);
    }

    @Test
    public void checkNestedAutoBehavior() throws Exception {
        checkFieldBehavior(mManualContainerAuto, true);
    }

    @Test
    public void checkNestedManualBehavior() throws Exception {
        checkFieldBehavior(mManualContainerManual, false);
    }

    @Test
    public void checksetAutofillMode() {
        mFirstLevelDefault.setAutofillMode(View.AUTOFILL_MODE_MANUAL);
        assertThat(mFirstLevelDefault.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_MANUAL);
    }

    @Test
    public void checkIllegalAutoFillModeSet() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mFirstLevelDefault.setAutofillMode(-1));
    }

    @Test
    public void checkTextViewNoHint() {
        assertThat(mActivity.findViewById(R.id.textViewNoHint).getAutofillHints()).isNull();
    }

    @Test
    public void checkTextViewHintCustom() {
        assertThat(mActivity.findViewById(R.id.textViewHintCustom).getAutofillHints()).isEqualTo(
                new String[]{mActivity.getString(R.string.new_password_label)});
    }

    @Test
    public void checkTextViewPassword() {
        assertThat(mActivity.findViewById(R.id.textViewPassword).getAutofillHints()).isEqualTo(
                new String[]{View.AUTOFILL_HINT_PASSWORD});
    }

    @Test
    public void checkTextViewPhoneName() {
        assertThat(mActivity.findViewById(R.id.textViewPhoneName).getAutofillHints()).isEqualTo(
                new String[]{View.AUTOFILL_HINT_PHONE, View.AUTOFILL_HINT_USERNAME});
    }

    @Test
    public void checkTextViewHintsFromArray() {
        assertThat(mActivity.findViewById(R.id.textViewHintsFromArray).getAutofillHints()).isEqualTo(
                new String[]{"yesterday", "today", "tomorrow", "never"});
    }

    @Test
    public void checkSetAutoFill() {
        View v = mActivity.findViewById(R.id.textViewNoHint);

        v.setAutofillHints(null);
        assertThat(v.getAutofillHints()).isNull();

        v.setAutofillHints(new String[0]);
        assertThat(v.getAutofillHints()).isNull();

        v.setAutofillHints(new String[]{View.AUTOFILL_HINT_PASSWORD});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{View.AUTOFILL_HINT_PASSWORD});

        v.setAutofillHints(new String[]{"custom", "value"});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{"custom", "value"});

        v.setAutofillHints("more", "values");
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{"more", "values"});

        v.setAutofillHints(
                new String[]{View.AUTOFILL_HINT_PASSWORD, View.AUTOFILL_HINT_EMAIL_ADDRESS});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{View.AUTOFILL_HINT_PASSWORD,
                View.AUTOFILL_HINT_EMAIL_ADDRESS});
    }

    @Test
    public void attachViewToManualContainer() throws Exception {
        runOnUiThreadSync(() -> mFirstLevelManual.requestFocus());
        enableService();

        try {
            View view = new TextView(mActivity);

            view.setLayoutParams(
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

            assertThat(view.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_INHERIT);
            assertThat(view.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);

            // Requesting focus should not trigger any mishaps
            runOnUiThreadSync(() -> view.requestFocus());

            LinearLayout attachmentPoint = (LinearLayout) mActivity.findViewById(
                    R.id.manualContainer);
            runOnUiThreadSync(() -> attachmentPoint.addView(view));

            assertThat(view.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_MANUAL);
        } finally {
            disableService();
        }
    }

    @Test
    public void attachNestedViewToContainer() throws Exception {
        runOnUiThreadSync(() -> mFirstLevelManual.requestFocus());
        enableService();

        try {
            // Create view and viewGroup but do not attach to window
            LinearLayout container = (LinearLayout) mActivity.getLayoutInflater().inflate(
                    R.layout.nested_layout, null);
            EditText field = container.findViewById(R.id.field);

            assertThat(field.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_INHERIT);
            assertThat(container.getAutofillMode()).isEqualTo(View.AUTOFILL_MODE_INHERIT);

            // Resolved mode for detached views should behave as documented
            assertThat(field.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);
            assertThat(container.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);

            // Requesting focus should not trigger any mishaps
            runOnUiThreadSync(() -> field.requestFocus());

            // Set up auto-fill service and response
            sReplier.addResponse(new CannedFillResponse.Builder()
                    .addDataset(new CannedFillResponse.CannedDataset.Builder()
                            .setField("field", "filled")
                            .setPresentation(createPresentation("dataset"))
                            .build())
                    .build());

            OneTimeTextWatcher mViewWatcher = new OneTimeTextWatcher("field", field, "filled");
            field.addTextChangedListener(mViewWatcher);

            // As the focus is set to "field", attaching "container" should trigger an auto-fill
            // request on "field"
            LinearLayout attachmentPoint = (LinearLayout) mActivity.findViewById(
                    R.id.rootContainer);
            runOnUiThreadSync(() -> attachmentPoint.addView(container));

            // Now the resolved auto-fill modes make sense, hence check them
            assertThat(field.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);
            assertThat(container.getResolvedAutofillMode()).isEqualTo(View.AUTOFILL_MODE_AUTO);

            // We should now be able to select the data set
            sReplier.getNextFillRequest();
            sUiBot.selectDataset("dataset");

            // Check if auto-fill operation worked
            mViewWatcher.assertAutoFilled();
        } finally {
            disableService();
        }
    }
}
