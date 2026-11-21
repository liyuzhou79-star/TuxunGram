cmake_minimum_required(VERSION 3.22.1)
project(tuxungram_jni)

# Prebuilt TDLib (built by the Build TDLib workflow, placed into
# jniLibs by the app build workflow).
add_library(tdjson SHARED IMPORTED)
set_target_properties(tdjson PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libtdjson.so)

add_library(tuxungram_jni SHARED tuxungram_jni.cpp)
target_link_libraries(tuxungram_jni tdjson log)
