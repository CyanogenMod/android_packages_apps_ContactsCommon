LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    com.android.contacts.common.test

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := com.android.contacts.common.unittest

LOCAL_INSTRUMENTATION_FOR := com.android.contacts.common

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
