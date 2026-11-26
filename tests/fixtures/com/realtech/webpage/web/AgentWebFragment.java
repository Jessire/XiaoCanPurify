package com.realtech.webpage.web;

import android.app.Activity;
import android.webkit.WebView;

public class AgentWebFragment {
    public final WebView web = new WebView();
    public Activity activity = new CommonWebActivity();
    public Object agent = new Object();
    public boolean history;
    public boolean canJsBack;
    public int historyMoves;

    public Activity getActivity() { return activity; }
    public WebView getWebView() { return web; }
    public Object getAgentWeb() { return agent; }
    public final void callJsRetention() {}
    public void onBackClick(Runnable retention) {
        if (history) historyMoves++;
        else if (canJsBack) activity.finish();
        else retention.run();
    }
    public void otherCaller(Runnable retention) { retention.run(); }
}
