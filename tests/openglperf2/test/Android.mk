#
# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-subdir-cpp-files)
LOCAL_SRC_FILES += ../jni/graphics/Matrix.cpp

LOCAL_CFLAGS := -Wall -Werror
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../jni/graphics/
LOCAL_STATIC_LIBRARIES := liblog
LOCAL_LDFLAGS:= -g -lpthread
LOCAL_MODULE_HOST_OS := linux
LOCAL_MODULE:= cts_device_opengl_test
include $(BUILD_HOST_NATIVE_TEST)
