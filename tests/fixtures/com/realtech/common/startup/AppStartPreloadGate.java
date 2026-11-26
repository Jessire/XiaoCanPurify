package com.realtech.common.startup;
import kotlin.jvm.functions.Function0;
public final class AppStartPreloadGate {
    public static final AppStartPreloadGate INSTANCE = new AppStartPreloadGate();
    private static Function0 abortDelayAction;
    public void setAbortDelayAction(Function0 a) { abortDelayAction = a; }
    public void abortDelay() {
        Function0 a = abortDelayAction;
        abortDelayAction = null;
        if (a != null) a.invoke();
    }
}
