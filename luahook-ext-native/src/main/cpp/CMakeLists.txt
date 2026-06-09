cmake_minimum_required(VERSION 3.22.1)

project("luahook")

# XDL
file(GLOB XDL_SRC
        "xdl/xdl/src/main/cpp/*.c"
        "memory/*.cpp"
)
add_library(xdl SHARED ${XDL_SRC})
target_compile_features(xdl PRIVATE c_std_17)
target_include_directories(xdl PUBLIC
        xdl/xdl/src/main/cpp/include
        memory/include
)

# Dobby
set(DOBBY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/dobby)
add_library(dobby_lib STATIC IMPORTED)
set_target_properties(dobby_lib PROPERTIES IMPORTED_LOCATION
        ${DOBBY_DIR}/${ANDROID_ABI}/libdobby.a)

add_library(luahook SHARED luahook.cpp)

target_include_directories(luahook PRIVATE
        ${DOBBY_DIR}/include
        xdl/xdl/src/main/cpp/include
        memory/include
)

target_link_libraries(luahook PRIVATE android log xdl dobby_lib)