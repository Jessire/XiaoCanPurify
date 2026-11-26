package com.realtech.promotion.pages.detail;

public final class PromotionDetailActivity {
    public int exitWork;
    public int dailyWrite;
    public void onDestroy(Runnable navigation) {
        try {
            exitWork++;
            foodDetectiveCheck(navigation);
        } finally {
            exitWork++;
        }
    }
    public void foodDetectiveCheck(Runnable navigation) {
        dailyWrite++;
        navigation.run();
    }
    public void openManually(Runnable navigation) { navigation.run(); }
}
