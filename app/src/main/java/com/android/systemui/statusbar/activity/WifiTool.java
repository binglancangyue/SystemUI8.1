package com.android.systemui.statusbar.activity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.android.systemui.SystemUIApplication;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class WifiTool {
    private Context mContext;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    public WifiTool() {
        mContext = SystemUIApplication.getInstance();
        mWifiManager =
                (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager =
                mContext.getApplicationContext().getSystemService(ConnectivityManager.class);

    }

    /**
     * wifi是否打开
     *
     * @return open or close
     */
    public boolean isWifiEnable() {
        boolean isEnable = false;
        if (mWifiManager != null) {
            Log.d("WifiTool", "isWifiEnable: " + mWifiManager.isWifiEnabled());
            if (mWifiManager.isWifiEnabled()) {
                isEnable = true;
            }
        }
        return isEnable;
    }

    public void setWifiStatue(int type) {
        if (type == 0) {
            closeWifi();
        } else {
            openWifi();
        }
    }

    /**
     * 打开WiFi
     */
    public void openWifi() {
        if (mWifiManager != null && !isWifiEnable()) {
            mWifiManager.setWifiEnabled(true);
        }
    }

    /**
     * 关闭WiFi
     */
    public void closeWifi() {
        if (mWifiManager != null && isWifiEnable()) {
            mWifiManager.disconnect();
            mWifiManager.setWifiEnabled(false);
        }
    }

    public int getWifiSignalLevel() {
        WifiInfo connectionInfo = mWifiManager.getConnectionInfo();
        int infoRssi = connectionInfo.getRssi();
        int level = WifiManager.calculateSignalLevel(infoRssi, 5);
        Log.d("WIFITool", "onReceive: level " + level);
        return level;
    }

    public void setWifiApState(boolean isOpen) {
        if (isOpen) {
            mConnectivityManager.startTethering(ConnectivityManager.TETHERING_WIFI, false,
                    new ConnectivityManager.OnStartTetheringCallback() {
                        @Override
                        public void onTetheringStarted() {
                            Log.d("wifi", "onTetheringStarted");
                            // Don't fire a callback here, instead wait for the next update from
                            // wifi.
                        }

                        @Override
                        public void onTetheringFailed() {
                            Log.d("wifi", "onTetheringFailed");
                            // TODO: Show error.
                        }
                    });
        } else {
            mConnectivityManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
        }
    }

    public boolean isWifiApOpen() {
        try {
            //通过放射获取 getWifiApState()方法
            Method method = mWifiManager.getClass().getDeclaredMethod("getWifiApState");
            //调用getWifiApState() ，获取返回值
            int state = (int) method.invoke(mWifiManager);
            //通过放射获取 WIFI_AP的开启状态属性
            Field field = mWifiManager.getClass().getDeclaredField("WIFI_AP_STATE_ENABLED");
            //获取属性值
            int value = (int) field.get(mWifiManager);
            //判断是否开启
            if (state == value) {
                return true;
            } else {
                return false;
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean wifiConnected() {
        NetworkInfo activeNetInfo = mConnectivityManager.getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }
}

