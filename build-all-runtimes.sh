#!/bin/bash
set -e
platforms=(win linux linux-aarch64 mac)
for platform in "${platforms[@]}"; do
  rm -rf build/{classes,generated,libs,resources}
  ./gradlew satergoRuntime -Pplatform="$platform"
done
echo "All runtimes built"