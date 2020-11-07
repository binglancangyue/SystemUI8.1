package com.android.systemui.statusbar.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.android.systemui.SystemUIApplication;

/**
 * @author Altair
 * @date :2019.10.28 上午 10:03
 * @description: SharedPreferences 工具
 */
public class SharedPreferencesTool {
    private SharedPreferences mPreferences;
    private SharedPreferences mDVRPreferences;
    private SharedPreferences.Editor mEditor;
    private Context mContext;
    public static final String ITEM_RECORDING_TIME = "Auto-Recording-Time";
    public static final String DVR_ADAS = "Dvr_ADAS";
    public static final String DVR_COLLISION = "Dvr_collision";
    public static final String DVR_CAMERA_FACING = "Dvr_camera_facing_front";
    public static final int TIME_INTERVAL_1_MINUTE = 60;
    public static final int TIME_INTERVAL_3_MINUTE = 60 * 3;
    public static final int TIME_INTERVAL_5_MINUTE = 60 * 5;

    @SuppressLint("CommitPrefEdits")
    public SharedPreferencesTool() {
        this.mContext = SystemUIApplication.getInstance();
        this.mPreferences = mContext.getSharedPreferences(CustomValue.SP_NAME,
                Context.MODE_PRIVATE);
        this.mDVRPreferences = mContext.getSharedPreferences("UTOPS-DVR-Service-Preferences",
                Context.MODE_PRIVATE);
        this.mEditor = mPreferences.edit();
    }



    public SharedPreferences getSharePreferences() {
        return mPreferences;
    }

    public void saveString(String name, String value) {
        mEditor.putString(name, value);
        mEditor.apply();
    }

    public void saveInt(String name, int value) {
        mEditor.putInt(name, value);
        mEditor.apply();
    }

    public void saveCameraFront(boolean value) {
        SharedPreferences.Editor editor = mDVRPreferences.edit();
        editor.putBoolean(DVR_CAMERA_FACING, value);
        editor.apply();
        sendBroadcast("dvr_camera_front", value, CustomValue.ACTION_OPEN_DVR_CAMERA);
    }
    public void saveBoolean(String name, boolean value) {
        mEditor.putBoolean(name, value);
        mEditor.apply();
    }
    public void getBoolean(String name, boolean value) {
        mEditor.putBoolean(name, value);
        mEditor.apply();
    }

    public boolean isFirstStart() {
        return mPreferences.getBoolean("is_first_start", true);
    }

    public void saveFirstStart() {
        mEditor.putBoolean("is_first_start", false);
        mEditor.apply();
    }

    @SuppressLint("NewApi")
    public void saveAutoBrightness(boolean value) {
        mEditor.putBoolean("auto_brightness", value);
        mEditor.apply();
    }

    public boolean getAutoBrightness() {
        return mPreferences.getBoolean("auto_brightness", false);
    }

    public void saveRecordTime(int time) {
        SharedPreferences.Editor editor = mDVRPreferences.edit();
        int recordTime = TIME_INTERVAL_1_MINUTE;
        if (time == 1) {
            recordTime = TIME_INTERVAL_1_MINUTE;
        }
        if (time == 3) {
            recordTime = TIME_INTERVAL_3_MINUTE;
        }
        if (time == 5) {
            recordTime = TIME_INTERVAL_5_MINUTE;
        }
        editor.putInt(ITEM_RECORDING_TIME, recordTime);
        editor.apply();
        sendBroadcast("record_time", recordTime, CustomValue.ACTION_SET_DVR_RECORD_TIME);
    }

    public int getRecordTime() {
        int time = mDVRPreferences.getInt(ITEM_RECORDING_TIME, 60);
        int timeLevel = 1;
        if (time == TIME_INTERVAL_1_MINUTE) {
            timeLevel = 1;
        }
        if (time == TIME_INTERVAL_3_MINUTE) {
            timeLevel = 3;
        }
        if (time == TIME_INTERVAL_5_MINUTE) {
            timeLevel = 5;
        }
        return timeLevel;
    }

    public void saveADALevel(int adasLevel) {
        SharedPreferences.Editor editor = mDVRPreferences.edit();
        editor.putInt(DVR_ADAS, adasLevel);
        editor.apply();
        sendBroadcast("adasLevel", adasLevel, CustomValue.ACTION_SET_ADAS_LEVEL);
    }

    public int getADASLevel() {
        int level = mDVRPreferences.getInt(DVR_ADAS, 1);
        return level;
    }

    public void saveCollisionLevel(int level) {
        SharedPreferences.Editor editor = mDVRPreferences.edit();
        editor.putInt(DVR_COLLISION, level);
        editor.apply();
        sendBroadcast("g_sensor", level, CustomValue.ACTION_SET_G_SENSOR_LEVEL);
    }

    public int getCollisionLevel() {
        int level = mDVRPreferences.getInt(DVR_COLLISION, 1);
        return level;
    }

    private void sendBroadcast(String name, int value, String action) {
        Intent intent = new Intent(action);
        intent.putExtra(name, value);
        mContext.sendBroadcast(intent);
    }
    private void sendBroadcast(String name, Boolean value, String action) {
        Intent intent = new Intent(action);
        intent.putExtra(name, value);
        mContext.sendBroadcast(intent);
    }
}
