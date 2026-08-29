# Java on the GPU with Project Babylon

This repository contains one small example: an animated Julia fractal where
every pixel is computed by a GPU thread. The GPU kernel is written in plain
Java. There is no JNI code and no CUDA C in this project.

It works because of [Project Babylon](https://openjdk.org/projects/babylon),
an OpenJDK project that adds "code reflection" to Java. A method marked with
`@Reflect` keeps a symbolic model of its own body inside the class file. At
run time, the HAT toolkit (part of the Babylon repository) reads that model,
translates it to CUDA C, compiles it with `nvcc`, and runs it on the GPU.

The whole example is one file, [JavaDayGpu.java](JavaDayGpu.java). The kernel
is about 25 lines. When you run it, a window opens with an animated fractal.
A status line at the bottom of the window shows how many threads ran on the
GPU and how long each frame took. On an RTX 5060 Ti, one frame (1,440,000 threads, up to 1,000
iterations each) takes a few milliseconds.

This example was written for the talk "Projeto Babylon: Rodando Java na GPU"
at JavaDay.

## What you need

- Linux, or Windows with WSL2 (tested on Ubuntu). macOS works with the
  OpenCL backend, but this README covers CUDA.
- An NVIDIA GPU with a recent driver.
- The CUDA Toolkit, version 13 or newer. The driver alone is not enough,
  because HAT calls `nvcc` at run time.
- A JDK, version 26 or newer, used only to build Babylon. Any distribution
  works, for example [Temurin](https://adoptium.net).
- Build tools: `git`, `make`, `autoconf`, `cmake`, `gcc` (12 or newer),
  `maven`, and the X11 development headers.

On Ubuntu you can install the build tools with:

```bash
sudo apt-get install make autoconf cmake maven zip unzip file \
  libx11-dev libxext-dev libxrender-dev libxrandr-dev libxtst-dev \
  libxt-dev libcups2-dev libfontconfig1-dev libasound2-dev
```

## Step 1: build the Babylon JDK

Project Babylon does not ship binaries yet, so you build it from source.
This takes 15 to 30 minutes on a modern machine.

```bash
git clone --depth 1 --branch code-reflection https://github.com/openjdk/babylon
cd babylon
bash configure --with-boot-jdk=/path/to/your/jdk-26
make images
```

The result is a full JDK in `build/linux-x86_64-server-release/images/jdk`.

Note for WSL2 users: `configure` sees "microsoft" in the kernel name and
assumes you want a Windows build. Force a Linux build like this:

```bash
bash configure --with-boot-jdk=/path/to/your/jdk-26 \
  --build=x86_64-unknown-linux-gnu --host=x86_64-unknown-linux-gnu \
  --with-toolchain-type=gcc
```

## Step 2: build HAT

HAT lives inside the Babylon repository, in the `hat` directory. Build it
with the JDK you just made:

```bash
cd hat
export JAVA_HOME=$PWD/../build/linux-x86_64-server-release/images/jdk
export PATH=$JAVA_HOME/bin:/usr/local/cuda/bin:$PATH
mvn clean package
```

Note for WSL2 users: the linker cannot find `libcuda.so`, because on WSL it
lives in `/usr/lib/wsl/lib`. Set this before running Maven:

```bash
export LIBRARY_PATH=/usr/lib/wsl/lib:/usr/local/cuda/lib64/stubs
```

## Step 3: run the example

Clone this repository and run the script. It copies `JavaDayGpu.java` into
the `hat` directory and starts it with the right flags:

```bash
git clone https://github.com/devops-thiago/javaday-babylon-gpu
cd javaday-babylon-gpu
BABYLON_HOME=/path/to/babylon ./run.sh
```

A window opens with the animated fractal. Watch `nvidia-smi` in a second
terminal to see the GPU working. Close the window to stop.

If you prefer to run it by hand, these are the flags that matter. The code
reflection API is an incubator module, so nothing works without them:

```bash
java --enable-preview --add-modules=jdk.incubator.code \
     --enable-native-access=ALL-UNNAMED ...
```

## Running without a GPU

The same Java code runs on the CPU. Replace the CUDA backend jar with the
multi-threaded Java backend in the classpath:

```bash
-cp build/hat-core-1.0.jar:build/hat-optkl-1.0.jar:build/hat-wrap-shared-1.0.jar:build/hat-backend-java-mt-1.0.jar
```

It is much slower, but the Java code stays the same.

## Troubleshooting

### nvcc fails with "exception specification is incompatible" for rsqrt

This happens on distributions with glibc 2.41 or newer (Ubuntu 25.10+,
Fedora 42+). glibc now declares `rsqrt` with `noexcept`, and the CUDA 13
headers declare it without. Until NVIDIA fixes the header, you can patch
your local copy of `crt/math_functions.h` to add `noexcept (true)` to the
`rsqrt` and `rsqrtf` declarations, or keep a patched copy in a shadow
directory and put a small `nvcc` wrapper first in your `PATH`. Remember
that HAT calls `nvcc` at run time, so the wrapper has to be on the `PATH`
of the Java process too.

### The window never appears on WSL2

Check that other GUI apps work. If nothing shows up and WSL has been
running for days, run `wsl --shutdown` from PowerShell and open a new
terminal. That restarts WSLg.

### Module jdk.incubator.code not found

You are running a normal JDK, not the one you built from the Babylon
repository. Check which `java` is first in your `PATH`.

## License

The code in this repository is under the [MIT License](LICENSE).
Project Babylon and HAT belong to the OpenJDK project and have their own
license (GPLv2 with Classpath Exception). This repository does not include
any of their code; the build steps above fetch it from the official
repository.
