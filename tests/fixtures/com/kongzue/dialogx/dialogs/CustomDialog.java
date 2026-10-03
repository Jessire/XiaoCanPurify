package com.kongzue.dialogx.dialogs;
import android.app.Activity;
public final class CustomDialog {
    public int dismissCalls;
    public CustomDialog show() { return this; }
    public CustomDialog show(Activity activity) { return this; }
    public void dismiss() { dismissCalls++; }
}
