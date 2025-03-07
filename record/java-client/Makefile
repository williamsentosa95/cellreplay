# Compiler and JAR tool
JAVAC = javac
JAR = jar

# Directories
BIN = bin

# List of Java programs (without .java extension)
PROGRAMS = SaturatorClient PacketTrainClient PacketTrainEchoDynamicClient

# Rule to compile all programs
all: $(PROGRAMS)

# Rule to compile each Java program and package into a JAR
$(PROGRAMS):
	@echo "Compiling $@..."
	@mkdir -p $(BIN)/$@
	$(JAVAC) -d $(BIN)/$@ $@.java
	@echo "Main-Class: $@" > $(BIN)/$@/MANIFEST.MF
	$(JAR) cfm $@.jar $(BIN)/$@/MANIFEST.MF -C $(BIN)/$@ .
	@rm $(BIN)/$@/MANIFEST.MF
	@echo "JAR file '$@.jar' created successfully!"

# Clean all compiled files and JARs
clean:
	rm -rf $(BIN) *.jar
