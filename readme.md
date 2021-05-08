# DashPlus

A customisable FRC Driver Station Dashboard for the New WPILIB Framework.

## Setting up Build Environment
Tested working on Ubuntu and macOS.

```bash
# If you are on Ubuntu
sudo apt install cmake build-essential ninja-build libx11-dev libxrandr-dev libxinerama-dev libxcursor-dev libxi-dev libopencv-dev freeglut3-dev
# Otherwise, for Mac, use MacPorts or Homebrew to pull down the required libraries.

git clone --recursive https://github.com/mitchellblaser/dashplus
cd dashplus

cd allwpilib
mkdir build && cd build
cmake .. -GNinja -DWITH_JAVA=OFF -DWITH_TESTS=OFF -DWITH_SIMULATION_MODULES=OFF -DWITH_CSCORE=OFF -DWITH_GUI=OFF
ninja
cd ..

cd raylib
mkdir build && cd build
cmake .. -DBUILD_EXAMPLES=OFF -GNinja
ninja
cd ..

mkdir build && cd build
cmake .. -GNinja
ninja
```
