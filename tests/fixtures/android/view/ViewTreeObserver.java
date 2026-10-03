package android.view;
public class ViewTreeObserver {
    public interface OnGlobalLayoutListener { void onGlobalLayout(); }
    public void addOnGlobalLayoutListener(OnGlobalLayoutListener listener) {}
}
