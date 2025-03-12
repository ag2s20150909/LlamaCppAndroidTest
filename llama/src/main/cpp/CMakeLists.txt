

cmake_minimum_required(VERSION 3.24.0)
project("llama-android")

#set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16+dotprod -flto")
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}  -march=armv8.2-a+fp16+dotprod  -flto")
#set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS}  -flto")
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}    -flto")
#set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -O3 -flto")


set(GGML_VULKAN OFF)
set(GGML_OPENCL OFF)
set(GGML_CPU_KLEIDIAI OFF)
set(GGML_LTO ON)
set(GGML_NATIVE OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(BUILD_SHARED_LIBS ON)

if (GGML_OPENCL)
    set(OpenCL_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/opencl)
    set(OpenCL_LIBRARY ${CMAKE_SOURCE_DIR}/opencl/libOpenCL.so)
endif ()

add_compile_options(-Wall -Wextra)

if (ANDROID_ABI MATCHES armeabi-v7a)
    set(GGML_LLAMAFILE OFF)
endif ()


if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(GGML_CPU_AARCH64 ON)
    #https://gcc.gnu.org/onlinedocs/gcc-14.2.0/gcc/AArch64-Options.html
    #set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod")
    set(GGML_USE_CPU_AARCH64 ON)
endif ()


include_directories(
        ${CMAKE_SOURCE_DIR}/llama.cpp/ggml/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/include
)


if (GGML_VULKAN)
    if (CMAKE_HOST_SYSTEM MATCHES Windows)
        set(Vulkan_GLSLC_EXECUTABLE,"${CMAKE_ANDROID_NDK}/shader-tools/windows-x86_64/glslc")
        set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN ${CMAKE_SOURCE_DIR}/vulkan/Windows.MSVC.toolchain.cmake)
    elseif (CMAKE_HOST_APPLE)
        set(Vulkan_GLSLC_EXECUTABLE,"${CMAKE_ANDROID_NDK}/shader-tools/darwin-x86_64/glslc")
    elseif (CMAKE_HOST_SYSTEM MATCHES Linux)
        set(Vulkan_GLSLC_EXECUTABLE,"${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
    endif ()
    include_directories(
            ${CMAKE_SOURCE_DIR}/vulkan
            ${CMAKE_CURRENT_BINARY_DIR}/build-llama/ggml/src/ggml-vulkan
    )
endif ()


add_subdirectory("llama.cpp" build-llama EXCLUDE_FROM_ALL)

add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        LLamaAndroid.cpp
        llm_inference.cpp
)


target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        llama
        common
        android
        log)
