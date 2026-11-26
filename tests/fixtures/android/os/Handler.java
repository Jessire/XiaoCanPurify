package android.os;
public final class Handler {
    public static Runnable pending;
    public Handler(Looper looper) {}
    public boolean postDelayed(Runnable callback, long delay) { pending = callback; return true; }
}
