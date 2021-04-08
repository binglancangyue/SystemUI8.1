package com.android.systemui.statusbar.activity;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.SystemUIApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.os.storage.DiskInfo;
import android.os.storage.VolumeInfo;

/**
 * @author Altair
 * @date :2019.10.25 上午 09:43
 * @description: 设置弹窗功能工具类
 */
public class SettingsFunctionTool {
    private Context mContext;
    private LocationManager locationManager;
    private AudioManager audioManager;
    private final float baseValue = 2.55f;//0-255
    private BluetoothAdapter bluetoothAdapter;
    private static final String TAG = "SettingsFunctionTool";
    private TelephonyManager mTelephonyManager;
    private int lastCount = -1;
    private PowerManager mPowerManager;
    public static final String fm_power_path = "/sys/class/QN8027/QN8027/power_state";
    public static final String fm_tunetoch_path = "/sys/class/QN8027/QN8027/tunetoch";
    public static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;
    public static final String EXTRA_FORMAT_PRIVATE = "format_private";
    public static final String EXTRA_FORGET_UUID = "forget_uuid";
    public static final String FM_STATE = "bx_fm_state";
    public static final String FM_VALUE = "bx_fm_value";
    public static final String BX_HEADSET_PATH = "/sys/kernel/headset/state";

    public SettingsFunctionTool() {
        this.mContext = SystemUIApplication.getInstance();
        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        mFmManager = (FmManager) getSystemService(mContext, ContextDef.FM_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    //打开或者关闭gps
    public void openGPS(boolean open) {
        if (Build.VERSION.SDK_INT < 19) {
            Settings.Secure.setLocationProviderEnabled(mContext.getContentResolver(),
                    LocationManager.GPS_PROVIDER, open);
        } else {
            if (!open) {
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.LOCATION_MODE,
                        android.provider.Settings.Secure.LOCATION_MODE_OFF);
            } else {
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.LOCATION_MODE,
                        android.provider.Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
            }
        }
    }

    //判断gps是否处于打开状态
    public boolean isGpsOpen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } else {
            int state = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
            if (state == Settings.Secure.LOCATION_MODE_OFF) {
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * 获得屏百分比制幕亮度值
     *
     * @return 百分比值
     */
    public int getScreenBrightnessPercentageValue() {
//        double value = (int) (getScreenBrightness() / baseValue);
//        return (int) Math.floor(value);
        String s = NumberFormat.getPercentInstance().format(getCurrentBrightness());
        s = s.replace("%", "");
        return Integer.parseInt(s);
    }

    /**
     * 关闭光感，设置手动调节背光模式
     * SCREEN_BRIGHTNESS_MODE_AUTOMATIC 自动调节屏幕亮度模式值为1
     * SCREEN_BRIGHTNESS_MODE_MANUAL 手动调节屏幕亮度模式值为0
     **/
    private void setScreenManualMode() {
        ContentResolver contentResolver = mContext.getContentResolver();
        int mode;
        try {
            mode = Settings.System.getInt(contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE);
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.putInt(contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "setScreenManualMode Exception: " + e.toString());
        }
    }

    /**
     * 系统值 0-255
     * 修改Setting 中屏幕亮度值
     * 修改Setting的值需要动态申请权限<uses-permission
     * android:name="android.permission.WRITE_SETTINGS"/>
     **/

    public void modifyScreenBrightness(int brightnessValue) {
        // 首先需要设置为手动调节屏幕亮度模式
        setScreenManualMode();
        ContentResolver contentResolver = mContext.getContentResolver();
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue);
    }

    /**
     * 根据seeBar progress 转换后屏幕亮度
     *
     * @param progress seekBar progress
     */
    public void progressChangeToBrightness(int progress) {
//        int brightnessValue = (int) Math.ceil(progress * baseValue);
        int brightnessValue = (int) Math.round(progress * baseValue);

        Log.d(TAG, "progressChangeToBrightness: brightnessValue " + brightnessValue);
        try {
            modifyScreenBrightness(brightnessValue);
        } catch (Exception e) {
            Log.e(TAG, "progressChangeToBrightness Exception: " + e.getMessage());
        }
    }

    /**
     * 获取开启静音(音量设为0)的权限
     */
    private void getDoNotDisturb() {
        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && !notificationManager.isNotificationPolicyAccessGranted()) {

            Intent intent = new Intent(android.provider.Settings
                    .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            mContext.startActivity(intent);
        }
    }

    /**
     * 根据类型获取最大音量
     *
     * @param type 声音类型
     * @return 系统音量值
     */
    public int getMaxValue(int type) {
        return audioManager.getStreamMaxVolume(type);
    }

    /**
     * 获取系统当前音量
     *
     * @return 系统当前音量
     */
    public int getCurrentVolume() {
        return audioManager.getStreamVolume(STREAM_TYPE);
    }

    public void setVolume(int volume) {
        audioManager.setStreamVolume(STREAM_TYPE, volume,
                AudioManager.FLAG_PLAY_SOUND);
    }

    /**
     * 蓝牙是否开启
     *
     * @return true or false
     */
    public boolean isBlueToothEnable() {
        return bluetoothAdapter.isEnabled();
    }

    /**
     * 开启或关闭蓝牙
     *
     * @param isOpen true or false
     */
    public void openOrCloseBT(boolean isOpen) {
        if (isOpen) {
            if (!isBlueToothEnable()) {
                bluetoothAdapter.enable();
            }
//            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            mContext.startActivity(intent);
        } else {
            bluetoothAdapter.disable();
        }
    }

    public void openOrCloseFM(boolean isOpen) {
        if (isOpen) {
            Log.d(TAG, "openOrCloseFM: on");
            if (!getFmStatus()) {
                openFm();
            }
        } else {
            Log.d(TAG, "openOrCloseFM: off");
            closeFm();
        }
    }

    /**
     * 设置息屏或屏保时间
     * * 管理器方式
     *
     * @param time 时间值
     */
    public void setScreenOffTimeOut(int time) {
        Settings.System.putInt(mContext.getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT, time);
        Uri uri = Settings.System
                .getUriFor(Settings.System.SCREEN_OFF_TIMEOUT);
        mContext.getContentResolver().notifyChange(uri, null);
    }

    /**
     * 获取当前系统的息屏或屏保时间
     * 管理器方式
     *
     * @return 时间
     */
    public int getScreenOutTime() {
        try {
            int time = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT) / 1000;
            Log.d(TAG, "instance initializer: " + time);
            if (time <= 60) {
                return 1;
            } else if (time <= 300) {
                return 2;
            } else if (time <= 1800) {
                return 3;
            } else {
                return 4;
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "getScreenOutTime: error " + e.getMessage());
        }
        return 1;
    }

    public boolean isHasSimCard() {
        int simState = mTelephonyManager.getSimState();
        boolean result = true;
        String imsi = mTelephonyManager.getSubscriberId();
        Log.d(TAG, "isHasSimCard: imsi " + imsi);
        if (simState == TelephonyManager.SIM_STATE_ABSENT ||
                simState == TelephonyManager.SIM_STATE_UNKNOWN) {
            result = false;
        }
        Log.d(TAG, result ? "有SIM卡" : "无SIM卡");
        return result;
    }


    public void setDataEnabled(boolean enable) {
        try {
            SubscriptionInfo subscriptionInfo =
                    SubscriptionManager.from(mContext).getActiveSubscriptionInfoForSimSlotIndex(0);
            if (subscriptionInfo == null) {
                Log.d(TAG, "setDataEnabled:subscriptionInfo==null ");
                return;
            }
            int subid = subscriptionInfo.getSubscriptionId();
            Method setDataEnabled = mTelephonyManager.getClass().getDeclaredMethod("setDataEnabled"
                    , int.class, boolean.class);
            if (null != setDataEnabled) {
                setDataEnabled.invoke(mTelephonyManager, subid, enable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean getDataEnabled() {
        boolean enabled = false;
        try {
            SubscriptionInfo subscriptionInfo =
                    SubscriptionManager.from(mContext).getActiveSubscriptionInfoForSimSlotIndex(0);
            if (subscriptionInfo == null) {
                Log.d(TAG, "getDataEnabled:subscriptionInfo==null ");
                return false;
            }
            int subid = subscriptionInfo.getSubscriptionId();
            Log.d(TAG, "getDataEnabled: " + mTelephonyManager.getDataEnabled());
            Method getDataEnabled = mTelephonyManager.getClass().getDeclaredMethod("getDataEnabled"
                    , int.class);
            if (null != getDataEnabled) {
                enabled = (Boolean) getDataEnabled.invoke(mTelephonyManager, subid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return enabled;
    }

    private double getCurrentBrightness() {
        ContentResolver contentResolver = mContext.getContentResolver();
        final int mMinBrightness = mPowerManager.getMinimumScreenBrightnessSetting();
        final int mMaxBrightness = mPowerManager.getMaximumScreenBrightnessSetting();
        final double value = Settings.System.getInt(contentResolver,
                Settings.System.SCREEN_BRIGHTNESS, mMinBrightness);
        return getPercentage(value, mMinBrightness, mMaxBrightness);
    }

    private double getPercentage(double value, int min, int max) {
        if (value > max) {
            return 1.0;
        }
        if (value < min) {
            return 0.0;
        }
        return (value - min) / (max - min);
    }

    public boolean getFmStatus() {
        boolean isOpen = false;
        BufferedReader reader;
        String prop;
        try {
            reader = new BufferedReader(new FileReader(new File(fm_power_path)));
            prop = reader.readLine();
            if (prop.equals("1")) {
                isOpen = true;
            }
            Log.d(TAG, "getFmStatus:prop " + prop);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isOpen;
    }

    public boolean getInitFmStatus() {
        boolean isOpen = false;
        int state = Settings.Global.getInt(mContext.getContentResolver(), FM_STATE, 0);
        Log.d(TAG, "getInitFmStatus: " + state);
        if (state == 1) {
            setFmValue(Settings.Global.getInt(mContext.getContentResolver(), FM_VALUE, 9800));
            openFm();
            return true;
        } else {
            closeFm();
            return false;
        }
    }

    private void openFm() {
        try {
            Writer fm_power = new FileWriter(fm_power_path);
            fm_power.write("on");
            fm_power.close();
            setFmState("on");
            setSpeakerphoneOn(false);
            Settings.Global.putInt(mContext.getContentResolver(), FM_STATE, 1);
            Log.d(TAG, "openFm:on ");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "closeFm: " + e.getMessage());
        }
    }

    private void closeFm() {
        try {
            Writer fm_power = new FileWriter(fm_power_path);
            fm_power.write("off");
            fm_power.flush();
            fm_power.close();
            setFmState("off");
            setSpeakerphoneOn(false);
            Settings.Global.putInt(mContext.getContentResolver(), FM_STATE, 0);
            Log.d(TAG, "openFm:on off");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "closeFm: " + e.getMessage());
        }
    }

    public void setFmState(String value) {
        Writer fmState = null;
        try {
            fmState = new FileWriter(BX_HEADSET_PATH);
            fmState.write(value);
            fmState.flush();
            Log.d(TAG, "setFmState:value " + value);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "setFmState: " + e.getMessage());
        } finally {
            if (fmState != null) {
                try {
                    fmState.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setFmValue(int value) {
        Writer fmTuneTouch = null;
        try {
            fmTuneTouch = new FileWriter(fm_tunetoch_path);
            fmTuneTouch.write(value + "");
            fmTuneTouch.flush();
//            Settings.Global.putInt(mContext.getContentResolver(), FM_VALUE, value);
            Log.d(TAG, "setFmValue:value " + value);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "setFmValue: " + e.getMessage());
        } finally {
            if (fmTuneTouch != null) {
                try {
                    fmTuneTouch.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void startFormatting() {
        StorageManager mStorage = (StorageManager) SystemUIApplication.getInstance()
                .getSystemService(Context.STORAGE_SERVICE);
//        DiskInfo mDisk = mStorage.findDiskById("disk:179,64");
        DiskInfo mDisk = getDiskId(mStorage);
        if (mDisk == null) {
            ToastTool.showToast(R.string.no_sd_card_detected);
            return;
        }
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.deviceinfo.StorageWizardFormatProgress");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.setComponent(cn);
        intent.putExtra(DiskInfo.EXTRA_DISK_ID, mDisk.getId());
        intent.putExtra(EXTRA_FORMAT_PRIVATE, false);
        intent.putExtra(EXTRA_FORGET_UUID, "");
        mContext.startActivity(intent);

    }

    public int getWeatherIcon(String weather) {
        Log.d(TAG, "getWeatherIcon: weather " + weather);
        int iconId = 0;
        switch (weather) {
            case "多云":
                iconId = R.drawable.ic_weather_cloudy;
                break;
            case "阴":
            case "阴天":
                iconId = R.drawable.ic_weather_cloudy_day;
                break;
            case "阵雨":
                iconId = R.drawable.ic_weather_shower;
                break;
            case "雷阵雨伴有冰雹":
                iconId = R.drawable.ic_weather_thunder_shower;
                break;
            case "雷阵雨":
                iconId = R.drawable.ic_weather_thunder_storm_and_hail;
                break;
            case "小雨":
            case "雨":
                iconId = R.drawable.ic_weather_light_rain;
                break;
            case "中雨":
                iconId = R.drawable.ic_weather_moderate_rain;
                break;
            case "大雨":
                iconId = R.drawable.ic_weather_heavy_rain;
                break;
            case "暴雨":
                iconId = R.drawable.ic_weather_baoyu;
                break;
            case "大暴雨":
                iconId = R.drawable.ic_weather_dabaoyu;
                break;
            case "特暴大雨":
                iconId = R.drawable.ic_weather_torrential_rain;
                break;
            case "冰雨":
                iconId = R.drawable.ic_weather_ice_rain;
                break;
            case "小雪":
                iconId = R.drawable.ic_weather_light_snow;
                break;
            case "中雪":
                iconId = R.drawable.ic_weather_medium_snow;
                break;
            case "大雪":
                iconId = R.drawable.ic_weather_heavy_snow;
                break;
            case "暴雪":
                iconId = R.drawable.ic_weather_greate_heavy_snow;
                break;
            case "雨夹雪":
                iconId = R.drawable.ic_weather_sleet;
                break;
            case "阵雪":
                iconId = R.drawable.ic_weather_snow_shower;
                break;
            case "雾":
                iconId = R.drawable.ic_weather_fog;
                break;
            case "霾":
                iconId = R.drawable.ic_weather_haze;
                break;
            case "浮尘":
                iconId = R.drawable.ic_weather_float_dust;
                break;
            case "扬沙":
                iconId = R.drawable.ic_weather_raise_sand;
                break;
            case "沙尘暴":
                iconId = R.drawable.ic_weather_dust_storm;
                break;
            default:    // 晴天
                iconId = R.drawable.ic_weather_sunny_day;
                break;
        }
        return iconId;
    }

    /**
     * 检查wifi是否处开连接状态
     *
     * @return
     */
    public boolean isWifiConnect() {
        ConnectivityManager connManager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager == null) {
            return false;
        }
        NetworkInfo mWifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifiInfo.isConnected();
    }

    private DiskInfo getDiskId(StorageManager mStorage) {
//        String id = null;
        DiskInfo diskinfo = null;
        for (VolumeInfo volumeInfo : mStorage.getVolumes()) {
            if (VolumeInfo.TYPE_PUBLIC == volumeInfo.getType()) {
//                id = volumeInfo.getDiskId();
                diskinfo = volumeInfo.getDisk();
                Log.d(TAG, "VolumeInfo " + volumeInfo.getType() + " " + volumeInfo.getDiskId());
            }
        }
        return diskinfo;
    }

    public void setSpeakerphoneOn(boolean b) {
        if (audioManager != null) {
            if (!b) {
                audioManager.setParameters("fm=1");
                Log.d(TAG, "setSpeakerphoneOn: fm=1");
            } else {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setParameters("fm=0");
                Log.d(TAG, "setSpeakerphoneOn: fm=0");
            }
            audioManager.setSpeakerphoneOn(b);
            Log.d(TAG, "setSpeakerphoneOn: " + b);
        }
    }

}
