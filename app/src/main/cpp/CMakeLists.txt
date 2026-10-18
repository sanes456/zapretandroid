cmake_minimum_required(VERSION 3.22.1)

project(byedpi_jni C)

set(BYEDPI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/byedpi)

add_library(byedpi SHARED
    ${BYEDPI_DIR}/conev.c
    ${BYEDPI_DIR}/desync.c
    ${BYEDPI_DIR}/extend.c
    ${BYEDPI_DIR}/main.c
    ${BYEDPI_DIR}/mpool.c
    ${BYEDPI_DIR}/packets.c
    ${BYEDPI_DIR}/proxy.c
    ${CMAKE_CURRENT_SOURCE_DIR}/byedpi_jni.c
)

target_include_directories(byedpi PRIVATE ${BYEDPI_DIR})

target_compile_definitions(byedpi PRIVATE ANDROID_APP=1)

target_compile_options(byedpi PRIVATE
    -std=gnu99
    -O2
    -Wall
    -Wno-unused-parameter
    -Wno-unused-variable
)

target_link_options(byedpi PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

find_library(log-lib log)
target_link_libraries(byedpi ${log-lib})
