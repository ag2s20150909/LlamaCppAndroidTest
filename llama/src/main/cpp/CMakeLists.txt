
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("llama-android")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -flto")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -flto")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -O3 -flto")


set(GGML_VULKAN OFF)
set(GGML_LTO ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(BUILD_SHARED_LIBS ON)



if (ANDROID_ABI MATCHES  armeabi-v7a)
    set(GGML_LLAMAFILE OFF)
endif ()
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(GGML_CPU_AARCH64  ON)
    set(GGML_USE_CPU_AARCH64 "-march=armv8.2-a+fp16+dotprod")
    #set(GGML_USE_CPU_AARCH64 "-march=armv8.2-a+fp16+dotprod  --mtune=cortex-a78.cortex-a55")
endif ()


include_directories(
        ${CMAKE_SOURCE_DIR}/llama.cpp/ggml/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/include
)




if (GGML_VULKAN)
    #set(Vulkan_GLSLC_EXECUTABLE,"C:/VulkanSDK/1.3.296.0/Bin/glslc")
    if(CMAKE_HOST_SYSTEM MATCHES Windows)
        set(Vulkan_GLSLC_EXECUTABLE,"${CMAKE_ANDROID_NDK}/shader-tools/windows-x86_64/glslc")
    elseif (CMAKE_HOST_APPLE)
        set(Vulkan_GLSLC_EXECUTABLE,"${CMAKE_ANDROID_NDK}/shader-tools/darwin-x86_64/glslc")
    elseif (CMAKE_HOST_SYSTEM MATCHES Linux)
        set(Vulkan_GLSLC_EXECUTABLE,"${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
    endif ()

    include_directories(
            ${CMAKE_SOURCE_DIR}/include
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
