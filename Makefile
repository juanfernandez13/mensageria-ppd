.PHONY: all run run-sensor run-cliente jar clean

SRC = src/main/java/br/edu/ifce/mom/*.java
MAIN = br.edu.ifce.mom.Launcher
CP = build:lib/*

all:
	mkdir -p build
	javac -cp "lib/*" -d build $(SRC)

run: all
	java -cp "$(CP)" $(MAIN)

run-sensor: all
	java -cp "$(CP)" br.edu.ifce.mom.Sensor $(ARGS)

run-cliente: all
	java -cp "$(CP)" br.edu.ifce.mom.Cliente $(ARGS)

jar: all
	echo "Main-Class: $(MAIN)" > manifest.txt
	echo "Class-Path: $(addprefix lib/,$(notdir $(wildcard lib/*.jar)))" >> manifest.txt
	jar cfm projeto-mom.jar manifest.txt -C build .
	rm manifest.txt
	@echo "Executavel criado: projeto-mom.jar  (java -jar projeto-mom.jar, precisa da pasta lib/ ao lado)"

clean:
	rm -rf build projeto-mom.jar
