#!/bin/bash
platforms=(win linux linux-aarch64 linux-arm32 mac mac-aarch64)
for platform in "${platforms[@]}"; do
  rm -rf build
  ./gradlew runtimeZip -Pplatform="$platform"
done
echo "All runtimes built"