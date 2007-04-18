import java.io.File;

public class JRIBootstrap {
	//--- global constants ---
	public static final int HKLM = 0; // HKEY_LOCAL_MACHINE
	public static final int HKCU = 1; // HKEY_CURRENT_USER

	//--- native methods ---
	public static native String getenv(String var);
	public static native void setenv(String var, String val);

	public static native String regvalue(int root, String key, String value);
	public static native String[] regsubkeys(int root, String key);

	public static native String expand(String val);

	//--- helper methods ---	
	static void fail(String msg) {
		System.err.println("ERROR: "+msg);
		System.exit(1);
	}
	
	//--- main bootstrap method ---
	public static void bootstrap(String[] args) {
		System.out.println("JRIBootstrap("+args+")");
		try {
			System.loadLibrary("boot");
		} catch (Exception e) {
			fail("Unable to load boot library!");
		}
		// just testing from now on
		System.out.println("PATH="+getenv("PATH"));
		Main.main(args);
	}
}
