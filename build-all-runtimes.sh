#!/bin/bash
platforms=(win linux linux-aarch64 mac mac-aarch64)
for platform in "${platforms[@]}"; do
  rm -rf build
  ./gradlew satergoRuntime -Pplatform="$platform"
done
echo "All runtimes built"