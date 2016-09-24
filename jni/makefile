all: TestNatives.dll

TestNatives.dll: src/TestNatives.cpp glue/TestNatives.inc
	g++ -shared -g -Wall -Isrc -Iglue -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/win32" -o TestNatives.dll src/TestNatives.cpp

clean:
	rm -f obj/TestNatives.o TestNatives.dll