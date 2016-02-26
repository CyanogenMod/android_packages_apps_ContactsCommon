PROVIDER_OVERLAY_DIR := ../../../vendor/extra/overlays/cyngn/packages/apps/ContactsCommon/info_lookup/src/com/cyanogen/lookup/phonenumber/provider

LOCAL_SRC_FILES += $(call all-java-files-under, $(PROVIDER_OVERLAY_DIR))

LOCAL_STATIC_JAVA_AAR_LIBRARIES += \
    ambientsdk

LOCAL_AAPT_FLAGS += \
    --auto-add-overlay \
    --extra-packages com.cyanogen.ambient