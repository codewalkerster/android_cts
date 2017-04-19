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

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"
#include <utils/Log.h>

#include <gtest/gtest.h>

#include <aaudio/AAudio.h>

// Make sure enums do not change value.
TEST(test_aaudio_misc, aaudio_freeze_enums) {

    ASSERT_EQ(0, AAUDIO_DIRECTION_OUTPUT);
    ASSERT_EQ(1, AAUDIO_DIRECTION_INPUT);

    ASSERT_EQ(-1, AAUDIO_FORMAT_INVALID);
    ASSERT_EQ(0, AAUDIO_FORMAT_UNSPECIFIED);
    ASSERT_EQ(1, AAUDIO_FORMAT_PCM_I16);
    ASSERT_EQ(2, AAUDIO_FORMAT_PCM_FLOAT);

    ASSERT_EQ(0, AAUDIO_OK);
    ASSERT_EQ(-900, AAUDIO_ERROR_BASE);
    ASSERT_EQ(-899, AAUDIO_ERROR_DISCONNECTED);
    ASSERT_EQ(-898, AAUDIO_ERROR_ILLEGAL_ARGUMENT);
    ASSERT_EQ(-897, AAUDIO_ERROR_INCOMPATIBLE);
    ASSERT_EQ(-896, AAUDIO_ERROR_INTERNAL);
    ASSERT_EQ(-895, AAUDIO_ERROR_INVALID_STATE);
    ASSERT_EQ(-894, AAUDIO_ERROR_UNEXPECTED_STATE);
    ASSERT_EQ(-893, AAUDIO_ERROR_UNEXPECTED_VALUE);
    ASSERT_EQ(-892, AAUDIO_ERROR_INVALID_HANDLE);
    ASSERT_EQ(-891, AAUDIO_ERROR_INVALID_QUERY);
    ASSERT_EQ(-890, AAUDIO_ERROR_UNIMPLEMENTED);
    ASSERT_EQ(-889, AAUDIO_ERROR_UNAVAILABLE);
    ASSERT_EQ(-888, AAUDIO_ERROR_NO_FREE_HANDLES);
    ASSERT_EQ(-887, AAUDIO_ERROR_NO_MEMORY);
    ASSERT_EQ(-886, AAUDIO_ERROR_NULL);
    ASSERT_EQ(-885, AAUDIO_ERROR_TIMEOUT);
    ASSERT_EQ(-884, AAUDIO_ERROR_WOULD_BLOCK);
    ASSERT_EQ(-883, AAUDIO_ERROR_INVALID_FORMAT);
    ASSERT_EQ(-882, AAUDIO_ERROR_OUT_OF_RANGE);
    ASSERT_EQ(-881, AAUDIO_ERROR_NO_SERVICE);

    ASSERT_EQ(0, AAUDIO_STREAM_STATE_UNINITIALIZED);
    ASSERT_EQ(1, AAUDIO_STREAM_STATE_UNKNOWN);
    ASSERT_EQ(2, AAUDIO_STREAM_STATE_OPEN);
    ASSERT_EQ(3, AAUDIO_STREAM_STATE_STARTING);
    ASSERT_EQ(4, AAUDIO_STREAM_STATE_STARTED);
    ASSERT_EQ(5, AAUDIO_STREAM_STATE_PAUSING);
    ASSERT_EQ(6, AAUDIO_STREAM_STATE_PAUSED);
    ASSERT_EQ(7, AAUDIO_STREAM_STATE_FLUSHING);
    ASSERT_EQ(8, AAUDIO_STREAM_STATE_FLUSHED);
    ASSERT_EQ(9, AAUDIO_STREAM_STATE_STOPPING);
    ASSERT_EQ(10, AAUDIO_STREAM_STATE_STOPPED);
    ASSERT_EQ(11, AAUDIO_STREAM_STATE_CLOSING);
    ASSERT_EQ(12, AAUDIO_STREAM_STATE_CLOSED);

    ASSERT_EQ(0, AAUDIO_SHARING_MODE_EXCLUSIVE);
    ASSERT_EQ(1, AAUDIO_SHARING_MODE_SHARED);

    ASSERT_EQ(0, AAUDIO_CALLBACK_RESULT_CONTINUE);
    ASSERT_EQ(1, AAUDIO_CALLBACK_RESULT_STOP);
}
