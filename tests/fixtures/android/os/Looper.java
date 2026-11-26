package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static boolean mainThread = true;
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return mainThread ? MAIN : null; }
}
