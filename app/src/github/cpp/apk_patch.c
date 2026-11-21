#include <errno.h>
#include <jni.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

#include "hdiffpatch/libHDiffPatch/HPatch/patch.h"
#include "hdiffpatch/decompress_plugin_demo.h"

static hpatch_BOOL read_at(const hpatch_TStreamInput *stream, hpatch_StreamPos_t offset,
                           unsigned char *data, unsigned char *end) {
    const int fd = *(const int *)stream->streamImport;
    if (offset > stream->streamSize || (size_t)(end - data) > stream->streamSize - offset) {
        return hpatch_FALSE;
    }
    while (data < end) {
        ssize_t count = pread(fd, data, end - data, (off_t)offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return hpatch_FALSE;
        data += count;
        offset += count;
    }
    return hpatch_TRUE;
}

static hpatch_BOOL write_at(const hpatch_TStreamOutput *stream, hpatch_StreamPos_t offset,
                            const unsigned char *data, const unsigned char *end) {
    const int fd = *(const int *)stream->streamImport;
    if (offset > stream->streamSize || (size_t)(end - data) > stream->streamSize - offset) {
        return hpatch_FALSE;
    }
    while (data < end) {
        ssize_t count = pwrite(fd, data, end - data, (off_t)offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return hpatch_FALSE;
        data += count;
        offset += count;
    }
    return hpatch_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_me_rerere_rikkahub_data_update_ApkPatcher_apply(
        JNIEnv *env, jobject self, jint base_fd, jint patch_fd, jint output_fd, jlong output_size) {
    struct stat base_stat, patch_stat;
    if (output_size <= 0 || fstat(base_fd, &base_stat) || fstat(patch_fd, &patch_stat)
        || base_stat.st_size <= 0 || patch_stat.st_size <= 0) {
        return JNI_FALSE;
    }

    const hpatch_TStreamInput base = { &base_fd, base_stat.st_size, read_at, NULL };
    const hpatch_TStreamInput diff = { &patch_fd, patch_stat.st_size, read_at, NULL };
    const hpatch_TStreamOutput output = { &output_fd, output_size, NULL, write_at };
    hpatch_compressedDiffInfo info;
    if (!getCompressedDiffInfo(&info, &diff) || info.oldDataSize != base.streamSize
        || info.newDataSize != output.streamSize
        || (info.compressedCount && !zlibDecompressPlugin.is_can_open(info.compressType))) {
        return JNI_FALSE;
    }

    const size_t cache_size = 1024 * 1024;
    unsigned char *cache = malloc(cache_size);
    if (!cache) return JNI_FALSE;
    hpatch_TDecompress zlib = zlibDecompressPlugin;
    hpatch_BOOL success = patch_decompress_with_cache(&output, &base, &diff, &zlib,
                                                     cache, cache + cache_size);
    free(cache);
    return success && zlib.decError == hpatch_dec_ok ? JNI_TRUE : JNI_FALSE;
}
