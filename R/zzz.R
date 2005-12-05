.First.lib <- function(libname, pkgname) {
  # For MacOS X we have to remove /usr/X11R6/lib from the DYLD_LIBRARY_PATH
  # because it would override Apple's OpenGL framework (which is loaded
  # by JavaVM implicitly)
  Sys.putenv("DYLD_LIBRARY_PATH"=gsub("/usr/X11R6/lib","",Sys.getenv("DYLD_LIBRARY_PATH")))
  Sys.putenv("LD_LIBRARY_PATH"=paste(Sys.getenv("LD_LIBRARY_PATH"),"/usr/lib/j2sdk1.5-sun/jre/lib/i386/client:/usr/lib/j2sdk1.5-sun/jre/lib/i386:/usr/lib/j2sdk1.5-sun/jre/../lib/i386:/home/Hornik/tmp/R/lib:/usr/local/lib:/usr/X11R6/lib:/usr/lib/j2sdk1.5-sun/jre/lib/i386/client:/usr/lib/j2sdk1.5-sun/jre/lib/i386:/home/Hornik/tmp/R/lib:/usr/local/lib:/usr/X11R6/lib:/usr/lib/j2sdk1.5-sun/jre/lib/i386/client:/usr/lib/j2sdk1.5-sun/jre/lib/i386",sep=':'))
  library.dynam("rJava", pkgname, libname)
}
