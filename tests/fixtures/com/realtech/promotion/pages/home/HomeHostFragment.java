package com.realtech.promotion.pages.home;
import android.os.Bundle;
import android.view.View;
import com.realtech.promotion.pages.home.viewmodel.HomeViewModel;
public final class HomeHostFragment {
    private final HomeViewModel vm;
    public HomeHostFragment(HomeViewModel vm) { this.vm = vm; }
    public HomeViewModel getViewModel() { return vm; }
    public void onViewCreated(View view, Bundle state) {}
}
