#!/usr/bin/env bash
# Abre o menu principal (Launcher) com opcoes para iniciar broker, sensores e clientes.
set -e
cd "$(dirname "$0")"
JAR="projeto-mom.jar"
[ -f "$JAR" ] || { echo "JAR nao encontrado. Execute ./build.sh (ou 'make jar') primeiro."; exit 1; }
java -jar "$JAR"
