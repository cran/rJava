### reflection functions - convenience function relying on the low-level
### functions .jcall/.jnew and friends

### reflection tools (inofficial so far, because it returns strings
### instead of the reflection objects - it's useful for quick checks,
### though)
.jmethods <- function(o, name=NULL, as.obj=FALSE, class.loader=.rJava.class.loader) {
  cl <- if (is(o, "jobjRef")) .jcall(o, "Ljava/lang/Class;", "getClass") else if (is(o, "jclassName")) o@jobj else .jfindClass(as.character(o), class.loader=class.loader)
  ms<-.jcall(cl,"[Ljava/lang/reflect/Method;","getMethods")
  if (length(name)) {
     n <- sapply(ms, function(o) .jcall(o, "S", "getName"))
     ms <- ms[grep(name, n)]
  }
  if (isTRUE(as.obj)) ms else unlist(lapply(ms,function(x) .jcall(x,"S","toString")))
}

.jconstructors <- function(o, as.obj=FALSE, class.loader=.rJava.class.loader) {
  cl <- if (is(o, "jobjRef")) .jcall(o, "Ljava/lang/Class;", "getClass") else if (is(o, "jclassName")) o@jobj else .jfindClass(as.character(o), class.loader=class.loader)
  cs<-.jcall(cl,"[Ljava/lang/reflect/Constructor;","getConstructors")
  if (isTRUE(as.obj)) return(cs)
  unlist(lapply(cs,function(x) .jcall(x,"S","toString")))
}

### this list maps R class names to Java class names for which the constructor does the necessary conversion (for use in .jrcall)
### The order matters in case an object inherits from multiple (S3) classes - then the first one
### in the list is picked.
.class.to.jclass <-    c(
    jlong    = "java/lang/Long",
    jchar    = "java/lang/Character",
    jshort   = "java/lang/Short",
    jfloat   = "java/lang/Float",
    jbyte    = "java/lang/Byte",
    integer  = "java/lang/Integer",
    numeric  = "java/lang/Double",
    logical  = "java/lang/Boolean",
    character= "java/lang/String"
)

### Java classes that have a corresponding primitive type and thus a corresponding TYPE field to use with scalars
.primitive.classes = c("java/lang/Byte", "java/lang/Integer", "java/lang/Double", "java/lang/Boolean",
                       "java/lang/Long", "java/lang/Character", "java/lang/Short", "java/lang/Float")

### creates a valid java object
### if a is already a java object reference, all is good
### otherwise some primitive conversion occurs
# this is used for internal purposes only, in particular 
# it does not dispatch arrays to jrectRef
._java_valid_object <- function(a) {
  if (is(a, "jobjRef")) a
  else if (is(a, "jclassName")) a@jobj
  else if (is.null(a)) .jnull()
  else if (is.raw(a)) .jarray(a, dispatch=FALSE) ## raw is always [B
  else {
      ## check all classes (in S3 case, see #317)
      cm <- match(class(a), names(.class.to.jclass))
      if (!all(is.na(cm))) { ## at least one subclass matches
          cm <- min(cm, na.rm=TRUE) ## pick the lowest (i.e. in precedence of the order above)
          ## scalar? then create directly
          if (length(a) == 1) {
    		y <- .jnew(.class.to.jclass[cm], a)
    		if (.class.to.jclass[cm] %in% .primitive.classes) attr(y, "primitive") <- TRUE
                y
    	} else .jarray(a, dispatch = FALSE)
    } else {
      stop("Sorry, parameter type `", class(a)[1] ,"' is ambiguous or not supported.")
    }
  }
}

### creates a list of valid java parameters, used in both .J and .jrcall
._java_valid_objects_list <- function( ... )
  lapply(list(...), ._java_valid_object )


### returns a list of Class objects
### this is used in both .J and .jrcall
._isPrimitiveReference <- function(x) 
  isTRUE(attr(x, "primitive"))

._java_class <- function( x ){
  ## FIXME: not sure how to pass though the class loader here - only affects NULL objects
  if (is.jnull(x)) { if (is(x,"jobjRef")) .jfindClass(x@jclass) else .jclassObject } else {
    if (._isPrimitiveReference(x)) .jfield(x, "Ljava/lang/Class;", "TYPE") else .jcall(x, "Ljava/lang/Class;", "getClass")
  }
}
._java_class_list <- function( objects_list )
	lapply(objects_list, ._java_class )
                       
### reflected call - this high-level call uses reflection to call a method
### it is much less efficient than .jcall but doesn't require return type
### specification or exact matching of parameter types
.jrcall <- function(o, method, ..., simplify=TRUE, class.loader=.rJava.class.loader) {
  if (!is.character(method) | length(method) != 1)
    stop("Invalid method name - must be exactly one character string.")
  if (.need.init()) .jinit()
  if (is(o, "jclassName"))
    cl <- o@jobj
  else if (is(o, "jobjRef"))
    cl <- .jcall(o, "Ljava/lang/Class;", "getClass")
  else
    cl <- .jfindClass(o, class.loader=class.loader)
  if (is.jnull(cl))
    stop("Cannot find class of the object.")
  
  # p is a list of parameters that are formed solely by valid Java objects
  p <- ._java_valid_objects_list(...)
  
  # list of classes
  pc <- ._java_class_list( p )
  
  # invoke the method directly from the RJavaTools class
  # ( this throws the actual exception instead of an InvocationTargetException ) 
  j_p  <- .jarray(p, "java/lang/Object" , dispatch = FALSE )
  j_pc <- .jarray(pc, "java/lang/Class" , dispatch = FALSE )
  r <- .jcall( "RJavaTools", "Ljava/lang/Object;", "invokeMethod",
  	cl, .jcast(if(inherits(o,"jobjRef") || inherits(o, "jarrayRef")) o else cl, "java/lang/Object"), 
  	.jnew( "java/lang/String", method), 
  	j_p, j_pc, use.true.class = TRUE, evalString = simplify, evalArray = FALSE )
  
  # null is returned when the return type of the method is void
  # TODO[romain]: not sure how to distinguish when the result is null but the 
  #       return type is not null
  if( is.jnull( r ) || is.null(r) ){ 
  	return( invisible( NULL ) )
  }
  
  # simplify if needed and return the object
  if( is(r, "jarrayRef" ) && simplify ){
  	._jarray_simplify( r )
  } else if (simplify){
  	  .jsimplify(r) 
  } else  {
  	  r
  }
}

### reflected construction of java objects
### This uses reflection to call a suitable constructor based 
### on the classes of the ... it does not require exact match between 
### the objects and the constructor parameters
### This is to .jnew what .jrcall is to .jcall
.J <- function(class, ..., class.loader=.rJava.class.loader) {
    ## if it is a name, load the class, otherwise assume we have the object
    if (is.character(class)) {
        ## allow non-JNI specifiation
        class <- gsub("\\.","/",class)
        class <- .jfindClass(class, class.loader=class.loader)
    } else if (is(class, "jclassName")) {
        class <- class@jobj
    }

  # p is a list of parameters that are formed solely by valid Java objects
  p <- ._java_valid_objects_list(...)
  
  # list of classes
  pc <- ._java_class_list( p )

  # use RJavaTools to find create the object
  o <- .jcall("RJavaTools", "Ljava/lang/Object;", 
        "newInstance", class,
  	.jarray(p,"java/lang/Object", dispatch = FALSE ), 
  	.jarray(pc,"java/lang/Class", dispatch = FALSE ), 
  	evalString = FALSE, evalArray = FALSE, use.true.class = TRUE )
  
  o
}

## make sure Java's -2147483648 
.iNA <- function(o, convert=TRUE) if(convert && is.na(o)) -2147483648.0 else o

### simplify non-scalar reference to a scalar object if possible
.jsimplify <- function(o, promote=FALSE) {
  if (!inherits(o, "jobjRef") && !inherits(o, "jarrayRef"))
    return(o)
  cn <- .jclass(o, true=TRUE)
  if (cn == "java.lang.Boolean") .jcall(o, "Z", "booleanValue") else
  if (cn == "java.lang.Integer" || cn == "java.lang.Short" || cn == "java.lang.Character" || cn == "java.lang.Byte") .iNA(.jcall(o, "I", "intValue"), promote) else
  if (cn == "java.lang.Number" || cn == "java.lang.Double" || cn == "java.lang.Long" || cn == "java.lang.Float") .jcall(o, "D", "doubleValue") else
  if (cn == "java.lang.String") .jstrVal(.jcast(o, "java/lang/String")) else
  o
}

#! ### get the value of a field (static class fields are not supported yet)
#! .jrfield <- function(o, name, simplify=TRUE, true.class=TRUE) {
#!   if (!inherits(o, "jobjRef") && !inherits(o, "jarrayRef") && !is.character(o))
#!     stop("Object must be a Java reference or class name.")
#!   if (is.character(o)) {
#!     cl <- .jfindClass(o)
#!     .jcheck(silent=TRUE)
#!     if (is.null(cl))
#!       stop("class not found")
#!     o <- .jnull()
#!   } else {
#!     cl <- .jcall(o, "Ljava/lang/Class;", "getClass")
#!     o <- .jcast(o, "java/lang/Object")
#!   }
#!   f <- .jcall(cl, "Ljava/lang/reflect/Field;", "getField", name)
#!   r <- .jcall(f,"Ljava/lang/Object;","get",o)
#!   if (simplify) r <- .jsimplify(r)
#!   if (true.class && (inherits(r, "jobjRef") || inherits(r, "jarrayRef"))) {
#!     cl <- .jcall(r, "Ljava/lang/Class;", "getClass")
#!     cn <- .jcall(cl, "Ljava/lang/String;", "getName")
#!     if (substr(cn,1,1) != '[')
#!       r@jclass <- gsub("\\.","/",cn)
#!   }
#!   r
#! }

### list the fields of a class or object
.jfields <- function(o, name=NULL, as.obj=FALSE, class.loader=.rJava.class.loader) {
  cl <- if (is(o, "jobjRef")) .jcall(o, "Ljava/lang/Class;", "getClass") else if (is(o, "jclassName")) o@jobj else .jfindClass(as.character(o), class.loader=class.loader)
  f <- .jcall(cl, "[Ljava/lang/reflect/Field;", "getFields")
  if (length(name)) {
     n <- sapply(f, function(o) .jcall(o, "S", "getName"))
     f <- f[grep(name, n)]
  }
  if (isTRUE(as.obj)) f else
  unlist(lapply(f, function(x) .jcall(x, "S", "toString")))
}

._must_be_character_of_length_one <- function(name){
	if( !is.character(name) || length(name) != 1L ){
		stop( "'name' must be a character vector of length one" )
	}
}
### checks if the java object x has a field called name
hasField <- function( x, name ){
	._must_be_character_of_length_one(name)
	.jcall("RJavaTools", "Z", "hasField", .jcast( x, "java/lang/Object" ), name)
}

hasJavaMethod <- function( x, name ){
	._must_be_character_of_length_one(name)
	.jcall("RJavaTools", "Z", "hasMethod", .jcast( x, "java/lang/Object" ), name)
}

hasClass <- function( x, name){
	._must_be_character_of_length_one(name)
	.jcall("RJavaTools", "Z", "hasClass", .jcast( x, "java/lang/Object" ), name)
}

### the following ones are needed for the static version of $
classHasField <- function(x, name, static=FALSE, class.loader=.rJava.class.loader) {
	if (is(x, "jclassName")) x <- x@jobj else if (!is(x, "jobjRef")) x <- .jfindClass(as.character(x), class.loader=class.loader)
	._must_be_character_of_length_one(name)
	.jcall("RJavaTools", "Z", "classHasField", x, name, static)
}

classHasMethod <- function(x, name, static=FALSE, class.loader=.rJava.class.loader) {
	if (is(x, "jclassName")) x <- x@jobj else if (!is(x, "jobjRef")) x <- .jfindClass(as.character(x), class.loader=class.loader)
	._must_be_character_of_length_one(name)
	.jcall("RJavaTools", "Z", "classHasMethod", x, name, static)
}

classHasClass <- function(x, name, static=FALSE, class.loader=.rJava.class.loader) {
	if (is(x, "jclassName")) x <- x@jobj else if (!is(x, "jobjRef")) x <- .jfindClass(as.character(x), class.loader=class.loader)
	._must_be_character_of_length_one(name)
	.jcall("RJavaTools", "Z", "classHasClass", x, name, static)
}

### syntactic sugar to allow object$field and object$methods(...)
### first attempts to find a field of that name and then a method
._jobjRef_dollar <- function(x, name) {
	if (hasField(x, name) ){
		.jfield(x, , name)
	} else if( hasJavaMethod( x, name ) ) {
		function(...) .jrcall(x, name, ...)
	} else if( hasClass(x, name) ) {
		cl <- .jcall( x, "Ljava/lang/Class;", "getClass" )
		inner.cl <- .jcall( "RJavaTools", "Ljava/lang/Class;", "getClass", cl, name, FALSE ) 
		new("jclassName", name=.jcall(inner.cl, "S", "getName"), jobj=inner.cl)
	} else if( is.character(name) && length(name) == 1L && name == "length" && isJavaArray(x) ){
		length( x )
	} else {
		stop( sprintf( "no field, method or inner class called '%s' ", name ) ) 
	}
}
setMethod("$", c(x="jobjRef"), ._jobjRef_dollar )

### support for object$field<-...
._jobjRef_dollargets <- function(x, name, value) {
	if( hasField( x, name ) ){
		.jfield(x, name) <- value
	}
	x
}
setMethod("$<-", c(x="jobjRef"), ._jobjRef_dollargets )

# get a class name for an object
.jclass <- function(o, true=TRUE) {
  if (true) .jcall(.jcall(o, "Ljava/lang/Class;", "getClass"), "S", "getName")
  else if( inherits( o, "jarrayRef" ) ) o@jsig else o@jclass
}

