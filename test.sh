#!/bin/sh
set -eu
cd "$(dirname "$0")"
sh build.sh
mkdir -p target/test-classes
javac --release 17 -encoding UTF-8 -cp target/classes -d target/test-classes src/test/java/org/example/RoseRenderingCheck.java
java -Djava.awt.headless=true -cp target/classes:target/test-classes org.example.RoseRenderingCheck
