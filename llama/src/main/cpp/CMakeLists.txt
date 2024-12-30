
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
#set(Vulkan_GLSLC_EXECUTABLE,"${CMAKE_ANDROID_NDK}/shader-tools/windows-x86_64/glslc")
#set(Vulkan_GLSLC_EXECUTABLE,"C:/VulkanSDK/1.3.296.0/Bin/glslc")
set(GGML_VULKAN OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(BUILD_SHARED_LIBS ON)

message(${CMAKE_CURRENT_BINARY_DIR})


include_directories(
        ${CMAKE_SOURCE_DIR}/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/ggml/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_BINARY_DIR}/build-llama/ggml/src/ggml-vulkan

)
add_subdirectory("llama.cpp" build-llama EXCLUDE_FROM_ALL)

add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        LLamaAndroid.cpp)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        llama
        common
        android
        log)
