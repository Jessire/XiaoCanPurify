package android.view;
public class ViewGroup extends View {
    public static class LayoutParams {
        public int width, height;
        public LayoutParams(int width, int height) { this.width = width; this.height = height; }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int topMargin, bottomMargin;
        public MarginLayoutParams(int width, int height) { super(width, height); }
    }
}
