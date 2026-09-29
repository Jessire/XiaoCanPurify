# 小蚕净化 (XiaoCanPurify)

针对小蚕霸王餐 (`com.realtech.xiaocan`) 的现代化 Xposed / LSPosed API 102 净化模块。

## 功能特性

1. **去除所有广告 (All Ads Removal)**
   - **冷启动开屏广告**：自动秒跳 `SplashActivity` / `BaseAdActivity` 进入主界面。
   - **切后台热启动开屏**：拦截 `SplashAdUtils.initAd` 与 `SplashAdPlacementManager.refreshAsync`，彻底阻断回到前台时的开屏广告。
   - **全屏/激励/插屏广告**：拦截并自动关闭穿山甲 (CSJ/Pangle)、优量汇 (GDT)、快手 (Kwad)、Sigmob、Baidu、ToBid/WindMill、Beizi 等第三方 SDK 广告 Activity。
   - **信息流与卡片广告**：屏蔽 `BaseBqtContainerView` 与 `BqtAdContainerView`，置为 `View.GONE` 且零宽高；桩化 `AdServiceFactoryImpl` 广告流。
   - **Flutter 广告插件**：拦截 `AmSdkPlugin`、`AmInterstitialAdHandler` 及 `WindmillAdPlugin` 的 MethodChannel 广告分发。
   - **静默任务引擎**：阻断 `SilentTaskEngine.start`，禁用后台隐蔽任务加载与上报。
   - **首页广告元素**：隐藏 `HomeRedPackRainFabView` (浮动红包)、`HomeNewUserFabView` (新手引导)、顶部横幅 Banner、搜索右侧 Banner (`HomeAppBarLayoutView`)。

2. **去除各类弹窗 (Popups Removal)**
   - **首页营销活动弹窗**：自动拦截 `SharerHomePopupDialog`、`FirstOrderFullAmountAgencyDialog` (首单全返)。
   - **红包与商城弹窗**：拦截 `DouyinMallBonusDialog`、生日/周年庆蛋糕弹窗、分享奖励弹窗 (`OpenSchoolshareDialog`, `AnnualReportShareDialog` 等)。
   - **应用升级弹窗**：拦截 `AppUpdateServiceImpl.checkUpdate` 与 `MainViewModel.checkUpdate`，彻底杜绝强更/检查更新弹窗。
   - **元宝与通知弹窗**：拦截 `MainViewModel.checkSyceeUpdateDialog`、`checkSchoolStarts` 与 `takeGiftReq`。
   - **全局弹窗队列**：净化 `DialogUtils.addDialog` 与 Kuikly `GlobalDialogQueue.show`，阻断营销类弹窗入队。

3. **精简底部导航栏 (Bottom Navigation Simplification)**
   - 彻底移除中间的「会员」(VIP) 与「福利/赚钱」(Task/Benefit) 两个冗余标签。
   - 底部导航栏仅保留三个核心 Tab：
     1. **首页** (`HOME`)
     2. **订单** (`ORDER`)
     3. **我** (`USER`)
   - 联动 ViewPager2，Tab 索引无缝重映射，保留原有的到店/订单交互以及「我的」页面切换与未读提醒逻辑。

4. **网络层过滤 (Network Layer Filtering)**
   - 动态注入 OkHttp Interceptor，针对 `/g/pa` (placement 广告配置)、AdProLink、Sigmob、1rtb、Pangle、GDT、Kwad、Burying 埋点等流量实施本地拦截，返回纯净空响应。

## 构建与安装

- 编译环境：Microsoft JDK 17, Gradle 9.5.1, Android SDK 37 (compileSdk=37, minSdk=26)
- 产物路径：`XiaoCanPurify-1.0.0-release.apk`
- 安装步骤：
  1. 将生成的 APK 安装到设备。
  2. 在 LSPosed Manager 中启用「小蚕净化」模块。
  3. 作用域勾选「小蚕霸王餐」(`com.realtech.xiaocan`)。
  4. 强制停止并重新启动小蚕霸王餐即可享受清爽体验。
