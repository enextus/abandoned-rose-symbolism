#!/bin/sh
set -eu
cd "$(dirname "$0")"
java -jar abandoned-rose-symbolism.jar "$@"
