/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.backup.cts;

/**
 * Verifies receiving quotaExceeded() callback on full backup.
 *
 * Uses test app that creates large file and receives the callback.
 * {@link com.android.internal.backup.LocalTransport} is used, it has size quota 25MB.
 */
public class FullBackupQuotaTest extends BaseBackupCtsTest {

    private static final String BACKUP_APP_NAME = "android.backup.app";

    // Should be the same as LocalTransport.FULL_BACKUP_SIZE_QUOTA
    private static final int LOCAL_TRANSPORT_BACKUP_QUOTA = 25 * 1024 * 1024;
    private static final int LOCAL_TRANSPORT_EXCEEDING_FILE_SIZE = 30 * 1024 * 1024;

    public void testQuotaExceeded() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        exec("logcat --clear");
        exec("setprop log.tag." + APP_LOG_TAG +" VERBOSE");
        // Launch test app and create file exceeding limit for local transport
        createTestFileOfSize(BACKUP_APP_NAME, LOCAL_TRANSPORT_EXCEEDING_FILE_SIZE);

        // Request backup and wait for quota exceeded event in logcat
        exec("bmgr backupnow " + BACKUP_APP_NAME);
        assertTrue("Quota exceeded event is not received", waitForLogcat("Quota exceeded!", 10));
    }

    public void testQuotaReported() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        exec("logcat --clear");
        exec("bmgr backupnow " + BACKUP_APP_NAME);
        assertTrue("Quota not reported correctly",
                waitForLogcat("quota is " + LOCAL_TRANSPORT_BACKUP_QUOTA, 10));
    }

}
