package com.therouter.router;

import android.content.Context;
import android.os.Bundle;
import com.therouter.router.interceptor.NavigationCallback;

public final class Navigator {
    private final String route;
    private final Bundle extras = new Bundle();
    public Navigator(String route, String link) {
        this.route = route;
        extras.putString("link", link);
    }
    public String getOriginalUrl() { return route; }
    public Bundle getExtras() { return extras; }
    public static void navigation$default(Navigator nav, Context context, NavigationCallback callback, int mask, Object marker) {}
}
