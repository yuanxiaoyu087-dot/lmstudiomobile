cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure llama.cpp build options before adding subdirectory
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Build tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)

# Enable Vulkan if available (optional)
option(LLAMA_VULKAN "Enable Vulkan support" ON)

# Add llama.cpp subdirectory
add_subdirectory(llama.cpp)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# Create the JNI library
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Link libraries
target_link_libraries(llama_jni
    ${log-lib}
    ${android-lib}
    llama
)

# Include directories
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
)
