package com.android.systemui.statusbar.activity;

/**
 * @author Altair
 * @date :2020.04.09 下午 05:32
 * @description:
 */
public class StatusBean {
    private int type;
    private boolean open;
    private int batteryValue;
    private int batteryState;
    private int value;

    public StatusBean(int type, boolean open) {
        this.type = type;
        this.open = open;
    }

    public StatusBean(int type, int batteryValue) {
        this.type = type;
        this.batteryState = batteryValue;
    }

    public StatusBean(int type, int batteryValue, int batteryState) {
        this.type = type;
        this.batteryValue = batteryValue;
        this.batteryState = batteryState;
    }

    public int getBatteryState() {
        return batteryState;
    }

    public void setBatteryState(int batteryState) {
        this.batteryState = batteryState;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public int getBatteryValue() {
        return batteryValue;
    }

    public void setBatteryValue(int batteryValue) {
        this.batteryValue = batteryValue;
    }
}
