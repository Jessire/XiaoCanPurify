package io.github.jessire.xiaocanpurify;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class HomeBrandCoupons {
    private HomeBrandCoupons() {}

    static boolean isHomeAdapter(Object adapter) {
        return "Home".equals(enumName(get(adapter, "getSource")));
    }

    static Object filter(Object item) {
        if (item == null) return null;
        String type = enumName(get(item, "getType"));
        if ("bigBrand".equals(type) || "bigBrandV3".equals(type)) return null;
        Object items = get(item, "getList");
        if (items instanceof List<?> && !((List<?>) items).isEmpty()) {
            List<?> activities = (List<?>) items;
            List<Object> remaining = new ArrayList<>(activities.size());
            for (Object activity : activities) {
                if (!isExclusive(activity)) remaining.add(activity);
            }
            if (remaining.size() == activities.size()) return item;
            return remaining.isEmpty() ? null : copyWithRegularActivities(item, remaining);
        }
        return isExclusive(get(item, "getPromotion")) ? null : item;
    }

    private static Object copyWithRegularActivities(Object item, List<Object> remaining) {
        // The verified host versions place the activity list in the fifth copy parameter.
        for (Method copy : item.getClass().getDeclaredMethods()) {
            if (!"copy$default".equals(copy.getName()) || !Modifier.isStatic(copy.getModifiers())) continue;
            Class<?>[] types = copy.getParameterTypes();
            int mask = types.length - 2;
            if ((types.length != 25 && types.length != 26) || types[0] != item.getClass()
                    || types[5] != List.class || types[mask] != int.class
                    || types[mask + 1] != Object.class || copy.getReturnType() != types[0]) continue;
            Object[] args = new Object[types.length];
            args[0] = item;
            args[5] = remaining;
            for (int i = 1; i < mask; i++) if (types[i] == boolean.class) args[i] = false;
            args[mask] = ((1 << (mask - 1)) - 1) & ~(1 << 4);
            try { return copy.invoke(null, args); }
            catch (ReflectiveOperationException | RuntimeException ignored) { return item; }
        }
        return item;
    }

    private static boolean isExclusive(Object promotion) {
        return Boolean.TRUE.equals(get(promotion, "isBigBrand"))
                || Boolean.TRUE.equals(get(promotion, "isBigBrandNew"));
    }

    private static Object get(Object value, String method) {
        if (value == null) return null;
        try { return value.getClass().getMethod(method).invoke(value); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static String enumName(Object value) {
        return value instanceof Enum<?> ? ((Enum<?>) value).name() : "";
    }
}
