package com.realtech.location.utils;
import android.content.Context;
import kotlin.coroutines.Continuation;
public final class LocationUtils {
    public static final LocationUtils INSTANCE = new LocationUtils();
    public boolean allowed = true;
    public boolean checkLocation(Context c) { return allowed; }
    public Object getLocation(boolean force, boolean update, Continuation cont) { return null; }
}
