package com.realtech.location;
import com.realtech.common.entity.AppLocationEntity;
public final class LocationManager {
    public static final LocationManager INSTANCE = new LocationManager();
    public AppLocationEntity live, cache;
    public boolean cacheThrows;
    public int reads;
    public boolean hasValidLatLng(AppLocationEntity value) { return value != null; }
    public int seeds;
    public void updateLocation(AppLocationEntity value, boolean post, boolean tencent) {
        if (post || !tencent) throw new AssertionError("wrong startup flags");
        live = value; seeds++;
    }
    public AppLocationEntity getMyLocation() { return live; }
    public AppLocationEntity getCachedLocation() {
        reads++;
        if (cacheThrows) throw new IllegalStateException("fixture");
        return cache;
    }
}
