package com.android.systemui.statusbar.activity;

import android.content.Context;

import com.android.systemui.SystemUIApplication;

/**
 * @author Altair
 * @date :2020.01.10 下午 05:19
 * @description:
 */
public class TXZCommand {
    private SettingsFunctionTool mSettingsFunctionTool;
    private Context mContext;

    public TXZCommand() {
        this.mContext = SystemUIApplication.getInstance();
        mSettingsFunctionTool = new SettingsFunctionTool();
    }

    public int updateBrightness(int value) {
        int currentBrightness = mSettingsFunctionTool.getScreenBrightnessPercentageValue();
        currentBrightness += value;
        final int maxValue = 100;
        if (value>0&&value<maxValue){
            if (currentBrightness < 0) {
                currentBrightness = 0;
            }
            if (currentBrightness > maxValue) {
                currentBrightness = maxValue;
            }
        }else{

        }
        mSettingsFunctionTool.progressChangeToBrightness(currentBrightness);
        return currentBrightness;
    }

    public int updateVolume(int value) {
        final int maxValue = 15;
        int currentVolume = mSettingsFunctionTool.getCurrentVolume();

        currentVolume = currentVolume + value;
        if (currentVolume < 0) {
            currentVolume = 0;
        }
        if (currentVolume > maxValue) {
            currentVolume = maxValue;
        }
        mSettingsFunctionTool.setVolume(currentVolume);
        return currentVolume;
    }

}
