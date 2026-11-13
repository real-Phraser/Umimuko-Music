#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
echo "Building portable distribution for current OS..."
./gradlew createDistributable
echo "Done! Output located in build/compose/binaries/main/app/"
