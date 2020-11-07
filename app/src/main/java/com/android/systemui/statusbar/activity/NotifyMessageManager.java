package com.android.systemui.statusbar.activity;

import android.util.Log;

import com.android.systemui.statusbar.activity.listener.OnSettingsStatusListener;
import com.android.systemui.statusbar.activity.listener.OnShowSettingsWindowListener;
import com.android.systemui.statusbar.activity.listener.OnStatusListener;
import com.android.systemui.statusbar.activity.listener.OnTXZBroadcastListener;
import com.android.systemui.statusbar.activity.listener.OnUpdateWeatherListener;

/**
 * @author Altair
 * @date :2020.03.26 下午 04:28
 * @description: 回调管理类
 */
public class NotifyMessageManager {
    private OnSettingsStatusListener mOnSettingsStatusListener;
    private OnStatusListener mOnStatusListener;
    private OnShowSettingsWindowListener mSettingsWindowListener;
    private OnUpdateWeatherListener mOnUpdateWeather;

    public static NotifyMessageManager getInstance() {
        return SingletonHolder.sInstance;
    }

    private static class SingletonHolder {
        private static final NotifyMessageManager sInstance = new NotifyMessageManager();
    }

    public void setOnShowSettingsWindowListener(OnStatusListener listener) {
        this.mOnStatusListener = listener;
    }

    public void setOnUpdateWeatherListener(OnUpdateWeatherListener listener) {
        this.mOnUpdateWeather = listener;
    }

    public void setOnShowSettingsWindowListener(OnShowSettingsWindowListener listener) {
        this.mSettingsWindowListener = listener;
    }

    public void setListener(OnSettingsStatusListener onSettingsStatusListener) {
        setOnSettingsStatusListener(onSettingsStatusListener);
    }

    public void setOnSettingsStatusListener(OnSettingsStatusListener listener) {
        this.mOnSettingsStatusListener = listener;
    }


    public void openOrClose(int type, boolean state) {
        if (mOnSettingsStatusListener == null) {
            Log.e("NotifyMessageManager", "OnSettingsStatusListener = null ");
            return;
        }
        Log.d("NotifyMessageManager", "openOrClose: " + type + " " + state);
        mOnSettingsStatusListener.openOrClose(type, state);
    }

    public void updateStatusBarIconState(StatusBean statusBean) {
        if (mOnStatusListener != null) {
            mOnStatusListener.statusChange(statusBean);
        }
    }

    public void updateSDCardState(boolean in) {
        if (mOnStatusListener != null) {
            mOnStatusListener.statusChange(new StatusBean(CustomValue.TYPE_SDCARD, in));
        }
    }

    public void showSettingsWindow() {
        if (mSettingsWindowListener != null) {
            mSettingsWindowListener.ShowSettingsWindow();
        }
    }

    public void updateWeather(String weatherInfo) {
        if (mOnUpdateWeather != null) {
            mOnUpdateWeather.updateWeather(weatherInfo);
        }
    }

    public void releaseListener() {
        if (mOnSettingsStatusListener != null) {
            mOnSettingsStatusListener = null;
        }
        if (mOnStatusListener != null) {
            mOnSettingsStatusListener = null;
        }
        if (mSettingsWindowListener != null) {
            mSettingsWindowListener = null;
        }
    }

}
