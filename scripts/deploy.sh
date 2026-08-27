#!/bin/bash

# Load environment variables from .env file
if [ -f .env ]; then
  export "$(cat .env)"
fi

# Determine Maven profile (debug by default)
MAVEN_PROFILE=${MAVEN_PROFILE:-debug}

# Allow override via command line argument
if [ -n "$1" ]; then
  MAVEN_PROFILE="$1"
fi

echo "[INFO] Building with Maven profile: $MAVEN_PROFILE"

# Build with the selected profile
mvn clean install -P "$MAVEN_PROFILE"
if [ $? -ne 0 ]; then
  echo "[ERROR] Maven build failed"
  exit 1
fi

# Extract version from pom.xml
VERSION=$(sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' pom.xml | head -1)

if [ -z "$VERSION" ]; then
  echo "[ERROR] Could not extract version from pom.xml"
  exit 1
fi

# Remove old mod
rm -f "$HYTALE_SERVER_MODS_PATH"/arcanerelay-*.jar
echo "[INFO] Removed old mod from Hytale Mods folder"

# Copy new mod
cp "./target/arcanerelay-$VERSION.jar" "$HYTALE_SERVER_MODS_PATH"/
if [ $? -ne 0 ]; then
  echo "[ERROR] Failed to copy mod to $HYTALE_SERVER_MODS_PATH"
  exit 1
fi
echo "[INFO] COPY SUCCESS"

echo "[INFO] DEPLOY COMPLETED"
