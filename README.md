# 小蚕净化 (XiaoCanPurify)

针对小蚕霸王餐 (`com.realtech.xiaocan`) 的现代化 Xposed / LSPosed API 102 净化模块。

## 功能特性

1. **广告过滤 (Ads Filtering)**
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

## 版本

当前源码版本：**2.1.1** (`versionCode=28`)，适配小蚕霸王餐 **3.23.0** (3070)。

- 底栏拆成独立开关：「首页」「订单」「会员」「福利」均可单独隐藏，点按即时生效；默认仍只留首页、订单、我的（四项全隐藏时保留首页，「我的」不隐藏）。
- 修复隐藏「会员」后点「领元宝」没反应的问题，现在可正常打开元宝页领取。
- 新增「屏蔽会员页『激活SVIP』标签」开关（默认开启，即时生效），会员页仅保留会员专区和元宝乐园。

以下是 **2.1** (`versionCode=27`) 的历史发布记录：

- 适配小蚕 3.23.0：恢复「模块设置」入口和底栏「订单」标签，补拦新版插屏/横幅广告、首页通知与会员弹窗、详情页促销气泡、福利页推广跳转及搜券退出挽留。
- 新增「屏蔽首页大牌券专享活动」（默认开启）与「隐藏底栏『订单』」（默认关闭，即时生效）两项开关，共 40 项。
- 首页去横幅后统一为浅橙底色；修复设置页夜间模式文字对比度、底部重启图标，以及网络超时和空配置引发的偶发崩溃。

以下是 **2.0** (`versionCode=24`) 的历史发布记录：

- 新增「我的 → 设置」内的模块设置页与 38 个分项开关，点按即保存，大部分规则无需重启。
- 新增首页「你有一单可免单」与周年庆品牌券弹窗拦截、原生上传凭证页广告移除、提现成功页公众号关注推广关闭。
- 新增新店推荐任务成套屏蔽：详情页自动跳转、订单任务网页（含手动打开）与网页退出挽留，均可单独关闭。
- 移除模块桌面图标与自带页面，设置入口统一收进小蚕内部。

以下是 **1.2** (`versionCode=3`) 的历史发布记录：

- 补全提现页与提现成功弹窗广告位拦截 (`WITHDRAWAL_SUCCESS_POPUP`、`WITHDRAWALPAGE_POPUP`、广告位 `8876` 及列表型 `resource_slug` 参数), 不丢弃混合业务广告位请求.
- 恢复正常下拉刷新, 只屏蔽下拉进入第二层.
- 补充首页运营弹窗、详情页 ToBid 广告与 Flutter 广告通道过滤.
- 移除提现右侧会员轮播及详情页“分享赚豆”浮动入口.
- 增加报名自动外跳保护及淘宝安装领红包提示拦截.

完整更新内容见 [更新日志](CHANGELOG.md).

完整更新内容见 [更新日志](CHANGELOG.md).

## 净化规则设置

模块没有独立界面和桌面图标；LSPosed 管理器仍用于启用模块和选择作用域。设置入口在小蚕内部：打开「我的 → 设置」，在「基础设置」下方点击「模块设置」。开关按「首页 / 订单 / 我的 / 全局」分页，页内再区分「即时生效」与「结构与初始化」。开关打开表示屏蔽，未设置过的规则默认开启，底栏精简及其索引映射由同一个开关控制。

开关点按即写盘，没有保存按钮。大部分规则立即生效；需要重建界面的项（底栏、首页主题与布局、首页下拉第二层等）保存后底部会浮出提示条，点「立即重启」重启小蚕即可，登录状态与订单不受影响。关闭某项会放行对应规则，不会关闭全局广告过滤、弹窗过滤或报名外跳保护；如果宿主未下发相关内容，该布局不一定显示。

「活动详情」的「阻止详情离开时自动打开新店推荐任务」默认开启。仅对小蚕 3.21.2（2985）的已识别自动跳转生效，不屏蔽手动入口，也不会重置宿主的当日检查记录。

「订单」的两项：「隐藏原生上传凭证页广告与占位」和「关闭新店推荐任务的网页退出挽留」均默认开启，仅对小蚕 3.21.2（2985）的已识别路径生效，不修改凭证提交、失败提示、结果页或手动路由。另有「屏蔽新店推荐任务网页（含手动打开）」，只按已核对的导航与精确 URL 拦截，不匹配的路由与域名原样放行。

### 添加一项规则

在 `LayoutSettings.Item` 增加枚举项，指定现有 `Category` 和开关文案，然后在对应规则的 hook 中读取 `LayoutSettings.enabled(Item.新增项)`。界面自动列出新增项并更新总数、开启数，无需改 `SettingsUi`。枚举名的小写形式是持久化键，不要重命名已有枚举项；新增类别时，在 `Category` 增加名称和说明即可。

## 回归测试

本地运行 `powershell -File tests/run.ps1`, 需准备 JDK 17、Gradle 9.5.1 和 Android SDK 37.
测试使用 JVM 接口替身调用生产 hook, 与真机验证分别记录.

## 构建与安装

- 编译环境：Microsoft JDK 17, Gradle 9.5.1, Android SDK 37 (compileSdk=37, minSdk=26)
- 产物路径：`XiaoCanPurify-2.1.1-release.apk`
- 安装步骤：
  1. 若已安装 2.0，可直接覆盖安装（若从 1.2 及更早版本升级，请先卸载旧版模块）。
  2. 安装新的 APK。
  3. 在 LSPosed Manager 中启用「小蚕净化」模块。
  4. 作用域勾选「小蚕霸王餐」(`com.realtech.xiaocan`)。
  5. 强制停止并重新启动小蚕霸王餐即可享受清爽体验。

### 1.4.3：屏蔽新店推荐任务网页

「订单 → 屏蔽新店推荐任务网页（含手动打开）」默认开启。仅小蚕 3.21.2（2985）中，经已核对的无回调 TheRouter `/web` 默认导航，且 `link` 严格命中 `https://gw.hzaiguojiang.com/static/act/singleActivity.html#/new-recommend`（允许参数）时，在创建网页容器前阻止导航。不匹配的 URL、路由、签名或宿主版本均原样执行；不改提交、失败提示、Flutter 结果页或后台响应。不覆盖页面内重定向、其他域名或未核对的路由入口。

回退：只需关闭该开关、保存并冷启动小蚕，即恢复手动打开；旧「活动详情」自动跳转规则和「订单」网页挽留开关独立，若需完全恢复宿主原行为可分别关闭。本规则不清除宿主已写入的当日营销检查标记。
