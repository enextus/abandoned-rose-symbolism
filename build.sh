#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p target/classes
javac --release 17 -encoding UTF-8 -d target/classes src/main/java/org/example/*.java
jar --create --file abandoned-rose-symbolism.jar --main-class org.example.RoseDrawing -C target/classes .
