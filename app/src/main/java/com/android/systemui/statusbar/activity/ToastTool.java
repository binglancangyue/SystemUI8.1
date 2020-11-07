package com.android.systemui.statusbar.activity;

import android.annotation.SuppressLint;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.systemui.SystemUIApplication;

public class ToastTool {
    private static Toast toast;

    @SuppressLint("ShowToast")
    public static void showToast(String text) {
        if (toast == null) {
            toast = Toast.makeText(SystemUIApplication.getInstance(), text, Toast.LENGTH_SHORT);
//            toast.setGravity(Gravity.BOTTOM,0,50);
            LinearLayout linearLayout = (LinearLayout) toast.getView();
            TextView messageTextView = (TextView) linearLayout.getChildAt(0);
            messageTextView.setTextSize(24);
        } else {
            toast.setText(text);
            toast.setDuration(Toast.LENGTH_SHORT);
        }
        toast.show();
    }

    @SuppressLint("ShowToast")
    public static void showToast(int text) {
        if (toast == null) {
            toast = Toast.makeText(SystemUIApplication.getInstance(), text, Toast.LENGTH_SHORT);
//            toast.setGravity(Gravity.BOTTOM,0,50);
            LinearLayout linearLayout = (LinearLayout) toast.getView();
            TextView messageTextView = (TextView) linearLayout.getChildAt(0);
            messageTextView.setTextSize(24);
        } else {
            toast.setText(text);
            toast.setDuration(Toast.LENGTH_SHORT);
        }
        toast.show();
    }
}
