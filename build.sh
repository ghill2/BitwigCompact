#!/usr/bin/env bash
# Builds the shell + extension + monitor, runs the tests, installs the .bwextension
# into ~/Documents/Bitwig Studio/Extensions/ and exports it to the repository root.
set -euo pipefail
cd "$(dirname "$0")"

# No Java on the default PATH on this machine; use homebrew's OpenJDK if JAVA_HOME is unset.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
MVN="${MVN:-mvn}"

# Embed this checkout's maps/ folder path into the extension (gitignored). On this machine
# the extension then defaults to the repository's maps/; builds elsewhere embed a path that
# does not exist there, so users fall back to ~/Documents/Bitwig Studio/BitwigCompact.
PROPS="extension/src/main/resources/com/me/bitwig/repo.properties"
mkdir -p "$(dirname "$PROPS")"
printf 'maps.dir=%s/maps\n' "$(pwd)" > "$PROPS"

"$MVN" clean install "$@"

# Export the built extension to the repository root (committed for download).
cp extension/target/BitwigCompact.jar BitwigCompact.bwextension
echo "exported $(pwd)/BitwigCompact.bwextension"
