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

import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.FatActivity.ID_CAPTCHA;
import static android.autofillservice.cts.FatActivity.ID_IMAGE;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_IMAGE;
import static android.autofillservice.cts.FatActivity.ID_INPUT;
import static android.autofillservice.cts.FatActivity.ID_INPUT_CONTAINER;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_CHILD;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_GRAND_CHILD;
import static android.autofillservice.cts.Helper.assertNumberOfChildren;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.findNodeByText;
import static android.autofillservice.cts.Helper.importantForAutofillAsString;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_AUTO;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_YES;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for an activity containing useless auto-fill data that should be optimized out.
 */
public class FatActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<FatActivity> mActivityRule =
        new AutofillActivityTestRule<FatActivity>(FatActivity.class);

    private FatActivity mFatActivity;
    private AssistStructure mStructure;

    @Before
    public void setActivity() {
        mFatActivity = mActivityRule.getActivity();
    }

    @Test
    public void testNoContainers() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mFatActivity.onInput((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        mStructure = fillRequest.structure;
        mUiBot.assertNoDatasets();

        // TODO: should only have 5 children, but there is an extra
        // TextView that's probably coming from the title. For now we're just ignoring it, but
        // ideally we should change the .xml to exclude it.
        assertNumberOfChildren(fillRequest.structure, 8);

        // Should not have ImageView...
        assertThat(findNodeByResourceId(fillRequest.structure, ID_IMAGE)).isNull();

        // ...unless app developer asked to:
        assertNodeExists(ID_IMPORTANT_IMAGE, IMPORTANT_FOR_AUTOFILL_YES);

        // Should have TextView, even if it does not have id.
        assertNodeWithTextExists("Label with no ID", IMPORTANT_FOR_AUTOFILL_YES);

        // Should not have EditText that was explicitly removed.
        assertThat(findNodeByResourceId(fillRequest.structure, ID_CAPTCHA)).isNull();

        // Make sure container with a resource id was included.
        final ViewNode inputContainer = assertNodeExists(ID_INPUT_CONTAINER,
                IMPORTANT_FOR_AUTOFILL_AUTO);
        assertThat(inputContainer.getChildCount()).isEqualTo(1);
        final ViewNode input = inputContainer.getChildAt(0);
        assertNode(input, IMPORTANT_FOR_AUTOFILL_YES);
        assertThat(input.getIdEntry()).isEqualTo(ID_INPUT);

        // Make sure a non-important container can exclude descendants
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS)).isNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD)).isNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD)).isNull();

        // Make sure an important container can exclude descendants
        assertNodeExists(ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS,
                IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS);
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD)).isNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD)).isNull();

        // Make sure an intermediary descendant can be excluded
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS)).isNull();
        assertNodeExists(ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_CHILD,
                IMPORTANT_FOR_AUTOFILL_YES);
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_GRAND_CHILD)).isNull();
    }

    @Test
    public void testManualRequest() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mFatActivity.onInput((v) -> mFatActivity.getAutofillManager().requestAutofill(v));
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        mStructure = fillRequest.structure;
        mUiBot.assertNoDatasets();

        // TODO: should only have X children, but there is an extra
        // TextView that's probably coming from the title. For now we're just ignoring it, but
        // ideally we should change the .xml to exclude it.
        assertNumberOfChildren(fillRequest.structure, 27);

        // Assert all nodes are present
        assertNodeExists(ID_IMAGE, IMPORTANT_FOR_AUTOFILL_NO);
        assertNodeExists(ID_IMPORTANT_IMAGE, IMPORTANT_FOR_AUTOFILL_YES);

        assertNodeWithTextExists("Label with no ID", IMPORTANT_FOR_AUTOFILL_YES);

        assertNodeExists(ID_CAPTCHA, IMPORTANT_FOR_AUTOFILL_NO);

        final ViewNode inputContainer = assertNodeExists(ID_INPUT_CONTAINER,
                IMPORTANT_FOR_AUTOFILL_AUTO);
        assertThat(inputContainer.getChildCount()).isEqualTo(1);
        final ViewNode input = inputContainer.getChildAt(0);
        assertNode(input, IMPORTANT_FOR_AUTOFILL_YES);
        assertThat(input.getIdEntry()).isEqualTo(ID_INPUT);

        assertNodeExists(ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS,
                IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        assertNodeExists(ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD,
                IMPORTANT_FOR_AUTOFILL_YES);
        assertNodeExists(ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD,
                IMPORTANT_FOR_AUTOFILL_AUTO);

        assertNodeExists(ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS,
                IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS);
        assertNodeExists(ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD,
                IMPORTANT_FOR_AUTOFILL_YES);
        assertNodeExists(ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD,
                IMPORTANT_FOR_AUTOFILL_AUTO);

        assertNodeExists(ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS,
                IMPORTANT_FOR_AUTOFILL_NO);
        assertNodeExists(ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_CHILD,
                IMPORTANT_FOR_AUTOFILL_YES);
        assertNodeExists(ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_GRAND_CHILD,
                IMPORTANT_FOR_AUTOFILL_NO);
    }

    private ViewNode assertNodeExists(String resourceId, int expectedImportantForAutofill) {
        final ViewNode node = findNodeByResourceId(mStructure, resourceId);
        return assertNode(node, resourceId, expectedImportantForAutofill);
    }

    private ViewNode assertNodeWithTextExists(String text, int expectedImportantForAutofill) {
        final ViewNode node = findNodeByText(mStructure, text);
        return assertNode(node, text, expectedImportantForAutofill);
    }

    private ViewNode assertNode(ViewNode node, int expectedImportantForAutofill) {
        return assertNode(node, null, expectedImportantForAutofill);
    }

    private ViewNode assertNode(ViewNode node, String desc, int expectedImportantForAutofill) {
        assertThat(node).isNotNull();
        final String actualMode = importantForAutofillAsString(node.getImportantForAutofill());
        final String expectedMode = importantForAutofillAsString(expectedImportantForAutofill);
        if (desc != null) {
            assertWithMessage("Wrong importantForAutofill mode on %s", desc).that(actualMode)
                    .isEqualTo(expectedMode);
        } else {
            assertThat(actualMode).isEqualTo(expectedMode);
        }

        return node;
    }
}
