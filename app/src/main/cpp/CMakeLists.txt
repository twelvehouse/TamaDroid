cmake_minimum_required(VERSION 3.22.1)
project(tama C)

# TamaLib core (GPLv2) — compiled unmodified, plus the JNI/HAL bridge.
add_library(tama SHARED
    tama_jni.c
    tamalib/tamalib.c
    tamalib/cpu.c
    tamalib/hw.c
)

# hal.h includes "../hal_types.h"; cpu/hw/tamalib headers live in tamalib/.
target_include_directories(tama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/tamalib
)

# Android system log for __android_log_print.
find_library(log-lib log)
target_link_libraries(tama ${log-lib})
