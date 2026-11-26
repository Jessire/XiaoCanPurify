package io.github.jessire.xiaocanpurify;

/** Host intent, independent of the visibility temporarily exposed to Android. */
final class HomeLiveVisibility {
    private final int gone;
    private int host;
    private boolean masked;

    HomeLiveVisibility(int initial, int gone) {
        host = initial;
        this.gone = gone;
    }

    boolean hostWrite(int value, boolean hide) {
        host = value;
        masked = hide;
        return hide && value != gone;
    }

    int apply(int observed, boolean hide) {
        if (!masked || observed != gone) host = observed;
        masked = hide;
        return hide ? gone : host;
    }
}
