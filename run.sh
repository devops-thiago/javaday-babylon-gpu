#!/bin/bash
# Runs the JavaDayGpu example on the CUDA backend.
# Set BABYLON_HOME to the directory where you cloned the babylon repository.
set -e

BABYLON_HOME=${BABYLON_HOME:-$HOME/babylon}
JDK=$BABYLON_HOME/build/linux-x86_64-server-release/images/jdk
HAT=$BABYLON_HOME/hat

if [ ! -x "$JDK/bin/java" ]; then
  echo "Babylon JDK not found at $JDK"
  echo "Set BABYLON_HOME or follow the build steps in the README."
  exit 1
fi

export PATH=$JDK/bin:/usr/local/cuda/bin:$PATH
# On WSL the CUDA driver library lives here:
[ -d /usr/lib/wsl/lib ] && export LD_LIBRARY_PATH=/usr/lib/wsl/lib

cp "$(dirname "$0")/JavaDayGpu.java" "$HAT/"
cd "$HAT"

exec java --enable-preview --add-modules=jdk.incubator.code \
  --enable-native-access=ALL-UNNAMED -Djava.library.path=build \
  -cp build/hat-core-1.0.jar:build/hat-optkl-1.0.jar:build/hat-wrap-shared-1.0.jar:build/hat-backend-ffi-shared-1.0.jar:build/hat-backend-ffi-cuda-1.0.jar \
  JavaDayGpu.java
