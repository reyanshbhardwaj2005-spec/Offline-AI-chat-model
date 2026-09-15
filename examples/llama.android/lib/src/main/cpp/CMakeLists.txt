cmake_minimum_required(VERSION 3.31.6)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED TRUE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED TRUE)

# --------------------------------------------------------------------------
# Android / CPU configuration
# --------------------------------------------------------------------------

if(DEFINED ANDROID_ABI)

    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")

    if(ANDROID_ABI STREQUAL "arm64-v8a")

        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)

    elseif(ANDROID_ABI STREQUAL "x86_64")

        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)

    else()

        message(FATAL_ERROR
                "Unsupported ABI: ${ANDROID_ABI}")

    endif()

endif()

# --------------------------------------------------------------------------
# llama.cpp source
# --------------------------------------------------------------------------

set(LLAMA_SRC
        ${CMAKE_CURRENT_LIST_DIR}/../../../../../../
)

message(STATUS "llama.cpp source: ${LLAMA_SRC}")

# --------------------------------------------------------------------------
# llama.cpp build options
# --------------------------------------------------------------------------

# FORCE is important.
# CMake may already have cached these values from previous configurations.

set(LLAMA_BUILD_COMMON ON
        CACHE BOOL "Build llama common" FORCE)

set(LLAMA_BUILD_TOOLS OFF
        CACHE BOOL "Do not build llama tools" FORCE)

set(LLAMA_BUILD_MTMD ON
        CACHE BOOL "Build multimodal mtmd library" FORCE)

# MTMD does not need video/ffmpeg for our use case.
set(MTMD_VIDEO OFF
        CACHE BOOL "Disable MTMD video support" FORCE)

# --------------------------------------------------------------------------
# Build llama.cpp
# --------------------------------------------------------------------------

add_subdirectory(
        ${LLAMA_SRC}
        ${CMAKE_CURRENT_BINARY_DIR}/build-llama
)

# --------------------------------------------------------------------------
# Make sure MTMD actually exists
# --------------------------------------------------------------------------

if(NOT TARGET mtmd)

    message(STATUS "MTMD target was not created by llama.cpp.")
    message(STATUS "Adding tools/mtmd explicitly.")

    add_subdirectory(
            ${LLAMA_SRC}/tools/mtmd
            ${CMAKE_CURRENT_BINARY_DIR}/mtmd-build
    )

endif()

# --------------------------------------------------------------------------
# Our Android JNI library
# --------------------------------------------------------------------------

add_library(
        ${CMAKE_PROJECT_NAME}
        SHARED
        ai_chat.cpp
)

# --------------------------------------------------------------------------
# Compiler definitions
# --------------------------------------------------------------------------

target_compile_definitions(
        ${CMAKE_PROJECT_NAME}
        PRIVATE

        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}

        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>

        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

# --------------------------------------------------------------------------
# Include directories
# --------------------------------------------------------------------------

target_include_directories(
        ${CMAKE_PROJECT_NAME}
        PRIVATE

        ${LLAMA_SRC}

        ${LLAMA_SRC}/common

        ${LLAMA_SRC}/include

        ${LLAMA_SRC}/ggml/include

        ${LLAMA_SRC}/ggml/src

        ${LLAMA_SRC}/tools/mtmd
)

# --------------------------------------------------------------------------
# Link libraries
# --------------------------------------------------------------------------

target_link_libraries(
        ${CMAKE_PROJECT_NAME}

        PRIVATE

        llama

        llama-common

        mtmd

        android

        log
)
