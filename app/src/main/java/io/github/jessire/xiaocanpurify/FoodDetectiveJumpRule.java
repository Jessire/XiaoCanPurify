package io.github.jessire.xiaocanpurify;

import java.util.regex.Pattern;

final class FoodDetectiveJumpRule {
    private static final String DETAIL = "com.realtech.promotion.pages.detail.";
    private static final Pattern AUTO_SUFFIX = Pattern.compile("&from_source=3&t=[0-9]+\\z");

    private FoodDetectiveJumpRule() {}

    static boolean hasAutoSuffix(String link) {
        return link != null && AUTO_SUFFIX.matcher(link).find();
    }

    static boolean isDetailExit(StackTraceElement[] stack) {
        for (int i = 0; i + 1 < stack.length; i++) {
            StackTraceElement check = stack[i];
            if (!check.getClassName().startsWith("com.realtech.")) continue;
            // Only the first host frame may be the verified caller, not an ancestor of another route.
            if (!"foodDetectiveCheck".equals(check.getMethodName())) return false;
            String owner = check.getClassName();
            StackTraceElement caller = stack[i + 1];
            switch (owner) {
                case DETAIL + "PromotionDetailActivity":
                case DETAIL + "dp.DpPromotionDetailActivity":
                    return is(caller, owner, "onDestroy");
                case DETAIL + "mt.MtPromotionDetailActivity":
                    return is(caller, owner, "onPause");
                case DETAIL + "quick.QuickPromotionDetailBottomSheet":
                case DETAIL + "dp.DpQuickPromotionDetailBottomSheet":
                case DETAIL + "mt.quick.MtQuickPromotionDetailBottomSheet":
                    // The bytecode uses a synthetic accessor; ART can omit the inlined frame.
                    if (is(caller, owner, "access$foodDetectiveCheck") && i + 2 < stack.length) {
                        caller = stack[i + 2];
                    }
                    return is(caller, owner + "$createObserver$1", "onDestroy");
                default:
                    return false;
            }
        }
        return false;
    }

    private static boolean is(StackTraceElement frame, String owner, String method) {
        return owner.equals(frame.getClassName()) && method.equals(frame.getMethodName());
    }
}
