/*
 * Copyright 2017 The Android Open Source Project
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

import static android.autofillservice.cts.Helper.assertFillEventForContextCommitted;
import static android.autofillservice.cts.Helper.assertFillEventForFieldsClassification;
import static android.provider.Settings.Secure.AUTOFILL_FEATURE_FIELD_CLASSIFICATION;
import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MAX_FIELD_CLASSIFICATION_IDS_SIZE;
import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MAX_USER_DATA_SIZE;
import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MAX_VALUE_LENGTH;
import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MIN_VALUE_LENGTH;

import static com.google.common.truth.Truth.assertThat;

import android.autofillservice.cts.Helper.FieldClassificationResult;
import android.autofillservice.cts.common.SettingsStateChangerRule;
import android.content.Context;
import android.service.autofill.FillEventHistory.Event;
import android.service.autofill.UserData;
import android.support.test.InstrumentationRegistry;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.widget.EditText;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class FieldsClassificationTest extends AutoFillServiceTestCase {

    private static final Context sContext = InstrumentationRegistry.getContext();

    @ClassRule
    public static final SettingsStateChangerRule sFeatureEnabler =
            new SettingsStateChangerRule(sContext, AUTOFILL_FEATURE_FIELD_CLASSIFICATION, "1");

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMaxFcSizeChanger =
            new SettingsStateChangerRule(sContext,
                    AUTOFILL_USER_DATA_MAX_FIELD_CLASSIFICATION_IDS_SIZE, "10");

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMaxUserSizeChanger =
            new SettingsStateChangerRule(sContext, AUTOFILL_USER_DATA_MAX_USER_DATA_SIZE, "10");

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMinValueChanger =
            new SettingsStateChangerRule(sContext, AUTOFILL_USER_DATA_MIN_VALUE_LENGTH, "5");

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMaxValueChanger =
            new SettingsStateChangerRule(sContext, AUTOFILL_USER_DATA_MAX_VALUE_LENGTH, "50");

    @Rule
    public final AutofillActivityTestRule<GridActivity> mActivityRule =
            new AutofillActivityTestRule<GridActivity>(GridActivity.class);


    private GridActivity mActivity;
    private AutofillManager mAfm;

    @Before
    public void setFixtures() {
        mActivity = mActivityRule.getActivity();
        mAfm = mActivity.getAutofillManager();
    }

    @Test
    public void testFeatureIsEnabled() throws Exception {
        enableService();
        assertThat(mAfm.isFieldClassificationEnabled()).isTrue();

        disableService();
        assertThat(mAfm.isFieldClassificationEnabled()).isFalse();
    }

    @Test
    public void testGetAlgorithm() throws Exception {
        enableService();

        // Check algorithms
        final List<String> names = mAfm.getAvailableFieldClassificationAlgorithms();
        assertThat(names.size()).isAtLeast(1);
        final String defaultAlgorithm = getDefaultAlgorithm();
        assertThat(defaultAlgorithm).isNotEmpty();
        assertThat(names).contains(defaultAlgorithm);

        // Checks invalid service
        disableService();
        assertThat(mAfm.getAvailableFieldClassificationAlgorithms()).isEmpty();
    }

    @Test
    public void testHit_oneUserData_oneDetectableField() throws Exception {
        simpleHitTest(false, null);
    }

    @Test
    public void testHit_invalidAlgorithmIsIgnored() throws Exception {
        // For simplicity's sake, let's assume that name will never be valid..
        String invalidName = " ALGORITHM, Y NO INVALID? ";

        simpleHitTest(true, invalidName);
    }

    @Test
    public void testHit_userDataAlgorithmIsReset() throws Exception {
        simpleHitTest(true, null);
    }

    private void simpleHitTest(boolean setAlgorithm, String algorithm) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final UserData.Builder userData = new UserData.Builder("myId", "FULLY");
        if (setAlgorithm) {
            userData.setFieldClassificationAlgorithm(algorithm, null);
        }
        mAfm.setUserData(userData.build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field = mActivity.getCell(1, 1);
        final AutofillId fieldId = field.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFieldClassificationIds(fieldId)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        mUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field);

        // Simulate user input
        mActivity.setText(1, 1, "fully");

        // Finish context.
        mAfm.commit();

        // Assert results
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(1);
        assertFillEventForFieldsClassification(events.get(0), fieldId, "myId", 1,
                getDefaultAlgorithm());
    }

    @Test
    public void testHit_manyUserData_oneDetectableField_bestMatchIsFirst() throws Exception {
        manyUserData_oneDetectableField(true);
    }

    @Test
    public void testHit_manyUserData_oneDetectableField_bestMatchIsSecond() throws Exception {
        manyUserData_oneDetectableField(false);
    }

    private void manyUserData_oneDetectableField(boolean firstMatch) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mAfm.setUserData(new UserData.Builder("1stId", "Iam1ST").add("2ndId", "Iam2ND").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field = mActivity.getCell(1, 1);
        final AutofillId fieldId = field.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFieldClassificationIds(fieldId)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        mUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field);

        // Simulate user input
        mActivity.setText(1, 1, firstMatch ? "IAM111" : "IAM222");

        // Finish context.
        mAfm.commit();

        // Assert results
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(1);
        final String algorithm = getDefaultAlgorithm();
        final String[] algorithms = { algorithm, algorithm };
        // Best match is 0.66 (4 of 6), worst is 0.5 (3 of 6)
        if (firstMatch) {
            assertFillEventForFieldsClassification(events.get(0), new FieldClassificationResult[] {
                    new FieldClassificationResult(fieldId, new String[] { "1stId", "2ndId" },
                            new float[] { 0.66F, 0.5F }, algorithms)});
        } else {
            assertFillEventForFieldsClassification(events.get(0), new FieldClassificationResult[] {
                    new FieldClassificationResult(fieldId, new String[] { "2ndId", "1stId" },
                            new float[] { 0.66F, 0.5F }, algorithms) });
        }
    }

    @Test
    public void testHit_oneUserData_manyDetectableFields() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mAfm.setUserData(new UserData.Builder("myId", "FULLY").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field1 = mActivity.getCell(1, 1);
        final AutofillId fieldId1 = field1.getAutofillId();
        final EditText field2 = mActivity.getCell(1, 2);
        final AutofillId fieldId2 = field2.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFieldClassificationIds(fieldId1, fieldId2)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        mUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field1);

        // Simulate user input
        mActivity.setText(1, 1, "fully"); // 100%
        mActivity.setText(1, 2, "fooly"); // 60%

        // Finish context.
        mAfm.commit();

        // Assert results
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(1);
        final String algorithm = getDefaultAlgorithm();
        assertFillEventForFieldsClassification(events.get(0),
                new FieldClassificationResult[] {
                        new FieldClassificationResult(fieldId1, "myId", 1.0F, algorithm),
                        new FieldClassificationResult(fieldId2, "myId", 1.0F, algorithm),
                });
    }

    @Test
    public void testHit_manyUserData_manyDetectableFields() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mAfm.setUserData(new UserData.Builder("myId", "FULLY")
                .add("totalMiss", "ZZZZZZZZZZ") // should not have matched any
                .add("otherId", "EMPTY")
                .build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field1 = mActivity.getCell(1, 1);
        final AutofillId fieldId1 = field1.getAutofillId();
        final EditText field2 = mActivity.getCell(1, 2);
        final AutofillId fieldId2 = field2.getAutofillId();
        final EditText field3 = mActivity.getCell(2, 1);
        final AutofillId fieldId3 = field3.getAutofillId();
        final EditText field4 = mActivity.getCell(2, 2);
        final AutofillId fieldId4 = field4.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFieldClassificationIds(fieldId1, fieldId2)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        mUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field1);

        // Simulate user input
        mActivity.setText(1, 1, "fully"); // u1: 100% u2:  20%
        mActivity.setText(1, 2, "empty"); // u1:  20% u2: 100%
        mActivity.setText(2, 1, "fooly"); // u1:  60% u2:  20%
        mActivity.setText(2, 2, "emppy"); // u1:  20% u2:  80%

        // Finish context.
        mAfm.commit();

        // Assert results
        final String algorithm = getDefaultAlgorithm();
        final String[] algorithms = { algorithm, algorithm };
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(1);
        assertFillEventForFieldsClassification(events.get(0),
                new FieldClassificationResult[] {
                        new FieldClassificationResult(fieldId1, new String[] { "myId", "otherId" },
                                new float[] { 1.0F, 0.2F }, algorithms),
                        new FieldClassificationResult(fieldId2, new String[] { "otherId", "myId" },
                                new float[] { 1.0F, 0.2F }, algorithms),
                        new FieldClassificationResult(fieldId3, new String[] { "myId", "otherId" },
                                new float[] { 0.6F, 0.2F }, algorithms),
                        new FieldClassificationResult(fieldId4, new String[] { "otherId", "myId"},
                                new float[] { 0.80F, 0.2F }, algorithms)});
    }

    @Test
    public void testMiss() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mAfm.setUserData(new UserData.Builder("myId", "ABCDEF").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field = mActivity.getCell(1, 1);
        final AutofillId fieldId = field.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFieldClassificationIds(fieldId)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        mUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field);

        // Simulate user input
        mActivity.setText(1, 1, "xyz");

        // Finish context.
        mAfm.commit();

        // Assert results
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(1);
        assertFillEventForContextCommitted(events.get(0));
    }

    @Test
    public void testNoUserInput() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mAfm.setUserData(new UserData.Builder("myId", "FULLY").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field = mActivity.getCell(1, 1);
        final AutofillId fieldId = field.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFieldClassificationIds(fieldId)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        mUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field);

        // Finish context.
        mAfm.commit();

        // Assert results
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(1);
        assertFillEventForContextCommitted(events.get(0));
    }

    private String getDefaultAlgorithm() {
        return mAfm.getDefaultFieldClassificationAlgorithm();
    }

    /*
     * TODO(b/70407264): other scenarios:
     *
     * - Multipartition (for example, one response with FieldsDetection, others with datasets,
     *   saveinfo, and/or ignoredIds)
     * - make sure detectable fields don't trigger a new partition
     * v test partial hit (for example, 'fool' instead of 'full'
     * v multiple fields
     * v multiple value
     * - combinations of above items
     */
}
