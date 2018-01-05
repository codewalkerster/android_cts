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
package com.android.cts.devicepolicy;

/**
 * Tests the DPC transfer functionality for device owner. Testing is done by having two dummy DPCs,
 * CtsTransferOwnerOutgoingApp and CtsTransferOwnerIncomingApp. The former is the current DPC
 * and the latter will be the new DPC after transfer. In order to run the tests from the correct
 * process, first we setup some policies in the client side in CtsTransferOwnerOutgoingApp and then
 * we verify the policies are still there in CtsTransferOwnerIncomingApp.
 */
public class MixedDeviceOwnerTransferTest extends DeviceAndProfileOwnerTransferTest {
    private static final String TRANSFER_DEVICE_OWNER_OUTGOING_TEST =
            "com.android.cts.transferowner.TransferDeviceOwnerOutgoingTest";
    private static final String TRANSFER_DEVICE_OWNER_INCOMING_TEST =
            "com.android.cts.transferowner.TransferDeviceOwnerIncomingTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mHasFeature) {
            setupDeviceOwner(TRANSFER_OWNER_OUTGOING_APK,
                    TRANSFER_OWNER_OUTGOING_TEST_RECEIVER);
            setupTestParameters(mPrimaryUserId, TRANSFER_DEVICE_OWNER_OUTGOING_TEST,
                    TRANSFER_DEVICE_OWNER_INCOMING_TEST);
        }
    }

    private void setupDeviceOwner(String apkName, String adminReceiverClassName) throws Exception {
        installAppAsUser(apkName, mPrimaryUserId);
        setDeviceOwnerOrFail(adminReceiverClassName, mPrimaryUserId);
    }
}