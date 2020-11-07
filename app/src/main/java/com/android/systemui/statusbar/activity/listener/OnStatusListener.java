package com.android.systemui.statusbar.activity.listener;

import com.android.systemui.statusbar.activity.StatusBean;

/**
 * @author Altair
 * @date :2020.01.09 上午 11:35
 * @description: StatusBar update icon state
 */
public interface OnStatusListener {
    void statusChange(StatusBean statusBean);
}