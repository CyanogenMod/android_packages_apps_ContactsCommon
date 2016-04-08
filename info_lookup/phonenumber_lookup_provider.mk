CONTACTS_COMMON_PHONE_NUMBER_LOOKUP := ../../../packages/apps/ContactsCommon/info_lookup/src/com/cyanogen/lookup/phonenumber
LOCAL_SRC_FILES += $(call all-java-files-under, $(CONTACTS_COMMON_PHONE_NUMBER_LOOKUP))

LOCAL_STATIC_JAVA_AAR_LIBRARIES += \
    ambientsdk

LOCAL_AAPT_FLAGS += \
    --auto-add-overlay \
    --extra-packages com.cyanogen.ambient
