package com.android.systemui.statusbar.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;

import java.lang.reflect.Field;

/**
 * @author Altair
 * @date :2020.05.29 下午 04:47
 * @description:
 */
public class DialogTool {
    private AlertDialog stop4GdDialog;
    private AlertDialog stopGPSdDialog;

    public void showStop4GDialog(Context context) {
        if (stop4GdDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View view = View.inflate(context, R.layout.dialog_layout, null);
          /*  LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
            layoutParams.width = 280;*/
            builder.setView(view);
            TextView title = view.findViewById(R.id.tv_dialog_title);
            TextView message = view.findViewById(R.id.tv_dialog_message);
            ImageView negativeButton = view.findViewById(R.id.tv_dialog_cancel);
            ImageView positiveButton = view.findViewById(R.id.tv_dialog_ok);
            title.setText("關閉4G");
            message.setText("關閉後將無網路連線。");
            negativeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissStopRecordDialog();
                }
            });
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissStopRecordDialog();
                    NotifyMessageManager.getInstance().openOrClose(21, true);
                }
            });
            stop4GdDialog = builder.create();
        }
        showAlertDialog(stop4GdDialog);
    }


    public void showStopGPSDialog(Context context) {
        if (stopGPSdDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View view = View.inflate(context, R.layout.dialog_layout, null);
          /*  LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
            layoutParams.width = 280;*/
            builder.setView(view);
            TextView title = view.findViewById(R.id.tv_dialog_title);
            TextView message = view.findViewById(R.id.tv_dialog_message);
            ImageView negativeButton = view.findViewById(R.id.tv_dialog_cancel);
            ImageView positiveButton = view.findViewById(R.id.tv_dialog_ok);
            title.setText("關閉GPS");
            message.setText("關閉後將無法定位。");
            negativeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissFormatDialog();
                }
            });
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissFormatDialog();
                    NotifyMessageManager.getInstance().openOrClose(20, true);
                }
            });
            stopGPSdDialog = builder.create();
        }
        showAlertDialog(stopGPSdDialog);
    }

    private void showDialog(AlertDialog alertDialog) {
        if (!alertDialog.isShowing()) {
            focusNotAle(alertDialog.getWindow());
            alertDialog.show();
            setDialogTextSize(alertDialog);
            hideNavigationBar(alertDialog.getWindow());
            clearFocusNotAle(alertDialog.getWindow());

        }
    }

    private void showAlertDialog(AlertDialog alertDialog) {
        focusNotAle(alertDialog.getWindow());
        alertDialog.show();
        WindowManager.LayoutParams params =
                alertDialog.getWindow().getAttributes();
        params.width = 400;
        alertDialog.getWindow().setAttributes(params);
        hideNavigationBar(alertDialog.getWindow());
        clearFocusNotAle(alertDialog.getWindow());
    }


    public void dismissStopRecordDialog() {
        if (stop4GdDialog != null) {
            stop4GdDialog.dismiss();
        }
    }

    public void dismissFormatDialog() {
        if (stopGPSdDialog != null) {
            stopGPSdDialog.dismiss();
        }
    }

    public void dismissDialog() {
        dismissStopRecordDialog();
        dismissFormatDialog();
        stop4GdDialog = null;
        stopGPSdDialog = null;
    }

    /**
     * dialog 需要全屏的时候用，和clearFocusNotAle() 成对出现
     * 在show 前调用  focusNotAle   show后调用clearFocusNotAle
     *
     * @param window
     */
    public void focusNotAle(Window window) {
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    /**
     * dialog 需要全屏的时候用，focusNotAle() 成对出现
     * 在show 前调用  focusNotAle   show后调用clearFocusNotAle
     *
     * @param window
     */
    public void clearFocusNotAle(Window window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    public void hideNavigationBar(Window window) {
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        window.getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        //布局位于状态栏下方
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        //全屏
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        //隐藏导航栏
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                if (Build.VERSION.SDK_INT >= 19) {
                    uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                } else {
                    uiOptions |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
                }
                window.getDecorView().setSystemUiVisibility(uiOptions);
            }
        });
    }

    public void hideNavigationBar2(Window window){
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        View decorView = window.getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(option);
    }

    public void setDialogTextSize(AlertDialog builder) {
        Button button_negative = builder.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button button_positive = builder.getButton(AlertDialog.BUTTON_POSITIVE);
        button_negative.setTextSize(27);
        button_positive.setTextSize(27);
        builder.getWindow().setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) button_positive.getLayoutParams();
        LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) button_positive.getLayoutParams();
        layoutParams.height = 80;
        layoutParams.width = 90;
        layoutParams.setMargins(0, 0, 5, 0);
        layoutParams.gravity = Gravity.CENTER;
        layoutParams1.gravity = Gravity.CENTER;
        layoutParams1.height = 80;
        layoutParams1.width = 90;
        button_negative.setLayoutParams(layoutParams);
        button_positive.setLayoutParams(layoutParams1);
        try {
            //获取mAlert对象
            Field mAlert = AlertDialog.class.getDeclaredField("mAlert");
            mAlert.setAccessible(true);
            Object mAlertController = mAlert.get(builder);

            //获取mTitleView并设置大小颜色
            Field mTitle = mAlertController.getClass().getDeclaredField("mTitleView");
            mTitle.setAccessible(true);
            TextView mTitleView = (TextView) mTitle.get(mAlertController);
            if (mTitleView != null) {
                mTitleView.setTextSize(30);
            }

            //获取mMessageView并设置大小颜色
            Field mMessage = mAlertController.getClass().getDeclaredField("mMessageView");
            mMessage.setAccessible(true);
            TextView mMessageView = (TextView) mMessage.get(mAlertController);
            if (mMessageView != null) {
                mMessageView.setTextSize(27);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

}
