cmake_minimum_required(VERSION 3.22.1)
project(rikka_updates C)

if(RIKKA_APK_UPDATES)
    add_library(rikka_apk_patch SHARED apk_patch.c hdiffpatch/libHDiffPatch/HPatch/patch.c)
    target_compile_definitions(rikka_apk_patch PRIVATE
        _IS_USED_MULTITHREAD=0
        _IS_NEED_CACHE_OLD_BY_COVERS=0
        _IS_NEED_CACHE_OLD_ALL=0
        _CompressPlugin_zlib
    )
    target_compile_options(rikka_apk_patch PRIVATE -fvisibility=hidden)
    target_link_options(rikka_apk_patch PRIVATE "-Wl,-z,max-page-size=16384")
    target_link_libraries(rikka_apk_patch PRIVATE z log)
endif()
