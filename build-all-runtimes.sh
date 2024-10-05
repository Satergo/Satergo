#!/bin/bash
set -e
platforms=(win linux linux-aarch64 mac mac-aarch64)
for platform in "${platforms[@]}"; do
  echo "--- Building runtime for $platform ---"
  ./gradlew satergoRuntime -Pplatform="$platform" "$@"
done
echo "All runtimes built"