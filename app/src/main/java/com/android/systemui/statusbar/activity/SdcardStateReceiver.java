package com.android.systemui.statusbar.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * @author Altair
 * @date :2020.04.10 上午 11:38
 * @description:
 */
public class SdcardStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        //获取到当前广播的事件类型
        String action = intent.getAction();
        if ("android.intent.action.MEDIA_MOUNTED".equals(action)) {
            Log.d("SdcardStateReceiver", "onReceive: 说明sd卡挂载了 ....");
            NotifyMessageManager.getInstance().updateSDCardState(true);
        }
        if ("android.intent.action.MEDIA_UNMOUNTED".equals(action) ||
                "android.intent.action.MEDIA_REMOVED".equals(action)) {
            NotifyMessageManager.getInstance().updateSDCardState(false);
            Log.d("SdcardStateReceiver", "onReceive: 说明sd卡卸载了 ");
        }
        if ("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
            NetworkInfo info =
                    intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (info == null) {
                return;
            }
            Log.d("SdcardStateReceiver",
                    "onReceive:getState " + info.getState() + " isConnected " + info.isConnected()
                            + " type " + info.getType());
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {

                if (NetworkInfo.State.CONNECTED == info.getState() && info.isConnected()) {

                }
            }
        }
    }
}
