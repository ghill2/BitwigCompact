#!/usr/bin/env bash
# Builds the shell + extension, runs the tests, and installs the .bwextension
# into ~/Documents/Bitwig Studio/Extensions/ControllerBitwig.bwextension
set -euo pipefail
cd "$(dirname "$0")"

# No Java on the default PATH on this machine; use homebrew's OpenJDK if JAVA_HOME is unset.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
MVN="${MVN:-mvn}"

exec "$MVN" clean install "$@"
