#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
echo "Building native installer package for current OS..."
./gradlew packageDistributionForCurrentOS
echo "Done! Output located in build/compose/binaries/main/"
