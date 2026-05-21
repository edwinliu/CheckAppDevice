
cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-narrowing")

project("corner_monit")
# 导入子项目的一些头文件
include_directories(
        ./include

)



add_library(${CMAKE_PROJECT_NAME} SHARED
        Foundation/IOReplace.cpp
        Foundation/SandboxFs.cpp
        Foundation/SensorHook.cpp
        # Foundation/KeymasterHook.cpp
        Foundation/syscall/BinarySyscallFinder.cpp
        Foundation/HookFunction.cpp
        Foundation/canonicalize_md.c
        Foundation/Symbol.cpp
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        corner_monit.cpp)


#使用 dobby 静态链接库
add_library(DobbyLib STATIC IMPORTED)
# DobbyLib  lib库别名
# PROPERTIES  表示导入
# IMPORTED_LOCATION  表示导入本地库
# CMAKE_CURRENT_SOURCE_DIR   当前项目路径
# CMAKE_ANDROID_ARCH_ABI     平台架构
# jniLibs   注意 linux中 区分大小写
set_target_properties(DobbyLib PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libdobby.a)
#/home/vb2345qw/kecheng/gqghj/src/main/cpp/jniLibs/x86/libdobby.a
message("${CMAKE_CURRENT_SOURCE_DIR}/jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libdobby.a")


target_link_libraries(${CMAKE_PROJECT_NAME}
        DobbyLib
        android
        log)
