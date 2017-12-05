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
 * limitations under the License
 */

package com.android.cts.mockime;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * An immutable data store to control the behavior of {@link MockIme}.
 */
public class ImeSettings {

    @NonNull
    private final String mEventCallbackActionName;

    private static final String FULLSCREEN_MODE_ALLOWED = "FullscreenModeAllowed";

    @NonNull
    private final PersistableBundle mBundle;

    ImeSettings(@NonNull Parcel parcel) {
        mEventCallbackActionName = parcel.readString();
        mBundle = parcel.readPersistableBundle();
    }

    @Nullable
    String getEventCallbackActionName() {
        return mEventCallbackActionName;
    }

    public boolean fullscreenModeAllowed(boolean defaultValue) {
        return mBundle.getBoolean(FULLSCREEN_MODE_ALLOWED, defaultValue);
    }

    static void writeToParcel(@NonNull Parcel parcel, @NonNull String eventCallbackActionName,
            @Nullable Builder builder) {
        parcel.writeString(eventCallbackActionName);
        if (builder != null) {
            parcel.writePersistableBundle(builder.mBundle);
        } else {
            parcel.writePersistableBundle(PersistableBundle.EMPTY);
        }
    }

    /**
     * The builder class for {@link ImeSettings}.
     */
    public static final class Builder {
        private final PersistableBundle mBundle = new PersistableBundle();

        /**
         * Controls whether fullscreen mode is allowed or not.
         *
         * <p>By default, fullscreen mode is not allowed in {@link MockIme}.</p>
         *
         * @param allowed {@code true} if fullscreen mode is allowed
         * @see MockIme#onEvaluateFullscreenMode()
         */
        public Builder setFullscreenModeAllowed(boolean allowed) {
            mBundle.putBoolean(FULLSCREEN_MODE_ALLOWED, allowed);
            return this;
        }
    }
}
