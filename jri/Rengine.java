package org.rosuda.JRI;

import java.lang.*;

/** Rengine class is the interface between an instance of R and the Java VM. Due to the fact that R has no threading support, you can run only one instance of R withing a multi-threaded application. There are two ways to use R from Java: individual call and full event loop. See the Rengine @{link #Rengine constructor} for details. <p> <u>Important note:</u> All methods starting with <code>rni</code> (R Native Interface) are low-level native methods that should be avoided if a high-level methods exists. At some point in the future when the high-level API is complete they should become private. However, currently this high-level layer is still missing, so they are available for now.<p>All <code>rni</code> methods use <code>long</code> type to reference <code>SEXP</code>s on R side. Those reference should never be modified or used in arithmetics - the only reason for not using an extra interface class to wrap those references is that <code>rni</code> methods are all <i>ative</i> methods and therefore it would be too expensive to handle the unwrapping on the C side.<p><code>JRI</code> (Java/R Interface) methods are called internally by R and invoke the corresponding method from the even loop handler. Those methods should usualy not be called directly. */
public class Rengine extends Thread {
    static {
        try {
            System.loadLibrary("jri");
        } catch (UnsatisfiedLinkError e) {
			System.err.println("Cannot find JRI native library!\n");
            e.printStackTrace();
            System.exit(1);
        }
    }

	/**	API version of the Rengine itself; see also rniGetVersion() for binary version. It's a good idea for the calling program to check the versions of both and abort if they don't match. This should be done using @link{#versionCheck}
		@return version number as <code>long</code> in the form <code>0xMMmm</code> */
    public static long getVersion() {
        return 0x0103;
    }

    /** check API version of this class and the native binary. This is usually a good idea to ensure consistency.
	@return <code>true</code> if the API version of the Java code and the native library matches, <code>false</code> otherwise */
	public static boolean versionCheck() {
		return (getVersion()==rniGetVersion());
	}
	
    /** debug flag. Set to value &gt;0 to enable debugging messages. The verbosity increases with increasing number */
    public static int DEBUG=0;
	
    /** main engine. Since there can be only one instance of R, this is also the only instance. */
    static Rengine mainEngine=null;
    
    /** return the current main R engine instance. Since there can be only one tru eR instance at a time, this is also the only instance. This may not be true for future versions, though.
	@return current instance of the R engine or <code>null</code> if no R engine was started yet. */
    public static Rengine getMainEngine() { return mainEngine; }

    boolean died, alive, runLoop, loopRunning;
    String[] args;
    Mutex Rsync;
    RMainLoopCallbacks callback;
    
    /** create and start a new instance of R. 
	@param args arguments to be passed to R. Please note that R requires the presence of certain arguments, so passing an empty list usually doesn't work.
	@param runMainLoop if set to <code>true</code> the the event loop will be started as soon as possible. This requires <code>initialCallbacks</code> to be set correspondingly as well.
	@param initialCallbacks an instance implementing the {@link org.rosuda.JRI.RMainLoopCallbacks RMainLoopCallbacks} interface that provides methods to be called by R
    */
    public Rengine(String[] args, boolean runMainLoop, RMainLoopCallbacks initialCallbacks) {
        super();
        Rsync=new Mutex();
        died=false;
        alive=false;
        runLoop=runMainLoop;
        loopRunning=false;
        this.args=args;
        callback=initialCallbacks;
        mainEngine=this;
        start();
        while (!alive && !died) yield();
    }

    /** RNI: setup R with supplied parameters (should <b>not</b> be used directly!).
	@param args arguments
	@return result code
     */
    public native int rniSetupR(String[] args);
    
    synchronized int setupR() {
        return setupR(null);
    }
    
    synchronized int setupR(String[] args) {
        int r=rniSetupR(args);
        if (r==0) {
            alive=true; died=false;
        } else {
            alive=false; died=true;
        }
        return r;
    }
    
    /** RNI: parses a string into R expressions (do NOT use directly unless you know exactly what you're doing, where possible use @{link #eval} instead)
	@param s string to parse
	@param parts number of expressions contained in the string
	@return reference to the resulting list of expressions */
    public synchronized native long rniParse(String s, int parts);
    /** RNI: evaluate R expression (do NOT use directly unless you know exactly what you're doing, where possible use @{link #eval} instead)
	@param exp reference to the expression to evaluate
	@param rho environment to use for evaluation (or 0 for global environemnt)
	@return result of the evaluation */
    public synchronized native long rniEval(long exp, long rho);
    
    /** RNI: get the contents of the first entry of a character vector
	@param reference to STRSXP
	@return contents or <code>null</code> if the reference is not STRSXP */
    public synchronized native String rniGetString(long exp);
    /** RNI: get the contents of a character vector
	@param reference to STRSXP
	@return contents or <code>null</code> if the reference is not STRSXP */
    public synchronized native String[] rniGetStringArray(long exp);
    /** RNI: get the contents of an integer vector
	@param reference to INTSXP
	@return contents or <code>null</code> if the reference is not INTSXP */
    public synchronized native int[] rniGetIntArray(long exp);
    /** RNI: get the contents of a numeric vector
	@param reference to REALSXP
	@return contents or <code>null</code> if the reference is not REALSXP */
    public synchronized native double[] rniGetDoubleArray(long exp);
    /** RNI: get the contents of a generic vector (aka list)
	@param reference to VECSXP
	@return contents as an array of references or <code>null</code> if the reference is not VECSXP */
    public synchronized native long[] rniGetVector(long exp);

    /** RNI: create a character vector of the length 1
	@param initial contents of the first entry
	@return reference to the resulting STRSXP */
    public synchronized native long rniPutString(String s);
    /** RNI: create a character vector
	@param initial contents of vector
	@return reference to the resulting STRSXP */
    public synchronized native long rniPutStringArray(String[] a);
    /** RNI: create an integer vector
	@param initial contents of vector
	@return reference to the resulting INTSXP */
    public synchronized native long rniPutIntArray(int [] a);
    /** RNI: create a numeric vector
	@param initial contents of vector
	@return reference to the resulting REALSXP */
    public synchronized native long rniPutDoubleArray(double[] a);
    /** RNI: create a generic vector (aka a list)
	@param initial contents of vector consisiting of an array of references
	@return reference to the resulting VECSXP */
    public synchronized native long rniPutVector(long[] exps);
    
    /** RNI: get an attribute
	@param exp reference to the object whose attribute is requested
	@param name name of the attribute
	@return reference to the attribute or 0 if there is none */
    public synchronized native long rniGetAttr(long exp, String name);
    /** RNI: set an attribute
	@param exp reference to the object whose attribute is to be modified
	@param name attribute name
	@param attr reference to the object to be used as teh contents of the attribute */
    public synchronized native void rniSetAttr(long exp, String name, long attr);

    /** RNI: create a dotted-pair list (LISTSXP)
	@param head CAR
	@param tail CDR (must be a reference to LISTSXP or 0)
	@return reference to the newly created LISTSXP */
    public synchronized native long rniCons(long head, long tail);
    /** RNI: get CAR of a dotted-pair list (LISTSXP)
	@param exp reference to the list
	@return reference to CAR if the list (head) */
    public synchronized native long rniCAR(long exp);
    /** RNI: get CDR of a dotted-pair list (LISTSXP)
	@param exp reference to the list
	@return reference to CDR if the list (tail) */
    public synchronized native long rniCDR(long exp);
    /** RNI: create a dotted-pair list (LISTSXP)
	@param cont contents as an array of references
	@return reference to the newly created LISTSXP */
    public synchronized native long rniPutList(long[] cont);
    /** RNI: retrieve CAR part of a dotted-part list recursively as an array of references
	@param exp reference to a dotted-pair list (LISTSXP)
	@return contents of the list as an array of references */
    public synchronized native long[] rniGetList(long exp);

    //public static native void rniSetEnv(String key, String val);
    //public static native String rniGetEnv(String key);
	
    /** RNI: return the API version of the native library
	@return API version of the native library */
    public static native long rniGetVersion();
    
    /** RNI: interrupt the R process (if possible)
	@param flag currently ignored, set to 0
	@return result code (currently unspecified) */
    public native int rniStop(int flag);
    
    /** RNI: assign a value to an environment
	@param name name
	@param exp value
	@param rho environment (use 0 for the global environment) */
    public synchronized native void rniAssign(String name, long exp, long rho);
    
    /** RNI: get the SEXP type
	@param exp reference to a SEXP
	@return type of the expression (see xxxSEXP constants) */
    public synchronized native int rniExpType(long exp);
    /** RNI: run the main loop.<br> <i>Note:</i> this is an internal method and ot doesn't return until the loop exits. Don't use directly! */
    public native void rniRunMainLoop();
    
    /** RNI: run other event handlers in R */
    public synchronized native void rniIdle();

    /** Add a handler for R callbacks. The current implementation supports only one handler at a time, so call to this function implicitly removes any previous handlers */
    public void addMainLoopCallbacks(RMainLoopCallbacks c)
    {
        // we don't really "add", we just replace ... (so far)
        callback = c;
    }

    /** if Rengine was initialized with <code>runMainLoop=false</code> then this method can be used to start the main loop at a later point. It has no effect if the loop is running already. This method returns immediately but the loop will be started once the engine is ready. */
    public void startMainLoop() {
	runLoop=true;
    }
    
    //============ R callback methods =========

    /** JRI: R_WriteConsole call-back from R
	@param text text to disply */
    public void jriWriteConsole(String text)
    {
        if (callback!=null) callback.rWriteConsole(this, text);
    }

    /** JRI: R_Busy call-back from R
	@param which state */
    public void jriBusy(int which)
    {
        if (callback!=null) callback.rBusy(this, which);
    }

    /** JRI: R_ReadConsole call-back from R.
	@param prompt prompt to display before waiting for the input
	@param addToHistory flag specifying whether the entered contents should be added to history
	@return content entered by the user. Returning <code>null</code> corresponds to an EOF attempt. */
    public String jriReadConsole(String prompt, int addToHistory)
    {
		if (DEBUG>0)
			System.out.println("Rengine.jreReadConsole BEGIN "+Thread.currentThread());
        Rsync.unlock();
        String s=(callback==null)?null:callback.rReadConsole(this, prompt, addToHistory);
        if (!Rsync.safeLock()) {
            String es="\n>>JRI Warning: jriReadConsole detected a possible deadlock ["+Rsync+"]["+Thread.currentThread()+"]. Proceeding without lock, but this is inherently unsafe.\n";
            jriWriteConsole(es);
            System.err.print(es);
        }
		if (DEBUG>0)
			System.out.println("Rengine.jreReadConsole END "+Thread.currentThread());
        return s;
    }

    /** JRI: R_ShowMessage call-back from R
	@param message message */
    public void jriShowMessage(String message)
    {
        if (callback!=null) callback.rShowMessage(this, message);
    }
    
    /** JRI: R_loadhistory call-back from R
	@param filename name of the history file */
    public void jriLoadHistory(String filename)
    {
        if (callback!=null) callback.rLoadHistory(this, filename);
    }

    /** JRI: R_savehistory call-back from R
	@param filename name of the history file */
    public void jriSaveHistory(String filename)
    {
        if (callback!=null) callback.rSaveHistory(this, filename);
    }
	
    /** JRI: R_ChooseFile call-back from R
	@param newFile flag specifying whether an existing or new file is requested
	@return name of the selected file or <code>null</code> if cancelled */
    public String jriChooseFile(int newFile)
    {
        if (callback!=null) return callback.rChooseFile(this, newFile);
		return null;
    }
	
    /** JRI: R_FlushConsole call-back from R */	
    public void jriFlushConsole()
    {
        if (callback!=null) callback.rFlushConsole(this);
    }
	
    
    //============ "official" API =============


    /** Parses and evaluates an R expression and returns the result
	@param s expression (as string) to parse and evaluate
	@return resulting expression or <code>null</code> if something wnet wrong */
    public synchronized REXP eval(String s) {
		if (DEBUG>0)
			System.out.println("Rengine.eval("+s+"): BEGIN "+Thread.currentThread());
        boolean obtainedLock=Rsync.safeLock();
        try {
            /* --- so far, we ignore this, because it can happen when a callback needs an eval which is ok ...
            if (!obtainedLock) {
                String es="\n>>JRI Warning: eval(\""+s+"\") detected a possible deadlock ["+Rsync+"]["+Thread.currentThread()+"]. Proceeding without lock, but this is inherently unsafe.\n";
                jriWriteConsole(es);
                System.err.print(es);
            }
             */
            long pr=rniParse(s, 1);
            if (pr>0) {
                long er=rniEval(pr, 0);
                if (er>0) {
                    REXP x=new REXP(this, er);
                    if (DEBUG>0) System.out.println("Rengine.eval("+s+"): END (OK)"+Thread.currentThread());
                    return x;
                }
            }
        } finally {
            if (obtainedLock) Rsync.unlock();
        }
        if (DEBUG>0) System.out.println("Rengine.eval("+s+"): END (ERR)"+Thread.currentThread());
        return null;
    }
    
    /** This method is very much like {@link #eval(String)}, except that it is non-blocking and returns <code>null</code> if the engine is busy.
        @param s string to evaluate
        @return result of the evaluation or <code>null</code> if the engine is busy
        */
    public synchronized REXP idleEval(String s) {
        int lockStatus=Rsync.tryLock();
        if (lockStatus==1) return null; // 1=locked by someone else
        boolean obtainedLock=(lockStatus==0);
        try {
            long pr=rniParse(s, 1);
            if (pr>0) {
                long er=rniEval(pr, 0);
                if (er>0) {
                    REXP x=new REXP(this, er);
                    return x;
                }
            }
        } finally {
            if (obtainedLock) Rsync.unlock();
        }
        return null;
    }
    
    /** check the state of R
	@return <code>true</code> if R is alive and <code>false</code> if R died or exitted */
    public synchronized boolean waitForR() {
        return alive;
    }

    /** attempt to shut down R. This method is asynchronous. */
    public void end() {
        alive=false;
        interrupt();
    }
    
    /** The implementation of the R thread. This method should not be called directly. */	
    public void run() {
		if (DEBUG>0)
			System.out.println("Starting R...");
        if (setupR(args)==0) {
            while (alive) {
                try {
                    if (runLoop) {                        
						if (DEBUG>0)
							System.out.println("***> launching main loop:");
                        loopRunning=true;
                        rniRunMainLoop();
                        loopRunning=false;
						if (DEBUG>0)
							System.out.println("***> main loop finished:");
                        System.exit(0);
                    }
                    sleep(100);
                    if (runLoop) rniIdle();
                } catch (InterruptedException ie) {
                    interrupted();
                }
            }
            died=true;
			if (DEBUG>0)
				System.out.println("Terminating R thread.");
        } else {
			System.err.println("Unable to start R");
        }
    }
}
