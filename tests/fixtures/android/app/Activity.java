package android.app;

public class Activity {
    public int finishes;
    public boolean destroyed;
    public void finish() { finishes++; }
    public boolean isFinishing() { return finishes != 0; }
    public boolean isDestroyed() { return destroyed; }
}
