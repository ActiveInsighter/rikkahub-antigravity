package me.rerere.rikkahub.data.update

import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import java.io.File

@Keep
internal object ApkPatcher {
    fun reconstruct(base: File, patch: File, output: File, outputSize: Long) {
        System.loadLibrary("rikka_apk_patch")
        val writeMode = ParcelFileDescriptor.MODE_WRITE_ONLY or
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        ParcelFileDescriptor.open(base, ParcelFileDescriptor.MODE_READ_ONLY).use { old ->
            ParcelFileDescriptor.open(patch, ParcelFileDescriptor.MODE_READ_ONLY).use { diff ->
                ParcelFileDescriptor.open(output, writeMode).use { new ->
                    check(apply(old.fd, diff.fd, new.fd, outputSize)) { "APK patch failed" }
                }
            }
        }
    }

    private external fun apply(baseFd: Int, patchFd: Int, outputFd: Int, outputSize: Long): Boolean
}
