package io.github.jessire.xiaocanpurify;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

public final class NetworkAdInterceptor {
    private static final Map<Object, Boolean> INJECTED_BUILDERS = new WeakHashMap<>();

    private NetworkAdInterceptor() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookOkHttp(xposed, classLoader);
        hookNativeHttpBridge(xposed, classLoader);
    }

    private static void hookOkHttp(XposedInterface xposed, ClassLoader classLoader) {
        try {
            Class<?> builderClass = Class.forName("okhttp3.OkHttpClient$Builder", false, classLoader);
            Class<?> interceptorClass = Class.forName("okhttp3.Interceptor", false, classLoader);

            Method buildMethod = builderClass.getDeclaredMethod("build");
            Method addInterceptorMethod = builderClass.getMethod("addInterceptor", interceptorClass);

            xposed.hook(buildMethod).intercept(chain -> {
                Object builder = chain.getThisObject();
                if (builder != null && !isBuilderInjected(builder)) {
                    try {
                        Object interceptorProxy = Proxy.newProxyInstance(
                                classLoader,
                                new Class<?>[]{interceptorClass},
                                new AdInterceptorInvocationHandler(classLoader)
                        );
                        addInterceptorMethod.invoke(builder, interceptorProxy);
                        MainHook.log("Injected OkHttp ad filter interceptor");
                    } catch (Throwable t) {
                        MainHook.log("Failed to inject OkHttp interceptor: " + t);
                    }
                }
                return chain.proceed();
            });
            MainHook.log("Hooked OkHttpClient$Builder.build");
        } catch (Throwable t) {
            MainHook.log("Failed to hook OkHttp builder: " + t);
        }
    }

    private static void hookNativeHttpBridge(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> bridgeClass = Class.forName("com.realtech.xiaocan.flutter.NativeHttpBridge", false, cl);
            for (Method m : bridgeClass.getDeclaredMethods()) {
                if ("createBridge$lambda$2".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object call = chain.getArg(0);
                        Object result = chain.getArg(1);
                        if (call != null && result != null) {
                            try {
                                Method argumentMethod = call.getClass().getMethod("argument", String.class);
                                String service = (String) argumentMethod.invoke(call, "service");
                                String serverName = (String) argumentMethod.invoke(call, "servername");
                                String path = (String) argumentMethod.invoke(call, "path");
                                if (path == null) {
                                    path = (String) argumentMethod.invoke(call, "url");
                                }
                                String s = ((service == null ? "" : service) + " "
                                        + (serverName == null ? "" : serverName) + " "
                                        + (path == null ? "" : path)).toLowerCase(Locale.ROOT);
                                if (isBlockedFlutterService(s)) {
                                    MainHook.log(isDiscoveryName(s)
                                            ? "Blocked search discovery"
                                            : "Blocked withdraw card request");
                                    Method success = result.getClass().getMethod("success", Object.class);
                                    success.invoke(result, isDiscoveryName(s) ? EMPTY_DISCOVERY : null);
                                    if (isWithdrawCardName(s)) FlutterPageGuard.onWithdrawResponse();
                                    return null;
                                }
                            } catch (Throwable ignored) {}
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked NativeHttpBridge.createBridge$lambda$2");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook NativeHttpBridge: " + t);
        }
    }

    private static final String EMPTY_DISCOVERY =
            "{\"code\":0,\"status\":{\"code\":0},\"msg\":\"ok\",\"data\":{\"list\":[],\"words\":[],\"keywords\":[],\"items\":[],\"search_words\":[],\"recommendations\":[]},\"list\":[],\"words\":[],\"keywords\":[]}";

    private static boolean isBlockedFlutterService(String s) {
        return isWithdrawCardName(s) || isDiscoveryName(s);
    }

    private static boolean isWithdrawCardName(String s) {
        return s != null && (s.contains("checkuseringroup")
                || s.contains("groupdetail")
                || s.contains("wechat_group")
                || s.contains("bawangcan_banner")
                || s.contains("candoutx_banner")
                || s.contains("user_withdraw_banner")
                || s.contains("ismemberbysilkid")
                || s.contains("listexchangeproduct"));
    }

    private static boolean isDiscoveryName(String s) {
        // Search-page recommendations only. History stays on the local repository.
        return s != null && (s.contains("listsearchword")
                || s.contains("searchinviteword")
                || s.contains("listsilkrecommendation")
                || s.contains("keywordcategory")
                || s.contains("searchdiscovery"));
    }

    private static synchronized boolean isBuilderInjected(Object builder) {
        if (INJECTED_BUILDERS.containsKey(builder)) {
            return true;
        }
        INJECTED_BUILDERS.put(builder, Boolean.TRUE);
        return false;
    }

    private static final class AdInterceptorInvocationHandler implements InvocationHandler {
        private final ClassLoader classLoader;

        AdInterceptorInvocationHandler(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("intercept".equals(method.getName()) && args != null && args.length == 1) {
                Object chain = args[0];
                Method requestMethod = chain.getClass().getMethod("request");
                Object request = requestMethod.invoke(chain);

                Method urlMethod = request.getClass().getMethod("url");
                Object httpUrl = urlMethod.invoke(request);
                String urlStr = httpUrl != null ? httpUrl.toString().toLowerCase(Locale.ROOT) : "";

                // Check methodname header for blocked RPC calls
                try {
                    Method headerMethod = request.getClass().getMethod("header", String.class);
                    String methodName = (String) headerMethod.invoke(request, "methodname");
                    if (methodName != null) {
                        String mLower = methodName.toLowerCase(Locale.ROOT);
                        if (isWithdrawCardName(mLower)) {
                            MainHook.log("Blocked withdraw card RPC");
                            FlutterPageGuard.onWithdrawResponse();
                            return createMockJsonResponse(request, "{\"code\":0,\"data\":{},\"msg\":\"ok\"}");
                        }
                        if (isDiscoveryName(mLower)) {
                            MainHook.log("Blocked search discovery RPC");
                            return createMockJsonResponse(request, EMPTY_DISCOVERY);
                        }
                    }
                } catch (Throwable ignored) {}

                if (isDiscoveryName(urlStr)) {
                    MainHook.log("Blocked search discovery URL");
                    try {
                        return createMockJsonResponse(request, EMPTY_DISCOVERY);
                    } catch (Throwable t) {
                        MainHook.log("Mock response error: " + t);
                    }
                }

                if (isAdOrTrackingUrl(urlStr)) {
                    MainHook.log("Blocked ad network request: " + urlStr);
                    try {
                        return createMockJsonResponse(request, "{\"code\":0,\"data\":{},\"msg\":\"ok\"}");
                    } catch (Throwable t) {
                        MainHook.log("Mock response error: " + t);
                    }
                }

                Method proceedMethod = chain.getClass().getMethod("proceed", request.getClass());
                return proceedMethod.invoke(chain, request);
            }
            if ("equals".equals(method.getName())) {
                return proxy == (args != null && args.length > 0 ? args[0] : null);
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(method.getName())) {
                return "XiaoCanPurifyAdInterceptor";
            }
            return null;
        }

        private static boolean isAdOrTrackingUrl(String url) {
            return url.contains("adprolink")
                    || url.contains("sigmob")
                    || url.contains("1rtb.net")
                    || url.contains("midongtech")
                    || url.contains("etoolads")
                    || url.contains("ad66.top")
                    || url.contains("openadsdk")
                    || url.contains("pangolin")
                    || url.contains("kwad")
                    || url.contains("mobads.baidu.com")
                    || url.contains("buryingapi.xiaocantech.com")
                    || url.contains("sensorsdata.xiaocanapp.com");
        }

        private Object createMockJsonResponse(Object request, String json) throws Exception {
            Class<?> responseClass = Class.forName("okhttp3.Response", false, classLoader);
            Class<?> responseBuilderClass = Class.forName("okhttp3.Response$Builder", false, classLoader);
            Class<?> responseBodyClass = Class.forName("okhttp3.ResponseBody", false, classLoader);
            Class<?> mediaTypeClass = Class.forName("okhttp3.MediaType", false, classLoader);
            Class<?> protocolClass = Class.forName("okhttp3.Protocol", false, classLoader);

            Method parseMediaType = mediaTypeClass.getMethod("parse", String.class);
            Object jsonType = parseMediaType.invoke(null, "application/json; charset=utf-8");

            Method createBody = responseBodyClass.getMethod("create", mediaTypeClass, String.class);
            Object responseBody = createBody.invoke(null, jsonType, json);

            Object http11 = Enum.valueOf((Class<Enum>) protocolClass, "HTTP_1_1");

            Object builder = responseBuilderClass.getConstructor().newInstance();
            responseBuilderClass.getMethod("request", request.getClass()).invoke(builder, request);
            responseBuilderClass.getMethod("protocol", protocolClass).invoke(builder, http11);
            responseBuilderClass.getMethod("code", int.class).invoke(builder, 200);
            responseBuilderClass.getMethod("message", String.class).invoke(builder, "OK");
            responseBuilderClass.getMethod("body", responseBodyClass).invoke(builder, responseBody);

            return responseBuilderClass.getMethod("build").invoke(builder);
        }
    }
}
