LOCAL_PATH := $(call my-dir)
LIBMOBI_PATH := $(LOCAL_PATH)/../../../../../third_party/libmobi/src

include $(CLEAR_VARS)
LOCAL_MODULE := aibook_mobi
LOCAL_C_INCLUDES := $(LIBMOBI_PATH)
LOCAL_CFLAGS := -std=c99 -DPACKAGE_VERSION=\"0.12\" -DUSE_MINIZ -DMOBI_INLINE=inline \
    -DHAVE_STRDUP -DHAVE_UNISTD_H -D_POSIX_C_SOURCE=200112L
LOCAL_SRC_FILES := \
    mobi_jni.c \
    $(LIBMOBI_PATH)/buffer.c \
    $(LIBMOBI_PATH)/compression.c \
    $(LIBMOBI_PATH)/debug.c \
    $(LIBMOBI_PATH)/index.c \
    $(LIBMOBI_PATH)/memory.c \
    $(LIBMOBI_PATH)/meta.c \
    $(LIBMOBI_PATH)/miniz.c \
    $(LIBMOBI_PATH)/parse_rawml.c \
    $(LIBMOBI_PATH)/read.c \
    $(LIBMOBI_PATH)/structure.c \
    $(LIBMOBI_PATH)/util.c \
    $(LIBMOBI_PATH)/write.c \
    $(LIBMOBI_PATH)/xmlwriter.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
