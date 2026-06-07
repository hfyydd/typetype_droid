cmake_minimum_required(VERSION 3.13.0)

project(typetype_hymt LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(OHOS_ARCH STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
elseif(OHOS_ARCH STREQUAL "x86_64")
    set(GGML_SYSTEM_ARCH "x86")
else()
    message(FATAL_ERROR "Unsupported OHOS_ARCH: ${OHOS_ARCH}")
endif()

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_WEBUI OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
add_compile_definitions(TYPETYPE_OHOS=1)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(typetype_hymt SHARED
    typetype_hymt.cpp
)

target_compile_definitions(typetype_hymt PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=0
    GGML_OPENMP=0
)

target_include_directories(typetype_hymt PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src
)

target_link_libraries(typetype_hymt PUBLIC
    llama
    llama-common
    libace_napi.z.so
    libhilog_ndk.z.so
    librawfile.z.so
)
