package io.github.jessire.xiaocanpurify;

/** Insertion and inverse hit mapping in pixels local to the untransformed Flutter viewport. */
record SettingsRowGeometry(int seam, int height, int left, int right, int heading, int phone,
                           boolean dragFromHeading) {
    static int findSeam(int headingBottom, int phoneTop, int viewportTop, int viewportBottom) {
        return headingBottom > viewportTop && phoneTop >= headingBottom && headingBottom < viewportBottom
                && phoneTop < viewportBottom ? headingBottom : -1;
    }

    boolean showsRow() { return left < right; }
    boolean inGap(float y) { return y >= seam && y < seam + height; }
    boolean inBand(float x, float y, int width) { return showsRow() && inGap(y) && x >= 0 && x < width; }
    boolean inRow(float x, float y) { return inGap(y) && x >= left && x < right; }
    int offsetAt(float y) { return y >= seam + height ? height : 0; }
    int mapTop(int y) { return y >= seam ? y + height : y; }
    int mapBottom(int y) { return y > seam ? y + height : y; }
    boolean cuts(int top, int bottom) { return top < seam && seam < bottom; }
    boolean sameHitBand(float y, int offset) { return !inGap(y) && offsetAt(y) == offset; }
}
