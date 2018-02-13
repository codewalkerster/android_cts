LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

# Don't include this package in any target.
LOCAL_MODULE_TAGS := tests

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    android-support-v4 \

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_MODULE := cts-am-app-base

include $(BUILD_STATIC_JAVA_LIBRARY)