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

import static android.autofillservice.cts.Helper.assertText;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.LoginActivity.AUTHENTICATION_MESSAGE;
import static android.autofillservice.cts.LoginActivity.ID_PASSWORD;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME;
import static android.autofillservice.cts.LoginActivity.getWelcomeMessage;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.autofillservice.cts.InstrumentedAutoFillService.*;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.view.autofill.AutoFillValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import android.autofillservice.cts.R;

@SmallTest
public class LoginActivityTest extends AutoFillServiceTestCase {

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
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectation.
        replier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());

        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectByText("The Dude");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Sanity checks

        // Make sure input was sanitized.
        final FillRequest request = replier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();

        // Other sanity checks
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndMoveFocusAround() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectation.
        replier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());

        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mLoginActivity.onPassword((v) -> { v.requestFocus(); });
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });

        // Auto-fill it.
        sUiBot.selectByText("The Dude");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mLoginActivity.onPassword((v) -> { v.requestFocus(); });
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });

        // Sanity checks
        replier.assertNumberUnhandledFillRequests(1);
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndSave() throws Exception {
        enableService();

        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectation.
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");

        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                        .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .setExtras(extras)
                .build());

        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectByText("The Dude");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Try to login, it will fail.
        final String loginMessage = mLoginActivity.tapLogin();

        assertWithMessage("Wrong login msg").that(loginMessage).isEqualTo(AUTHENTICATION_MESSAGE);

        // Set right password...
        mLoginActivity.onPassword((v) -> { v.setText("dude"); });

        // ... and try again
        final String expectedMessage = getWelcomeMessage("dude");
        final String actualMessage = mLoginActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()
        sUiBot.saveForAutofill(true);

        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertText(username, "dude");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertText(password, "dude");

        // Make sure extras were passed back on onSave()
        assertThat(saveRequest.data).isNotNull();
        final String extraValue = saveRequest.data.getString("numbers");
        assertWithMessage("extras not passed on save").that(extraValue).isEqualTo("4815162342");

        // Sanity check: make sure service was called just once.
        replier.assertNumberUnhandledFillRequests(1);
        replier.assertNumberUnhandledSaveRequests(0);

        // Sanity check: once saved, the session should be finsihed
        assertNoDanglingSessions();

        // Other sanity checks
        waitUntilDisconnected();
    }

    @Test
    public void testSaveOnly() throws Exception {
        enableService();

        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectation.
        replier.addResponse(new CannedFillResponse.Builder()
                .setSavableIds(ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        replier.getNextFillRequest();

        // TODO(b/33197203, b/33802548): assert auto-fill bar was not shown

        // Set credentials...
        mLoginActivity.onUsername((v) -> { v.setText(""); v.setText("malkovich"); });
        mLoginActivity.onPassword((v) -> { v.setText(""); v.setText("malkovich"); });

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mLoginActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()
        sUiBot.saveForAutofill(true);

        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertText(username, "malkovich");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertText(password, "malkovich");

        // Sanity check: make sure service was called just once.
        replier.assertNumberUnhandledFillRequests(0);
        replier.assertNumberUnhandledSaveRequests(0);

        // Sanity check: once saved, the session should be finsihed
        assertNoDanglingSessions();

        // Other sanity checks
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndSaveWhenFlagSecure() throws Exception {
        mLoginActivity.setFlags(FLAG_SECURE);
        testAutoFillOneDatasetAndSave();
    }

    @Test
    public void testAutoFillOneDatasetWhenFlagSecure() throws Exception {
        mLoginActivity.setFlags(FLAG_SECURE);
        testAutoFillOneDataset();
    }

    @Test
    public void testFillResponseAuth() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Prepare the authenticated response
        AuthenticationActivity.setResponse(
                new CannedFillResponse.Builder()
            .addDataset(new CannedDataset.Builder()
                    .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                    .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                    .setPresentation(createPresentation("Dataset"))
                    .build())
            .build());

        // Create the authentication intent
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        replier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication)
                .setPresentation(createPresentation("Auth"))
                .build());

        // Set expectation for the activity
        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait for onFill() before proceeding.
        replier.getNextFillRequest();

        // Authenticate
        sUiBot.selectByText("Auth");

        // Select the dataset
        sUiBot.selectByText("Dataset");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Other sanity checks
        waitUntilDisconnected();
    }

    @Test
    public void testDatasetAuth() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Prepare the authenticated response
        AuthenticationActivity.setDataset(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                .setPresentation(createPresentation("Dataset"))
                .build());

        // Create the authentication intent
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                        .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                        .setPresentation(createPresentation("Auth"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait for onFill() before proceeding.
        replier.getNextFillRequest();

        // Authenticate
        sUiBot.selectByText("Auth");

        // Select the dataset
        sUiBot.selectByText("Dataset");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Other sanity checks
        waitUntilDisconnected();
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
     *  - test case where 'no thanks' is tapped
     *  - make sure snack bar times out (will require a shell cmd to change timeout)
     *  - text value of extras
     *
     *  Other assertions:
     *  - illegal state thrown on callback calls
     *  - system server state after calls (for example, no pending callback)
     *  - make sure there is no dangling session using 'cmd autofill list sessions'
     */
}
