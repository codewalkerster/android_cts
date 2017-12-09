/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.app.slice.Slice;
import android.app.slice.Slice.Builder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class SliceProvider extends android.app.slice.SliceProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Slice onBindSlice(Uri sliceUri) {
        switch (sliceUri.getPath()) {
            case "/set_flag":
                SliceTest.sFlag = true;
                break;
            case "/subslice":
                Builder b = new Builder(sliceUri);
                return b.addSubSlice(new Slice.Builder(b).build(), "subslice").build();
            case "/text":
                return new Slice.Builder(sliceUri).addText("Expected text", "text").build();
            case "/icon":
                return new Slice.Builder(sliceUri).addIcon(
                        Icon.createWithResource(getContext(), R.drawable.size_48x48), "icon").build();
            case "/action":
                Builder builder = new Builder(sliceUri);
                Slice subSlice = new Slice.Builder(builder).build();
                PendingIntent broadcast = PendingIntent.getBroadcast(getContext(), 0,
                        new Intent(getContext().getPackageName() + ".action"), 0);
                return builder.addAction(broadcast, subSlice, "action").build();
            case "/color":
                return new Slice.Builder(sliceUri).addColor(0xff121212, "color").build();
            case "/timestamp":
                return new Slice.Builder(sliceUri).addTimestamp(43, "timestamp").build();
            case "/hints":
                return new Slice.Builder(sliceUri)
                        .addHints(Slice.HINT_LIST)
                        .addText("Text", null, Slice.HINT_TITLE)
                        .addIcon(Icon.createWithResource(getContext(), R.drawable.size_48x48),
                                null, Slice.HINT_NO_TINT, Slice.HINT_LARGE)
                        .build();
            case "/bundle":
                Bundle b1 = new Bundle();
                b1.putParcelable("a", new TestParcel());
                return new Slice.Builder(sliceUri).addBundle(b1, "bundle").build();
        }
        return new Slice.Builder(sliceUri).build();
    }

    public static class TestParcel implements Parcelable {

        private final int mValue;

        public TestParcel() {
            mValue = 42;
        }

        protected TestParcel(Parcel in) {
            mValue = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mValue);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                TestParcel p = (TestParcel) obj;
                return p.mValue == mValue;
            } catch (ClassCastException e) {
                return false;
            }
        }

        public static final Creator<TestParcel> CREATOR = new Creator<TestParcel>() {
            @Override
            public TestParcel createFromParcel(Parcel in) {
                return new TestParcel(in);
            }

            @Override
            public TestParcel[] newArray(int size) {
                return new TestParcel[size];
            }
        };
    }
}
