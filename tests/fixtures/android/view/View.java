package android.view;
public class View {
    public static final int VISIBLE = 0, GONE = 8;
    private int visibility;
    private ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(100, 100);
    public int getVisibility() { return visibility; }
    public void setVisibility(int value) { visibility = value; }
    public ViewGroup.LayoutParams getLayoutParams() { return params; }
    public void setLayoutParams(ViewGroup.LayoutParams value) { params = value; }
    public ViewTreeObserver getViewTreeObserver() { return new ViewTreeObserver(); }
}
