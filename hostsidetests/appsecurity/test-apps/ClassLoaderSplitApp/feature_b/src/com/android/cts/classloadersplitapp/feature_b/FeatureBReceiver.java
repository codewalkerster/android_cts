/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.android.cts.classloadersplitapp.feature_b;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.cts.classloadersplitapp.feature_a.FeatureAReceiver;

public class FeatureBReceiver extends FeatureAReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle b = getResultExtras(true);

        // Figure out the classloader that loaded this class and also
        // its parent loader.
        final ClassLoader loader = getClass().getClassLoader();
        final ClassLoader parent = loader.getParent();

        b.putString("featureB_loaderClassName", loader.getClass().getName());
        b.putString("featureB_parentClassName", parent.getClass().getName());
    }
}
