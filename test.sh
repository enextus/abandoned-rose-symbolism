#!/bin/sh
set -eu
cd "$(dirname "$0")"
mvn clean verify "$@"
