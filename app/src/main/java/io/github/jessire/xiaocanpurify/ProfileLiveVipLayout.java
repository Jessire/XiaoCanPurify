package io.github.jessire.xiaocanpurify;

import android.view.View;
import android.view.ViewGroup.LayoutParams;

import java.lang.reflect.Field;

/** Expand only the withdraw card's horizontal geometry, with an inverse for every edit. */
final class ProfileLiveVipLayout {
    private static final String[] NAMES = {"endToStart", "rightToLeft", "endToEnd", "rightToRight",
            "matchConstraintDefaultWidth", "matchConstraintPercentWidth", "width"};
    private static final Object[] HIDDEN = {-1, -1, 0, 0, 0, 1f, 0};
    private Field[] fields;
    private Object[] original;

    void apply(View card, boolean hide) {
        LayoutParams params = card.getLayoutParams();
        if (params == null || (!hide && original == null)) return;
        try {
            // Resolve/read all members before making any changes on an unfamiliar host.
            if (fields == null || !fields[0].getDeclaringClass().isInstance(params)) {
                Field[] resolved = new Field[NAMES.length];
                for (int i = 0; i < NAMES.length; i++) {
                    resolved[i] = params.getClass().getField(NAMES[i]);
                    if (resolved[i].getType() != (HIDDEN[i] instanceof Float ? float.class : int.class)) return;
                }
                fields = resolved;
            }
            Object[] current = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) current[i] = fields[i].get(params);
            boolean changed = false;
            if (hide) {
                if (original == null) original = current.clone();
                for (int i = 0; i < fields.length; i++) {
                    if (HIDDEN[i].equals(current[i])) continue;
                    original[i] = current[i];
                    fields[i].set(params, HIDDEN[i]);
                    changed = true;
                }
            } else {
                for (int i = 0; i < fields.length; i++) {
                    if (!HIDDEN[i].equals(current[i]) || original[i].equals(current[i])) continue;
                    fields[i].set(params, original[i]);
                    changed = true;
                }
                original = null;
            }
            if (changed) card.setLayoutParams(params);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
