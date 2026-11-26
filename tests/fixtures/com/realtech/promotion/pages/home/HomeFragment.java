package com.realtech.promotion.pages.home;
import com.realtech.promotion.pages.home.viewmodel.HomeViewModel;
import com.realtech.common.entity.AppLocationEntity;
import java.util.List;
public final class HomeFragment {
    private final HomeViewModel vm;
    public HomeFragment(HomeViewModel vm) { this.vm = vm; }
    public HomeViewModel getMViewModel() { return vm; }
    public android.view.View view;
    public void lazyLoadData() {}
    public static kotlin.Unit createObserver$lambda$72(HomeFragment home, int version) { return kotlin.Unit.INSTANCE; }
    public android.view.View getView() { return view; }
    public void submitHomePromotionDisplayItems(List<?> rows) {}
    public void bindTrafficExpBHomePager(List<?> rows) {}
    public void onLocationChanged(AppLocationEntity location) {}
}
