package com.realtech.promotion.pages.home.viewmodel;
public final class HomeViewModel {
    private final kotlinx.coroutines.flow.MutableStateFlow rows = new kotlinx.coroutines.flow.StateFlowImpl();
    private final kotlinx.coroutines.flow.MutableStateFlow pages = new kotlinx.coroutines.flow.StateFlowImpl();
    public kotlinx.coroutines.flow.MutableStateFlow getTrafficExpBPageStates() { return pages; }
    public boolean loaded;
    public kotlinx.coroutines.CompletableDeferred outcome;
    public kotlinx.coroutines.CompletableDeferred getStartupRefreshOutcome$promotion_release() { return outcome; }
    public com.realtech.data.GetStorePromotionListRequest params = new com.realtech.data.GetStorePromotionListRequest();
    public com.realtech.data.GetStorePromotionListRequest getParams() { return params; }
    public boolean isFirstLoaded() { return loaded; }
    public kotlinx.coroutines.flow.MutableStateFlow getPromotionListState() { return rows; }
    public boolean canUseHomeSecondFloor() { return true; }
}
