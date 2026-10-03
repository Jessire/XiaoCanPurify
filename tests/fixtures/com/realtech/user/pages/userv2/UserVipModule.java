package com.realtech.user.pages.userv2;
public class UserVipModule {
    public final RegressionBinding binding = new RegressionBinding();
    public void render(Object state) {}
    public static class Banner extends android.view.View {
        public boolean stopped;
        public void stop() { stopped = true; }
    }
    public static class Params extends android.view.ViewGroup.LayoutParams {
        public int endToStart = 7, rightToLeft = -1, endToEnd = -1, rightToRight = -1;
        public int matchConstraintDefaultWidth = 2;
        public float matchConstraintPercentWidth = 0.667f;
        public Params() { super(0, 100); }
    }
    public static class RegressionBinding {
        public final Banner bannerVipCard = new Banner();
        public final android.view.View bannerIndicator = new android.view.View();
        public final android.view.View cardSilkEarn = new android.view.View();
        public final android.view.View tvWithdrawal = new android.view.View();
        public RegressionBinding() { cardSilkEarn.setLayoutParams(new Params()); }
        public android.view.View getRoot() { return new android.view.View(); }
    }
}
