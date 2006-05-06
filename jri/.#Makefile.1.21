# JRI - Java/R Interface      experimental!
#--------------------------------------------------------------------------

# please uncomment the platform you use

#include Makefile.win
#include Makefile.linux
include Makefile.osx

#--- comment out the following for non-debug version
CFLAGS+=-g

#--- if RHOME is different from the system defaults, set it here
#RHOME=/usr/local/lib/R

#--- normally you don't need to change this - modify JAVAB instead
JAVAC=$(JAVAB)c $(JFLAGS)
JAVAH=$(JAVAB)h

#--------------------------------------------------------------------------
# you shouldn't need to touch anything below this line

RINC=-I$(RHOME)/src/include -I$(RHOME)/include
RLD=-L$(RHOME)/bin -L$(RHOME)/lib -lR
CFLAGS+=-Iinclude -Isrc/include

TARGETS=$(JNIPREFIX)jri$(JNISO) run run2 $(PLATFORMT)

all: $(TARGETS)

src/org_rosuda_JRI_Rengine.h: org/rosuda/JRI/Rengine.class
	$(JAVAH) -d src org.rosuda.JRI.Rengine

src/Rcallbacks.o: src/Rcallbacks.c src/Rcallbacks.h src/globals.h
	$(CC) -c -o $@ src/Rcallbacks.c $(CFLAGS) $(CPICF) $(JAVAINC) $(RINC)

src/Rinit.o: src/Rinit.c src/Rinit.h src/Rcallbacks.h
	$(CC) -c -o $@ src/Rinit.c $(CFLAGS) $(CPICF) $(RINC)

src/globals.o: src/globals.c src/globals.h
	$(CC) -c -o $@ src/globals.c $(CFLAGS) $(CPICF) $(JAVAINC)

src/Rengine.o: src/Rengine.c src/org_rosuda_JRI_Rengine.h src/globals.h src/Rcallbacks.h src/Rinit.h
	$(CC) -c -o $@ src/Rengine.c $(CFLAGS) $(CPICF) $(JAVAINC) $(RINC)

src/jri.o: src/jri.c
	$(CC) -c -o $@ src/jri.c $(CFLAGS) $(CPICF) $(JAVAINC) $(RINC)

src/jri$(JNISO): src/Rengine.o src/jri.o src/Rcallbacks.o src/Rinit.o src/globals.o $(JRIDEPS)
	$(CC) -o $@ $^ $(JNILD) $(RLD)

libjvm.dll.a: jvm.def
	dlltool --input-def jvm.def --kill-at --dllname jvm.dll --output-lib libjvm.dll.a

$(JNIPREFIX)jri$(JNISO): src/jri$(JNISO)
	cp $^ $@

org/rosuda/JRI/Rengine.class org/rosuda/JRI/REXP.class org/rosuda/JRI/Mutex.class: Rengine.java REXP.java Mutex.java RMainLoopCallbacks.java
	$(JAVAC) -d . $^

rtest.class: rtest.java org/rosuda/JRI/Rengine.class org/rosuda/JRI/REXP.class
	$(JAVAC) -d . $<

rtest2.class: rtest2.java org/rosuda/JRI/Rengine.class org/rosuda/JRI/REXP.class
	$(JAVAC) -d . $<

run.pre: rtest.class
	echo "#!/bin/bash" > run.pre
	echo "export R_HOME=$(RHOME)" >> run.pre
	if [ -e .run ]; then cat .run >> run.pre; fi
	echo "export DYLD_LIBRARY_PATH=$(RHOME)/bin" >> run.pre
	echo "export LD_LIBRARY_PATH=.:$(RHOME)/bin:$(RHOME)/lib:$(JAVAHOME)/jre/lib/$(PLATFORM):$(JAVAHOME)/jre/lib/$(PLATFORM):$(JAVAHOME)/jre/bin/classic" >> run.pre

run: run.pre rtest.class
	cp run.pre run
	if [ -e .run ]; then echo "$(JAVAB) -jar JGRinst.jar \$$*" >> run; else echo "$(JAVAB) rtest \$$*" >> run; fi
	-chmod a+x run

run2: run.pre rtest2.class
	cp run.pre run2
	if [ -e .run ]; then echo "$(JAVAB) -jar JGRinst.jar \$$*" >> run2; else echo "$(JAVAB) rtest2 \$$*" >> run2; fi
	-chmod a+x run2

clean:
	rm -rf $(TARGETS) org src/*.o src/*~ src/org_rosuda_JRI_Rengine.h src/*$(JNISO) *.class *~ run.pre

.PHONY: clean all

