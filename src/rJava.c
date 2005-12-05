#include <R.h>
#include "rJava.h"
#include <stdlib.h>
#include <string.h>

#define PATH_SEPARATOR ':'
#define USER_CLASSPATH "."

JavaVM *jvm;
static JDK1_1InitArgs vm1_args;

jclass javaStringClass;
jclass javaObjectClass;

#ifdef JNI_VERSION_1_2 
static JavaVMInitArgs vm2_args;
/* Classpath. */
#define N_JDK_OPTIONS 3
#define VMARGS_TYPE JavaVMInitArgs
static JavaVMOption *vm2_options;
static JavaVMInitArgs *vm_args;
#else  /* So its not JNI_VERSION 1.2 */
static JDK1_1InitArgs vm2_args;
#define VMARGS_TYPE JDK1_1InitArgs
static JDK1_1InitArgs *vm_args;
#endif  /* finished the version 1.1 material */

JNIEnv *eenv;

JNIEnv *getJNIEnv() {
    JNIEnv *env;
    jsize l;
    jint res;
    
    if (!jvm) { /* we're hoping that the JVM pointer won't change :P we fetch it just once */
        res= JNI_GetCreatedJavaVMs(&jvm, 1, &l);
        if (res!=0) {
            Rf_error("JNI_GetCreatedJavaVMs failed! (result:%d)",(int)res); return 0;
        }
        if (l<1)
	    Rf_error("JNI_GetCreatedJavaVMs said there's no JVM running! Maybe .jinit() would help.");
    }
    res = (*jvm)->AttachCurrentThread(jvm, (void**) &env, 0);
    if (res!=0) {
      Rf_error("AttachCurrentThread failed! (result:%d)", (int)res); return 0;
    }
    if (env && !eenv) eenv=env;
    
    /* if (eenv!=env)
        fprintf(stderr, "Warning! eenv=%x, but env=%x - different environments encountered!\n", eenv, env); */
    return env;
}

int initJVM(char *user_classpath) {
  jint res;
  char *classpath;
  
  if(!user_classpath)
    /* use the CLASSPATH environment variable as default */
    user_classpath = (char*) getenv("CLASSPATH");
  if(!user_classpath) user_classpath = "";
  
  vm_args = (VMARGS_TYPE *) &vm2_args;
#if defined(JNI_VERSION_1_2)
  vm_args->version = JNI_VERSION_1_2;
  /* printf("JNI 1.2+\n"); */
#else
  /* printf("JNI 1.1\n"); */
  vm_args->version = 0x00010001;
  vm_args->verbose = 1;
#endif
  /* check the version: if 1.2 not available compute a class path */
  if(JNI_GetDefaultJavaVMInitArgs(vm_args) != 0) {
    vm_args = (VMARGS_TYPE *)(&vm1_args);
#if defined(JNI_VERSION_1_1)
    vm_args->version = JNI_VERSION_1_1;
#endif
    vm1_args.classpath = user_classpath;
    if(JNI_GetDefaultJavaVMInitArgs(vm_args) != 0) {
      Rf_error("Neither 1.1x nor 1.2x version of JDK seems supported");
      return -1;    
    }
  }
#if defined(JNI_VERSION_1_2)
  else {
    int total_num_properties, propNum = 0;
    
    /* total_num_properties = N_JDK_OPTIONS+n_properties; */
    total_num_properties = N_JDK_OPTIONS;
    
    /*
      if(RequireLibraries) {
      total_num_properties += 2;
      }
    */
    
    vm2_options = (JavaVMOption *) calloc(total_num_properties, sizeof(JavaVMOption));
    vm2_args.version = JNI_VERSION_1_2;
    vm2_args.options = vm2_options;
    vm2_args.ignoreUnrecognized = JNI_TRUE;
    
    classpath = (char*) calloc(strlen("-Djava.class.path=") + strlen(user_classpath)+1, sizeof(char));
    sprintf(classpath, "-Djava.class.path=%s", user_classpath);
    
    vm2_options[propNum++].optionString = classpath;   
    
    /*   print JNI-related messages */
    /* vm2_options[propNum++].optionString = "-verbose:class,jni"; */
    vm2_args.nOptions = propNum;
  }
  /* Create the Java VM */
  res = JNI_CreateJavaVM(&jvm,(void *)&eenv,(void *)vm_args);
#else
  tmp = (char*)malloc(strlen(user_classpath) +strlen(vm_args->classpath) + 2);
  strcpy(tmp, user_classpath);
  strcat(tmp,":");
  strcat(tmp,vm_args->classpath);
  vm_args->classpath = tmp;
  
#endif
  if (res != 0 || eenv == NULL) {
    Rf_error("Cannot create Java Virtual Machine");
    return -1;
  }
  return 0;
}

void doneJVM() {
  (*jvm)->DestroyJavaVM(jvm);
}
