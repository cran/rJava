package org.rosuda.JRI;

public interface RMainLoopCallbacks {
    public void   rWriteConsole (Rengine re, String text);
    public void   rBusy         (Rengine re, int which);
    public String rReadConsole  (Rengine re, String prompt, int addToHistory);
    public void   rShowMessage  (Rengine re, String message);
    public String rChooseFile   (Rengine re, int newFile);
    public void   rFlushConsole (Rengine re);
    public void   rSaveHistory  (Rengine re, String filename);
    public void   rLoadHistory  (Rengine re, String filename);
}
