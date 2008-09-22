import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Vector;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.zip.*;

public class RJavaClassLoader extends URLClassLoader {
    String rJavaPath, rJavaLibPath;
    HashMap libMap;
    Vector classPath;

    public static RJavaClassLoader primaryLoader = null;

    public static boolean verbose = false;

    public boolean useSystem = true;

    class UnixFile extends java.io.File {
	long lastModStamp;
	public Object cache;

	public UnixFile(String fn) {
	    super((separatorChar != '/')?fn.replace('/',separatorChar):fn);
	    lastModStamp=0;
	}

	public boolean hasChanged() {
	    long curMod = lastModified();
	    return (curMod != lastModStamp);
	}
	
	public void update() {
	    lastModStamp = lastModified();
	}
    }

    public static RJavaClassLoader getPrimaryLoader() {
	return primaryLoader;
    }

    public RJavaClassLoader(String path, String libpath) {
	super(new URL[] {});
	if (verbose) System.out.println("RJavaClassLoader(\""+path+"\",\""+libpath+"\")");
	if (primaryLoader==null) {
	    primaryLoader = this;
	    if (verbose) System.out.println(" - primary loader");
	} else {
	    if (verbose) System.out.println(" - NOT privrary (this="+this+", primary="+primaryLoader+")");
	}
	libMap = new HashMap();
	classPath = new Vector();
	rJavaPath = path;
	rJavaLibPath = libpath;
	classPath.add(new UnixFile(path+"/java"));
	UnixFile so = new UnixFile(rJavaLibPath+"/rJava.so");
	if (!so.exists())
	    so = new UnixFile(rJavaLibPath+"/rJava.dll");
	if (so.exists())
	    libMap.put("rJava", so);
	
	UnixFile jri = new UnixFile(path+"/jri/libjri.so");
	String rarch = System.getProperty("r.arch");
	if (rarch != null && rarch.length()>0) {
	    UnixFile af = new UnixFile(path+"/jri"+rarch+"/libjri.so");
	    if (af.exists()) jri=af;
	}
	if (!jri.exists())
	    jri = new UnixFile(path+"/jri/libjri.jnilib");
	if (!jri.exists())
	    jri = new UnixFile(path+"/jri/jri.dll");
	if (jri.exists()) {
	    libMap.put("jri", jri);
	    if (verbose) System.out.println(" - registered JRI: "+jri);
	}
    }

    String classNameToFile(String cls) {
	// convert . to /
	return cls.replace('.','/');
    }

    InputStream findClassInJAR(UnixFile jar, String cl) {
	String cfn = classNameToFile(cl)+".class";

	if (jar.cache==null || jar.hasChanged()) {
	    try {
		if (jar.cache!=null) ((ZipFile)jar.cache).close();
	    } catch (Exception tryCloseX) {}
	    jar.update();
	    try {
		jar.cache = new ZipFile(jar);
	    } catch (Exception zipCacheX) {}
	    if (verbose) System.out.println("RJavaClassLoader: creating cache for "+jar);
	}
        try {
	    ZipFile zf = (ZipFile) jar.cache;
	    if (zf == null) return null;
            ZipEntry e = zf.getEntry(cfn);
	    if (e != null)
		return zf.getInputStream(e);
        } catch(Exception e) {
	    if (verbose) System.err.println("findClassInJAR: exception: "+e.getMessage());
        }
	return null;
    }
    
    URL findInJARURL(String jar, String fn) {
        try {
            ZipInputStream ins = new ZipInputStream(new FileInputStream(jar));
	    
            ZipEntry e;
            while ((e=ins.getNextEntry())!=null) {
		if (e.getName().equals(fn)) {
		    ins.close();
		    try {
			return new URL("jar:"+(new UnixFile(jar)).toURL().toString()+"!"+fn);
		    } catch (Exception ex) {
		    }
		    break;
		}
	    }
        } catch(Exception e) {
	    if (verbose) System.err.println("findInJAR: exception: "+e.getMessage());
        }
	return null;
    }
    
    protected Class findClass(String name) throws ClassNotFoundException {
	Class cl = null;
	//System.out.println(""+this+".findClass("+name+")");
	if ("RJavaClassLoader".equals(name)) return getClass();
	if (useSystem) {
	    try {
		cl = super.findClass(name);
		if (cl != null) {
		    if (verbose) System.out.println("RJavaClassLoader: found class "+name+" using URL loader");
		    return cl;
		}
	    } catch (Exception fnf) {
	    }	    
	}
	if (verbose) System.out.println("RJavaClassLoaaer.findClass(\""+name+"\")");

	InputStream ins = null;

	for (Enumeration e = classPath.elements() ; e.hasMoreElements() ;) {
	    UnixFile cp = (UnixFile) e.nextElement();
	 
	    if (verbose) System.out.println(" - trying class path \""+cp+"\"");
	    try {
		ins = null;
		if (cp.isFile())
		    ins = findClassInJAR(cp, name);
		if (ins == null && cp.isDirectory()) {
		    UnixFile class_f = new UnixFile(cp.getPath()+"/"+classNameToFile(name)+".class");
		    if (class_f.isFile()) {
			ins = new FileInputStream(class_f);
		    }
		}
		if (ins != null) {
		    int al = 128*1024;
		    byte fc[] = new byte[al];
		    int n = ins.read(fc);
		    int rp = n;
		    // System.out.println("  loading class file, initial n = "+n);
		    while (n > 0) {
			if (rp == al) {
			    int nexa = al*2;
			    if (nexa<512*1024) nexa=512*1024;
			    byte la[] = new byte[nexa];
			    System.arraycopy(fc, 0, la, 0, al);
			    fc = la;
			    al = nexa;
			}
			n = ins.read(fc, rp, fc.length-rp);
			// System.out.println("  next n = "+n+" (rp="+rp+", al="+al+")");
			if (n>0) rp += n;
		    }
		    ins.close();
		    n = rp;
		    if (verbose) System.out.println("RJavaClassLoader: loaded class "+name+", "+n+" bytes");
		    cl = defineClass(name, fc, 0, n);
		    // System.out.println(" - class = "+cl);
		    return cl;
		}
	    } catch (Exception ex) {
		// System.out.println(" * won't work: "+ex.getMessage());
	    }
	}
	// System.out.println("=== giving up");
	if (cl == null) {
	    throw (new ClassNotFoundException());
	}
	return cl;
    }

    public URL findResource(String name) {
	if (verbose) System.out.println("RJavaClassLoader: findResource('"+name+"')");
	if (useSystem) {
	    try {
		URL u = super.findResource(name);
		if (u != null) {
		    if (verbose) System.out.println("RJavaClassLoader: found resource in "+u+" using URL loader.");
		    return u;
		}
	    } catch (Exception fre) {
	    }
	}
	for (Enumeration e = classPath.elements() ; e.hasMoreElements() ;) {
	    UnixFile cp = (UnixFile) e.nextElement();
	 
	    try {
		if (cp.isFile()) {
		    URL u = findInJARURL(cp.getPath(), name);
		    if (u != null) {
			if (verbose) System.out.println(" - found in a JAR file, URL "+u);
			return u;
		    }
		}
		if (cp.isDirectory()) {
		    UnixFile res_f = new UnixFile(cp.getPath()+"/"+name);
		    if (res_f.isFile()) {
			if (verbose) System.out.println(" - find as a file: "+res_f);
			return res_f.toURL();
		    }
		}
	    } catch (Exception iox) {
	    }
	}
	return null;
    }

    /** add a library to path mapping for a native library */
    public void addRLibrary(String name, String path) {
	libMap.put(name, new UnixFile(path));
    }

    public void addClassPath(String cp) {
	if (useSystem) {
	    try {
		addURL((new UnixFile(cp)).toURL());
		//return; // we need to add it anyway
	    } catch (Exception ufe) {
	    }
	}
	UnixFile f = new UnixFile(cp);
	if (!classPath.contains(f)) {
	    classPath.add(f);
	    System.setProperty("java.class.path",
			       System.getProperty("java.class.path")+File.pathSeparator+f.getPath());
	}
    }

    public void addClassPath(String[] cp) {
	int i = 0;
	while (i < cp.length) addClassPath(cp[i++]);
    }

    public String[] getClassPath() {
	int j = classPath.size();
	String[] s = new String[j];
	int i = 0;
	while (i < j) {
	    s[i] = ((UnixFile) classPath.elementAt(i)).getPath();
	    i++;
	}
	return s;
    }

    protected String findLibrary(String name) {
	if (verbose) System.out.println("RJavaClassLoaaer.findLibrary(\""+name+"\")");
	//if (name.equals("rJava"))
	//    return rJavaLibPath+"/"+name+".so";

	UnixFile u = (UnixFile) libMap.get(name);
	String s = null;
	if (u!=null && u.exists()) s=u.getPath();
	if (verbose) System.out.println(" - mapping to "+((s==null)?"<none>":s));

	return s;
    }

    public void bootClass(String cName, String mName, String[] args) throws java.lang.IllegalAccessException, java.lang.reflect.InvocationTargetException, java.lang.NoSuchMethodException, java.lang.ClassNotFoundException {
	Class c = findClass(cName);
	resolveClass(c);
	java.lang.reflect.Method m = c.getMethod(mName, new Class[] { String[].class });
	m.invoke(null, new Object[] { args });
    }

    public static void setDebug(int level) {
	verbose=(level>0);
    }

    public static String u2w(String fn) {
        return (java.io.File.separatorChar != '/')?fn.replace('/',java.io.File.separatorChar):fn;
    }

    public static void main(String[] args) {
	String rJavaPath = System.getProperty("rjava.path");
	if (rJavaPath  == null) {
	    System.err.println("ERROR: rjava.path is not set");
	    System.exit(2);
	}
	String rJavaLib = System.getProperty("rjava.lib");
	if (rJavaLib == null) { // it is not really used so far, just for rJava.so, so we can guess
	    rJavaLib = rJavaPath + File.separator + "libs";
	}
	RJavaClassLoader cl = new RJavaClassLoader(u2w(rJavaPath), u2w(rJavaLib));
	String mainClass = System.getProperty("main.class");
	if (mainClass == null || mainClass.length()<1) {
	    System.err.println("WARNING: main.class not specified, assuming 'Main'");
	    mainClass = "Main";
	}
	String classPath = System.getProperty("rjava.class.path");
	if (classPath != null) {
	    StringTokenizer st = new StringTokenizer(classPath, File.pathSeparator);
	    while (st.hasMoreTokens()) {
		String dirname = u2w(st.nextToken());
		cl.addClassPath(dirname);
	    }
	}
	try {
	    cl.bootClass(mainClass, "main", args);
	} catch (Exception ex) { 
	    System.err.println("ERROR: while running main method: "+ex);
	    ex.printStackTrace();
	}
    }
    
    //----- tools -----

    class RJavaObjectInputStream extends ObjectInputStream {
	public RJavaObjectInputStream(InputStream in) throws IOException {
		super(in);
	}
	protected Class resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
	    return Class.forName(desc.getName(), false, RJavaClassLoader.getPrimaryLoader());
	}
    }

    /** Serialize an object to a byte array. (code by CB) */
    public static byte[] toByte(Object object)
        throws Exception
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream   oos = new ObjectOutputStream((OutputStream) os);
        oos.writeObject(object);
        oos.close();
        return os.toByteArray();
    }

    /** Deserialize an object from a byte array. (code by CB) */
    public Object toObject(byte[] byteArray)
        throws Exception
    {
        InputStream        is = new ByteArrayInputStream(byteArray);
        RJavaObjectInputStream ois = new RJavaObjectInputStream(is);
        Object o = (Object) ois.readObject();
        ois.close();
        return o;
    }

    public static Object toObjectPL(byte[] byteArray) throws Exception
    {
	return RJavaClassLoader.getPrimaryLoader().toObject(byteArray);
    }
}
