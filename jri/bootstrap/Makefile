libboot.jnilib: JRIBootstrap.o
	$(CC) -dynamiclib -o $@ $^ -framework JavaVM

JRIBootstrap.o: JRIBootstrap.c JRIBootstrap.h
	$(CC) -c -o $@ $< -I/System/Library/Frameworks/JavaVM.framework/Headers

run: libboot.jnilib
	javac -source 1.2 -target 1.2 *.java
	java Boot
