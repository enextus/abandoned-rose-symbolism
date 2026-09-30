#!/bin/sh
set -eu
cd "$(dirname "$0")"
rm -rf target/classes
mkdir -p target/classes
javac --release 17 -encoding UTF-8 -d target/classes src/main/java/org/example/*.java
if [ -d src/main/resources ]; then
  cp -R src/main/resources/. target/classes/
fi
jar --create --file abandoned-rose-symbolism.jar --main-class org.example.RoseDrawing -C target/classes .
