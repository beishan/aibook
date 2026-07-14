#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "mobi.h"

#define STATUS_SUCCESS 0
#define STATUS_FILE_MISSING 1
#define STATUS_DRM 2
#define STATUS_UNSUPPORTED 3
#define STATUS_CORRUPTED 4
#define STATUS_NO_SPACE 5
#define STATUS_FAILED 6
#define MAX_INPUT_BYTES (256U * 1024U * 1024U)
#define MAX_TEXT_BYTES (256U * 1024U * 1024U)
#define MAX_PART_BYTES (64U * 1024U * 1024U)
#define MAX_EXPORTED_BYTES (512U * 1024U * 1024U)
#define MAX_PART_COUNT 10000U

static int write_part(const char *directory, const char *prefix, const MOBIPart *part, char *name, size_t name_size) {
    MOBIFileMeta meta = mobi_get_filemeta_by_type(part->type);
    const char *extension = meta.extension[0] != '\0' ? meta.extension : "bin";
    if (snprintf(name, name_size, "%s%05zu.%s", prefix, part->uid, extension) >= (int) name_size) {
        return STATUS_FAILED;
    }
    char path[4096];
    if (snprintf(path, sizeof(path), "%s/%s", directory, name) >= (int) sizeof(path)) {
        return STATUS_FAILED;
    }
    FILE *output = fopen(path, "wb");
    if (output == NULL) return STATUS_NO_SPACE;
    size_t written = fwrite(part->data, 1, part->size, output);
    int close_result = fclose(output);
    return written == part->size && close_result == 0 ? STATUS_SUCCESS : STATUS_NO_SPACE;
}

static void write_optional_file(const char *directory, const char *name, const unsigned char *data, size_t size) {
    if (data == NULL || size == 0 || size > MAX_PART_BYTES) return;
    char path[4096];
    if (snprintf(path, sizeof(path), "%s/%s", directory, name) >= (int) sizeof(path)) return;
    FILE *output = fopen(path, "wb");
    if (output == NULL) return;
    fwrite(data, 1, size, output);
    fclose(output);
}

static void write_metadata(const char *directory, const MOBIData *mobi) {
    char *title = mobi_meta_get_title(mobi);
    char *author = mobi_meta_get_author(mobi);
    char *description = mobi_meta_get_description(mobi);
    if (title != NULL) write_optional_file(directory, "title.txt", (unsigned char *) title, strlen(title));
    if (author != NULL) write_optional_file(directory, "author.txt", (unsigned char *) author, strlen(author));
    if (description != NULL) write_optional_file(directory, "description.txt", (unsigned char *) description, strlen(description));
    free(title);
    free(author);
    free(description);

    MOBIExthHeader *cover = mobi_get_exthrecord_by_tag(mobi, EXTH_COVEROFFSET);
    if (cover == NULL) return;
    uint32_t offset = mobi_decode_exthvalue(cover->data, cover->size);
    size_t sequence = mobi_get_first_resource_record(mobi) + offset;
    MOBIPdbRecord *record = mobi_get_record_by_seqnumber(mobi, sequence);
    if (record == NULL || record->data == NULL || record->size < 4) return;
    const char *extension = "bin";
    if (record->data[0] == 0xff && record->data[1] == 0xd8 && record->data[2] == 0xff) extension = "jpg";
    else if (memcmp(record->data, "GIF8", 4) == 0) extension = "gif";
    else if (record->size >= 8 && memcmp(record->data, "\x89PNG\r\n\x1a\n", 8) == 0) extension = "png";
    else if (memcmp(record->data, "BM", 2) == 0) extension = "bmp";
    char name[32];
    if (snprintf(name, sizeof(name), "cover.%s", extension) < (int) sizeof(name)) {
        write_optional_file(directory, name, record->data, record->size);
    }
}

JNIEXPORT jint JNICALL
Java_com_aibook_android_core_mobi_NativeMobiDocumentParser_nativeParse(
        JNIEnv *env,
        jobject thiz,
        jstring source_path,
        jstring output_directory) {
    (void) thiz;
    if (source_path == NULL || output_directory == NULL) return STATUS_FAILED;

    const char *source = (*env)->GetStringUTFChars(env, source_path, NULL);
    const char *directory = (*env)->GetStringUTFChars(env, output_directory, NULL);
    if (source == NULL || directory == NULL) {
        if (source != NULL) (*env)->ReleaseStringUTFChars(env, source_path, source);
        if (directory != NULL) (*env)->ReleaseStringUTFChars(env, output_directory, directory);
        return STATUS_FAILED;
    }

    int status = STATUS_FAILED;
    FILE *input = NULL;
    FILE *manifest = NULL;
    MOBIData *mobi = NULL;
    MOBIRawml *rawml = NULL;

    input = fopen(source, "rb");
    if (input == NULL) {
        status = STATUS_FILE_MISSING;
        goto cleanup;
    }
    if (fseek(input, 0, SEEK_END) != 0) {
        status = STATUS_CORRUPTED;
        goto cleanup;
    }
    long input_size = ftell(input);
    if (input_size < 0 || (unsigned long) input_size > MAX_INPUT_BYTES || fseek(input, 0, SEEK_SET) != 0) {
        status = STATUS_UNSUPPORTED;
        goto cleanup;
    }
    mobi = mobi_init();
    if (mobi == NULL) {
        status = STATUS_NO_SPACE;
        goto cleanup;
    }
    if (mobi_load_file(mobi, input) != MOBI_SUCCESS) {
        status = STATUS_CORRUPTED;
        goto cleanup;
    }
    fclose(input);
    input = NULL;
    if (mobi_is_encrypted(mobi)) {
        status = STATUS_DRM;
        goto cleanup;
    }
    if (mobi->rh == NULL || mobi->rh->text_length > MAX_TEXT_BYTES) {
        status = STATUS_UNSUPPORTED;
        goto cleanup;
    }
    write_metadata(directory, mobi);

    rawml = mobi_init_rawml(mobi);
    if (rawml == NULL) {
        status = STATUS_NO_SPACE;
        goto cleanup;
    }
    if (mobi_parse_rawml(rawml, mobi) != MOBI_SUCCESS) {
        status = STATUS_CORRUPTED;
        goto cleanup;
    }

    char manifest_path[4096];
    if (snprintf(manifest_path, sizeof(manifest_path), "%s/chapters.manifest", directory) >= (int) sizeof(manifest_path)) {
        status = STATUS_FAILED;
        goto cleanup;
    }
    manifest = fopen(manifest_path, "wb");
    if (manifest == NULL) {
        status = STATUS_NO_SPACE;
        goto cleanup;
    }

    size_t chapter_index = 0;
    size_t exported_bytes = 0;
    size_t part_count = 0;
    for (MOBIPart *part = rawml->markup; part != NULL; part = part->next) {
        if (part->size == 0 || part->data == NULL) continue;
        if (++part_count > MAX_PART_COUNT || part->size > MAX_PART_BYTES ||
            exported_bytes > MAX_EXPORTED_BYTES - part->size) {
            status = STATUS_UNSUPPORTED;
            goto cleanup;
        }
        exported_bytes += part->size;
        char name[128];
        status = write_part(directory, "chapter", part, name, sizeof(name));
        if (status != STATUS_SUCCESS) goto cleanup;
        if (fprintf(manifest, "%zu\t%s\n", chapter_index++, name) < 0) {
            status = STATUS_NO_SPACE;
            goto cleanup;
        }
    }
    for (MOBIPart *part = rawml->resources; part != NULL; part = part->next) {
        if (part->size == 0 || part->data == NULL) continue;
        if (++part_count > MAX_PART_COUNT || part->size > MAX_PART_BYTES ||
            exported_bytes > MAX_EXPORTED_BYTES - part->size) {
            status = STATUS_UNSUPPORTED;
            goto cleanup;
        }
        exported_bytes += part->size;
        char name[128];
        status = write_part(directory, "resource", part, name, sizeof(name));
        if (status != STATUS_SUCCESS) goto cleanup;
    }
    status = chapter_index > 0 ? STATUS_SUCCESS : STATUS_UNSUPPORTED;

cleanup:
    if (manifest != NULL) fclose(manifest);
    if (input != NULL) fclose(input);
    if (rawml != NULL) mobi_free_rawml(rawml);
    if (mobi != NULL) mobi_free(mobi);
    (*env)->ReleaseStringUTFChars(env, source_path, source);
    (*env)->ReleaseStringUTFChars(env, output_directory, directory);
    return status;
}
