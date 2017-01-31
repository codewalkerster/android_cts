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

import static android.autofillservice.cts.LoginActivity.ID_PASSWORD;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class LoginActivityTest extends AutoFillServiceTestCase {
    private static final boolean FALSE = false;

    @Rule
    public final ActivityTestRule<LoginActivity> mActivityRule =
        new ActivityTestRule<LoginActivity>(LoginActivity.class);

    private LoginActivity mLoginActivity;

    @Before
    public void setActivity() {
        mLoginActivity = mActivityRule.getActivity();
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        enableService();

        final CannedDataset.Builder dataset = new CannedDataset.Builder("The Dude");
        mLoginActivity.expectAutoFill(dataset, "dude", "sweet");

        InstrumentedAutoFillService.setFillResponse(new CannedFillResponse.Builder()
                .addDataset(dataset.build())
                .build());

        sUiBot.triggerImeByRelativeId(ID_USERNAME);

        // TODO(b/33197203): Add this logic back in the test.
        // Make sure tapping on other fields from the dataset does not trigger it again
        if (FALSE) {
            sUiBot.tapByRelativeId(ID_PASSWORD);
            sUiBot.tapByRelativeId(ID_USERNAME);
        }

        sUiBot.selectDataset("The Dude");

        mLoginActivity.assertAutoFilled();
        InstrumentedAutoFillService.assertNumberFillRequests(1);
    }

    /*
     * TODO(b/33197203, b/33802548): test other scenarios
     *
     *  - no dataset
     *  - multiple datasets
     *  - partitioned datasets (i.e., multiple fields)
     *  - response-level authentication (custom and fingerprint)
     *  - dataset-level authentication (custom and fingerprint)
     *
     *  Save:
     *  - when no dataset is returned initially
     *  - when a dataset is returned initially
     *  - make sure password is set
     *  - test cases where non-savable-ids only are changed
     *
     *  Other assertions:
     *  - illegal state thrown on callback calls
     *  - system server state after calls (for example, no pending callback)
     */
}
