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

import static android.autofillservice.cts.Helper.SAVE_TIMEOUT_MS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.content.res.Resources;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;

/**
 * Helper for UI-related needs.
 */
final class UiBot {

    private static final String RESOURCE_ID_DATASET_PICKER = "autofill_dataset_picker";
    private static final String RESOURCE_ID_SAVE_SNACKBAR = "autofill_save";
    private static final String RESOURCE_ID_SAVE_TITLE = "autofill_save_title";
    private static final String RESOURCE_ID_CONTEXT_MENUITEM = "floating_toolbar_menu_item_text";

    private static final String RESOURCE_STRING_SAVE_TITLE = "autofill_save_title";
    private static final String RESOURCE_STRING_SAVE_TITLE_WITH_TYPE =
            "autofill_save_title_with_type";
    private static final String RESOURCE_STRING_SAVE_TYPE_PASSWORD = "autofill_save_type_password";
    private static final String RESOURCE_STRING_SAVE_TYPE_ADDRESS = "autofill_save_type_address";
    private static final String RESOURCE_STRING_SAVE_TYPE_CREDIT_CARD =
            "autofill_save_type_credit_card";
    private static final String RESOURCE_STRING_AUTOFILL = "autofill";

    private static final String TAG = "AutoFillCtsUiBot";

    private final UiDevice mDevice;
    private final long mTimeout;
    private final String mPackageName;

    UiBot(Instrumentation instrumentation, long timeout) throws Exception {
        mDevice = UiDevice.getInstance(instrumentation);
        mTimeout = timeout;
        mPackageName = instrumentation.getContext().getPackageName();
    }

    /**
     * Asserts the dataset chooser is not shown.
     */
    void assertNoDatasets() {
        final UiObject2 ui;
        try {
            ui = waitForObject(By.res("android", RESOURCE_ID_DATASET_PICKER));
        } catch (Throwable t) {
            // TODO(b/33197203): use a more elegant check than catching the expection because it's
            // not showing...
            return;
        }
        throw new AssertionError("floating ui is shown: " + ui);
    }

    /**
     * Asserts the dataset chooser is shown and contains the given datasets.
     */
    void assertDatasets(String...names) {
        final UiObject2 picker = waitForObject(By.res("android", RESOURCE_ID_DATASET_PICKER));

        for (String name : names) {
            final UiObject2 dataset = picker.findObject(By.text(name));
            assertWithMessage("no dataset named %s", name).that(dataset).isNotNull();
        }
    }

    /**
     * Selects a dataset that should be visible in the floating UI.
     */
    void selectDataset(String name) {
        final UiObject2 picker = waitForObject(By.res("android", RESOURCE_ID_DATASET_PICKER));
        final UiObject2 dataset = picker.findObject(By.text(name));
        assertWithMessage("no dataset named %s", name).that(dataset).isNotNull();
        dataset.click();
    }

    /**
     * Selects a view by text.
     */
    void selectByText(String name) {
        Log.v(TAG, "selectByText(): " + name);

        final UiObject2 dataset = waitForObject(By.text(name));
        dataset.click();
    }

    /**
     * Checks if a View with a certain text exists.
     */
    boolean hasViewWithText(String name) {
        Log.v(TAG, "hasViewWithText(): " + name);

        return mDevice.findObject(By.text(name)) != null;
    }

    /**
     * Asserts the save snackbar is showing and returns it.
     */
    UiObject2 assertSaveShowing(int type) {
        return assertSaveShowing(type, null);
    }

    /**
     * Asserts the save snackbar is not showing and returns it.
     */
    void assertSaveNotShowing(int type) {
        try {
            assertSaveShowing(type);
        } catch (Throwable t) {
            // TODO(b/33197203): use a more elegant check than catching the expection because it's
            // not showing (in which case it wouldn't need a type as parameter).
            return;
        }
        throw new AssertionError("snack bar is showing");
    }

    UiObject2 assertSaveShowing(int type, String description) {
        final UiObject2 snackbar = waitForObject(By.res("android", RESOURCE_ID_SAVE_SNACKBAR),
                SAVE_TIMEOUT_MS);

        final UiObject2 titleView = snackbar.findObject(By.res("android", RESOURCE_ID_SAVE_TITLE));
        assertWithMessage("save title (%s)", RESOURCE_ID_SAVE_TITLE).that(titleView).isNotNull();

        final Resources resources = InstrumentationRegistry.getContext().getResources();
        final String serviceLabel = InstrumentedAutoFillService.class.getSimpleName();
        final String expectedTitle;
        if (type == SAVE_DATA_TYPE_GENERIC) {
            final int titleId = resources.getIdentifier(RESOURCE_STRING_SAVE_TITLE, "string",
                    "android");
            expectedTitle = resources.getString(titleId, serviceLabel);
        } else {
            final String typeResourceName;
            switch (type) {
                case SAVE_DATA_TYPE_PASSWORD:
                    typeResourceName = RESOURCE_STRING_SAVE_TYPE_PASSWORD;
                    break;
                case SAVE_DATA_TYPE_ADDRESS:
                    typeResourceName = RESOURCE_STRING_SAVE_TYPE_ADDRESS;
                    break;
                case SAVE_DATA_TYPE_CREDIT_CARD:
                    typeResourceName = RESOURCE_STRING_SAVE_TYPE_CREDIT_CARD;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported type: " + type);
            }
            final int typeId = resources.getIdentifier(typeResourceName, "string", "android");
            final String typeString = resources.getString(typeId);
            final int titleId = resources.getIdentifier(RESOURCE_STRING_SAVE_TITLE_WITH_TYPE,
                    "string", "android");
            expectedTitle = resources.getString(titleId, typeString, serviceLabel);
        }

        final String actualTitle = titleView.getText();
        Log.d(TAG, "save title: " + actualTitle);
        assertThat(actualTitle).isEqualTo(expectedTitle);

        if (description != null) {
            final UiObject2 saveSubTitle = snackbar.findObject(By.text(description));
            assertWithMessage("save subtitle(%s)", description).that(saveSubTitle).isNotNull();
        }

        return snackbar;
    }

    /**
     * Taps an option in the save snackbar.
     *
     * @param type expected type of save info.
     * @param yesDoIt {@code true} for 'YES', {@code false} for 'NO THANKS'.
     */
    void saveForAutofill(int type, boolean yesDoIt) {
        final UiObject2 saveSnackBar = assertSaveShowing(type, null);
        saveForAutofill(saveSnackBar, yesDoIt);
    }

    /**
     * Taps an option in the save snackbar.
     *
     * @param saveSnackBar Save snackbar, typically obtained through
     *            {@link #assertSaveShowing(int)}.
     * @param yesDoIt {@code true} for 'YES', {@code false} for 'NO THANKS'.
     */
    void saveForAutofill(UiObject2 saveSnackBar, boolean yesDoIt) {
        final String id = yesDoIt ? "autofill_save_yes" : "autofill_save_no";

        final UiObject2 button = saveSnackBar.findObject(By.res("android", id));
        assertWithMessage("save button (%s)", id).that(button).isNotNull();
        button.click();
    }

    /**
     * Gets the AUTOFILL contextual menu by long pressing a text field.
     *
     * @param id resource id of the field.
     */
    UiObject2 getAutofillMenuOption(String id) {
        final UiObject2 field = waitForObject(By.res(mPackageName, id));
        // TODO(b/33197203, b/33802548): figure out why obj.longClick() doesn't always work
        field.click(3000);

        final UiObject2 menuItem = waitForObject(By.res("android", RESOURCE_ID_CONTEXT_MENUITEM));
        final Resources resources = InstrumentationRegistry.getContext().getResources();
        final int stringId = resources.getIdentifier(RESOURCE_STRING_AUTOFILL, "string", "android");
        final String expectedText = resources.getString(stringId);
        assertThat(menuItem.getText().toUpperCase()).isEqualTo(expectedText.toUpperCase());
        return menuItem;
    }

    /**
     * Waits for and returns an object.
     *
     * @param selector {@link BySelector} that identifies the object.
     */
    private UiObject2 waitForObject(BySelector selector) {
        return waitForObject(selector, mTimeout);
    }

    /**
     * Waits for and returns an object.
     *
     * @param selector {@link BySelector} that identifies the object.
     * @param timeout timeout in ms
     */
    private UiObject2 waitForObject(BySelector selector, long timeout) {
        // NOTE: mDevice.wait does not work for the save snackbar, so we need a polling approach.
        final int maxTries = 5;
        final long napTime = timeout / maxTries;
        for (int i = 1; i <= maxTries; i++) {
            final UiObject2 uiObject = mDevice.findObject(selector);
            if (uiObject != null) {
                return uiObject;
            }
            SystemClock.sleep(napTime);
        }
        throw new AssertionError("Object with selector " + selector + " not found in "
                + mTimeout + " ms");
    }
}
