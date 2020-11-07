package com.android.systemui.statusbar.activity;

/**
 * @author Altair
 * @date :2019.11.08 下午 02:49
 * @description: 同行者广播接收实体类
 */
public class TXZOperation {
    private int light;
    private int volume;
    private boolean enableWifi;
    private boolean screenOpen;
    private boolean enableBluetooth;
    private boolean enableFM;
    private boolean enableGPS;
    private boolean enable4G;

    public int getLight() {
        return light;
    }

    public void setLight(int light) {
        this.light = light;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public boolean isEnableWifi() {
        return enableWifi;
    }

    public void setEnableWifi(boolean enableWifi) {
        this.enableWifi = enableWifi;
    }

    public boolean isScreenOpen() {
        return screenOpen;
    }

    public void setScreenOpen(boolean screenOpen) {
        this.screenOpen = screenOpen;
    }

    public boolean isEnableBluetooth() {
        return enableBluetooth;
    }

    public void setEnableBluetooth(boolean enableBluetooth) {
        this.enableBluetooth = enableBluetooth;
    }

    public boolean isEnableFM() {
        return enableFM;
    }

    public void setEnableFM(boolean enableFM) {
        this.enableFM = enableFM;
    }

    public boolean isEnableGPS() {
        return enableGPS;
    }

    public void setEnableGPS(boolean enableGPS) {
        this.enableGPS = enableGPS;
    }

    public boolean isEnable4G() {
        return enable4G;
    }

    public void setEnable4G(boolean enable4G) {
        this.enable4G = enable4G;
    }
}
