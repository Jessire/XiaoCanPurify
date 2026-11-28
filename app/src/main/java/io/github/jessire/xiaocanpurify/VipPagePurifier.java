package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.VIP_HIDE_SVIP;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 会员页（原底栏「会员」标签）净化。
 *
 * <p>会员页的三个子页是宿主动态拼出来的：「会员专区」+ 按条件追加的「激活SVIP」+「元宝乐园」。
 * 是否追加 SVIP 完全由 {@code VipFragmentExtKt.shouldShowSvipActivateTab} 一处决定，
 * 所以拦住这一个返回值就等于让该标签不存在，列表位置与其余子页都不受影响。
 */
final class VipPagePurifier {
    private VipPagePurifier() {}

    static void install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> ext = Class.forName("com.realtech.vip.ext.VipFragmentExtKt", false, cl);
            Class<?> userInfo = Class.forName("com.realtech.common.entity.UserInfo", false, cl);
            Method shouldShow = ext.getDeclaredMethod("shouldShowSvipActivateTab", userInfo);
            xposed.hook(shouldShow).intercept(chain ->
                    enabled(VIP_HIDE_SVIP) ? Boolean.FALSE : chain.proceed());
            MainHook.log("Hooked shouldShowSvipActivateTab -> hide activate SVIP tab");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // 未核对的宿主版本不处理。
        } catch (Throwable t) {
            MainHook.log("SVIP activate tab hook unavailable: " + t.getClass().getSimpleName());
        }
    }
}
