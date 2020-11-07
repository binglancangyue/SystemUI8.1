package com.android.systemui.statusbar.activity.listener;

import com.android.systemui.statusbar.activity.TXZOperation;

/**
 * @author Altair
 * @date :2019.11.05 下午 04:05
 * @description:
 */
public interface OnTXZBroadcastListener {
    void notifyActivity(int type, TXZOperation txzOperation);
}