package com.realtech.webpage.web;

import java.lang.ref.WeakReference;

public class WebControllerImpl {
    public final WeakReference<Object> hostWeakRef;
    public Object agent;

    public WebControllerImpl(AgentWebFragment fragment) {
        hostWeakRef = new WeakReference<>(fragment);
        agent = fragment.agent;
    }
    public Object getAgentWeb() { return agent; }
    public final void callJsRetention() {}
    public void closeWebView(Runnable retention) { retention.run(); }
}
