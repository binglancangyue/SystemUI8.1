package com.android.systemui.statusbar.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.SystemUIApplication;
import com.android.systemui.statusbar.activity.listener.OnSettingPopupWindowListener;
import com.android.systemui.statusbar.activity.listener.OnSettingsStatusListener;

import java.lang.ref.WeakReference;


/**
 * @author Altair
 * @date :2020.01.07 下午 02:16
 * @description:
 */
public class SettingsWindowActivity extends Activity implements View.OnClickListener,
        SeekBar.OnSeekBarChangeListener, OnSettingsStatusListener {
    private static final String TAG = "SettingsWindowActivity";
    private OnSettingPopupWindowListener mListener;
    private Context mContext;
    private LinearLayout llBtnWireless;
    private LinearLayout llBtnScreenControl;
    private LinearLayout llBtnOther;
    private LinearLayout llBtnDVR;
    private LinearLayout llWireless;
    private LinearLayout llScreenControl;
    private LinearLayout llOther;
    private LinearLayout llDVR;

    private RadioButton rbCloseScreen;
    private RadioButton rbBright;
    private RadioButton rbScreenSaver;
    private RadioButton rbTime1;
    private RadioButton rbTime5;
    private RadioButton rbTime30;
    private RadioButton rbDay;
    private RadioButton rbNight;
    private RadioButton rbAuto;

    private RadioButton rbRecordTime1;
    private RadioButton rbRecordTime3;
    private RadioButton rbRecordTime5;
    private RadioButton rbCollisionLow;
    private RadioButton rbCollisionMiddle;
    private RadioButton rbCollisionHigh;
    private RadioButton rbCollisionClose;
    private RadioButton rbADASLow;
    private RadioButton rbADASMiddle;
    private RadioButton rbADASHigh;

    private LinearLayout llBtnWifi;
    private TextView tvWifi;
    private TextView tvWifiStatus;
    private LinearLayout llBtnHotspot;
    private ImageView ivHotspot;
    private TextView tvHotspotStatus;
    private LinearLayout llBtnGps;
    private ImageView ivGps;
    private TextView tvGpsStatus;
    private LinearLayout llBtnMobileData;
    private ImageView ivMobileData;
    private TextView tvMobileDataStatus;
    private TextView tv4GStatus;
    private LinearLayout llBtnBlueTooth;
    private ImageView ivBlueTooth;
    private TextView tvBlueTooth;

    private SeekBar seekBarBrightness;
    private TextView tvBrightnessValue;
    private ImageView ivFmSwitch;
    private SeekBar seekBarVolume;
    private TextView tvVolumeValue;
    private InnerHandler mHandler;
    private SettingsFunctionTool mSettingsUtils;
    private WifiTool wifiUtils;

    private LinearLayout llWirelessDataRow;
    private LinearLayout llMobileDataRow;
    private RelativeLayout rlFmRow;

    private SharedPreferencesTool mSharedPreferencesTool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = this;
        setContentView(R.layout.popup_window_setting);
        mHandler = new InnerHandler(this);
        mSettingsUtils = new SettingsFunctionTool();
        wifiUtils = new WifiTool();
//        requestPermissionsTool = new RequestPermissionsTool();
        mSharedPreferencesTool = new SharedPreferencesTool();
        setWindowSize();
        NotifyMessageManager.getInstance().setListener(this);
        createPopWindow();
    }

    @SuppressLint("NewApi")
    public void hideNavigationBar() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        //布局位于状态栏下方
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        //全屏
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        //隐藏导航栏
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                if (Build.VERSION.SDK_INT >= 19) {
                    uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                } else {
                    uiOptions |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
                }
                getWindow().getDecorView().setSystemUiVisibility(uiOptions);
            }
        });
    }

    private void setWindowSize() {
        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        //获取手机屏幕的高度
        DisplayMetrics metric = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metric);
        int widthPixels = (int) (metric.widthPixels * 0.8f);
        int heightPixels = (int) (metric.heightPixels * 0.72f);
        if (CustomValue.SCREEN_3) {
            widthPixels = WindowManager.LayoutParams.MATCH_PARENT;
            heightPixels = (int) (metric.heightPixels * 0.79f);
        }
        if (CustomValue.SCREEN_3_BX) {
            widthPixels = WindowManager.LayoutParams.MATCH_PARENT;
            heightPixels = (int) (metric.heightPixels * 0.62f);
        }
        if (CustomValue.SCREEN_439IN) {
            widthPixels = (int) (metric.widthPixels * 0.95f);
            heightPixels = (int) (metric.heightPixels * 0.8f);
        }
        if (CustomValue.IS_966) {
            Log.d(TAG, "setWindowSize: metric.widthPixels " + metric.widthPixels);
            widthPixels = WindowManager.LayoutParams.MATCH_PARENT;
            heightPixels = (int) (metric.heightPixels * 0.85f);
        }
        lp.width = widthPixels;
        lp.height = heightPixels;
        lp.gravity= Gravity.CENTER_HORIZONTAL;
        window.setAttributes(lp);
    }

    @SuppressLint("InflateParams")
    public void createPopWindow() {
        initPopupWindow();
        setData();
        setPopupWindowListener();
    }

    private void setData() {
        if (CustomValue.SCREEN_3) {
            llWirelessDataRow.setVisibility(View.GONE);
            rlFmRow.setVisibility(View.GONE);
            LinearLayout llADASRow = findViewById(R.id.ll_adas_row);
            LinearLayout llGSensorRow = findViewById(R.id.ll_g_sensor_row);
            llADASRow.setVisibility(View.GONE);
            llGSensorRow.setVisibility(View.GONE);
        }
        if (CustomValue.NOT_MOBILE_NETWORK) {
            llMobileDataRow.setVisibility(View.GONE);
            llBtnHotspot.setVisibility(View.INVISIBLE);
        }
        if (CustomValue.NOT_MOBILE_WIFI) {
            llWirelessDataRow.setVisibility(View.GONE);
        }
        if (CustomValue.NOT_DVR) {
            llBtnDVR.setVisibility(View.GONE);
        }
        //Brightness
        updateBrightness();
        //Volume
        updateVolume();
        //WIFI
        updateWifiBtnStatus(wifiUtils.isWifiEnable());

        //BT
        updateBtnByListener(4, mSettingsUtils.isBlueToothEnable());
        Log.d(TAG, "setData:isBlueToothEnable " + mSettingsUtils.isBlueToothEnable());

        initScreenOutTime();
        initRecordTime();
        initADASLevel();
        initCollisionLevel();
        //FM
        mSettingsUtils.initFM();
//        updateBtnFM(mSettingsUtils.getFmStatus());
        updateBtnFM(mSettingsUtils.getFMState());
        Log.d(TAG, "setData: " + mSettingsUtils.isGpsOpen());

        //WIFI AP
        updateBtnHotspot(wifiUtils.isWifiApOpen());

        //Mobile Network
        if (mSettingsUtils.isHasSimCard()) {
            update4GBtn(mSettingsUtils.getDataEnabled());
            if (wifiUtils.isWifiEnable()) {
                update4GState(false);
            } else {
                update4GState(mSettingsUtils.getDataEnabled());
            }
        }
        //GPS
        boolean isGPSOpen = mSettingsUtils.isGpsOpen();
//        if (isGPSOpen) {
//            mSettingsUtils.openGPS(true);
//        }
        updateBtnGPS(isGPSOpen);
        //AutoBrightness
        Log.d(TAG, "setData: AutoBrightness " + mSharedPreferencesTool.getAutoBrightness());
        setAutoBrightnessCheck(mSharedPreferencesTool.getAutoBrightness());
    }

    public void setOnSettingPopupWindowListener(OnSettingPopupWindowListener listener) {
        this.mListener = listener;
    }

    public void showPopupWindow() {
        mHandler.sendEmptyMessage(CustomValue.HANDLE_POP_UPDATE_DATA);
    }

    private void initPopupWindow() {
        llBtnWireless = findViewById(R.id.ll_left_wifi);
        llBtnScreenControl = findViewById(R.id.ll_left_screen_control);
        llBtnOther = findViewById(R.id.ll_left_other);
        llBtnDVR = findViewById(R.id.ll_left_dvr);

        llWireless = findViewById(R.id.ll_wireless_data);
        llScreenControl = findViewById(R.id.ll_brightness);
        llOther = findViewById(R.id.ll_other);
        llDVR = findViewById(R.id.ll_dvr);

        // Wireless data
        llWirelessDataRow = findViewById(R.id.ll_wireless_data_row);
        llMobileDataRow = findViewById(R.id.ll_mobile_data_row);
        llBtnWifi = findViewById(R.id.ll_btn_wifi);
        tvWifi = findViewById(R.id.tv_wifi);
        tvWifiStatus = findViewById(R.id.tv_wifi_status);
        llBtnHotspot = findViewById(R.id.ll_btn_hotspot);
        ivHotspot = findViewById(R.id.iv_hotspot);
        tvHotspotStatus = findViewById(R.id.tv_hotspot_status);
        llBtnGps = findViewById(R.id.ll_btn_gps);
        ivGps = findViewById(R.id.iv_gps);
        tvGpsStatus = findViewById(R.id.tv_gps_status);
        llBtnMobileData = findViewById(R.id.ll_btn_mobile_data);
        ivMobileData = findViewById(R.id.iv_mobile_data);
        tvMobileDataStatus = findViewById(R.id.tv_mobile_data_status);
        tv4GStatus = findViewById(R.id.tv_4g_status);
        llBtnBlueTooth = findViewById(R.id.ll_btn_bluetooth);
        ivBlueTooth = findViewById(R.id.iv_bluetooth);
        tvBlueTooth = findViewById(R.id.tv_bluetooth_status);

        llBtnWifi.setOnClickListener(this);
        llBtnHotspot.setOnClickListener(this);
        llBtnGps.setOnClickListener(this);
        llBtnMobileData.setOnClickListener(this);
        llBtnBlueTooth.setOnClickListener(this);


        //Screen control
        rbCloseScreen = findViewById(R.id.rb_close_screen);
        rbBright = findViewById(R.id.rb_bright);
        rbScreenSaver = findViewById(R.id.rb_screen_saver);

        rbTime1 = findViewById(R.id.rb_time_1);
        rbTime5 = findViewById(R.id.rb_time_5);
        rbTime30 = findViewById(R.id.rb_time_30);

        rbDay = findViewById(R.id.rb_screen_control_day);
        rbNight = findViewById(R.id.rb_screen_control_night);
        rbAuto = findViewById(R.id.rb_screen_control_auto);

        rbCloseScreen.setOnClickListener(this);
        rbBright.setOnClickListener(this);
        rbScreenSaver.setOnClickListener(this);
        rbTime1.setOnClickListener(this);
        rbTime5.setOnClickListener(this);
        rbTime30.setOnClickListener(this);
        rbDay.setOnClickListener(this);
        rbNight.setOnClickListener(this);
        rbAuto.setOnClickListener(this);

        seekBarBrightness = findViewById(R.id.sb_brightness);
        tvBrightnessValue = findViewById(R.id.tv_brightness_value);

        // Other
        rlFmRow = findViewById(R.id.rl_fm_row);
        ivFmSwitch = findViewById(R.id.iv_switch_fm);
        seekBarVolume = findViewById(R.id.sb_volume);
        seekBarVolume.setMax(mSettingsUtils.getMaxValue(SettingsFunctionTool.STREAM_TYPE));
        tvVolumeValue = findViewById(R.id.tv_volume_value);

        // DVR
        rbRecordTime1 = findViewById(R.id.rb_record_time_1);
        rbRecordTime3 = findViewById(R.id.rb_record_time_3);
        rbRecordTime5 = findViewById(R.id.rb_record_time_5);
        rbCollisionLow = findViewById(R.id.rb_collision_value_low);
        rbCollisionMiddle = findViewById(R.id.rb_collision_value_middle);
        rbCollisionHigh = findViewById(R.id.rb_collision_value_high);
        rbCollisionClose = findViewById(R.id.rb_collision_value_close);
        rbADASLow = findViewById(R.id.rb_adas_value_low);
        rbADASMiddle = findViewById(R.id.rb_adas_value_middle);
        rbADASHigh = findViewById(R.id.rb_adas_value_high);

        RelativeLayout btnDvrFormat = findViewById(R.id.btn_dvr_format);
        RelativeLayout btnDvrBT = findViewById(R.id.btn_dvr_bt);
        RelativeLayout btnDvrSystemSettings = findViewById(R.id.btn_dvr_settings);

        rbRecordTime1.setOnClickListener(this);
        rbRecordTime3.setOnClickListener(this);
        rbRecordTime5.setOnClickListener(this);
        rbCollisionLow.setOnClickListener(this);
        rbCollisionMiddle.setOnClickListener(this);
        rbCollisionHigh.setOnClickListener(this);
        rbCollisionClose.setOnClickListener(this);
        rbADASLow.setOnClickListener(this);
        rbADASMiddle.setOnClickListener(this);
        rbADASHigh.setOnClickListener(this);
        btnDvrFormat.setOnClickListener(this);
        btnDvrBT.setOnClickListener(this);
        btnDvrSystemSettings.setOnClickListener(this);
/*
        //获取手机屏幕的高度
        DisplayMetrics metric = new DisplayMetrics();
        mHomeActivity.getWindowManager().getDefaultDisplay().getMetrics(metric);
        int widthPixels = (int) (metric.widthPixels * 0.75f);
        int heightPixels = (int) (metric.heightPixels * 0.6f);

        popupWindow = new PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setWidth(widthPixels);
        popupWindow.setHeight(heightPixels);

        popupWindow.setBackgroundDrawable(new ColorDrawable(-000000));
        popupWindow.setAnimationStyle(R.style.SettingsTranslateAnim);*/

        cleanLeftButton();
        llBtnWireless.setSelected(true);
        llWireless.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ll_left_wifi:
                setLeftBtnSelected(llBtnWireless, llWireless);
                break;
            case R.id.ll_left_screen_control:
                setLeftBtnSelected(llBtnScreenControl, llScreenControl);
                break;
            case R.id.ll_left_other:
                setLeftBtnSelected(llBtnOther, llOther);
                break;
            case R.id.ll_left_dvr:
                setLeftBtnSelected(llBtnDVR, llDVR);
                break;
            case R.id.rb_close_screen:
                setScreenControlRbCheck(rbCloseScreen, CustomValue.RADIO_BTN_TYPE_SCREEN_CONTROL);
                break;
            case R.id.rb_screen_saver:
                setScreenControlRbCheck(rbScreenSaver, CustomValue.RADIO_BTN_TYPE_SCREEN_CONTROL);
                mSettingsUtils.setScreenOffTimeOut(60 * 1000);
                setScreenControlRbCheck(rbTime1, CustomValue.RADIO_BTN_TYPE_SCREEN_OFF_TIMEOUT);
                break;
            case R.id.rb_bright:
                setMaxScreenOffTimeOut();
                break;
            case R.id.rb_time_1:
                mSettingsUtils.setScreenOffTimeOut(60 * 1000);
                setScreenControlRbCheck(rbTime1, CustomValue.RADIO_BTN_TYPE_SCREEN_OFF_TIMEOUT);
                break;
            case R.id.rb_time_5:
                mSettingsUtils.setScreenOffTimeOut(300 * 1000);
                setScreenControlRbCheck(rbTime5, CustomValue.RADIO_BTN_TYPE_SCREEN_OFF_TIMEOUT);
                break;
            case R.id.rb_time_30:
                mSettingsUtils.setScreenOffTimeOut(1800 * 1000);
                setScreenControlRbCheck(rbTime30, CustomValue.RADIO_BTN_TYPE_SCREEN_OFF_TIMEOUT);
                break;
            case R.id.rb_record_time_1:
                setScreenControlRbCheck(rbRecordTime1, CustomValue.RADIO_BTN_TYPE_RECORD_TIME);
                mSharedPreferencesTool.saveRecordTime(1);
                break;
            case R.id.rb_record_time_3:
                setScreenControlRbCheck(rbRecordTime3, CustomValue.RADIO_BTN_TYPE_RECORD_TIME);
                mSharedPreferencesTool.saveRecordTime(3);
                break;
            case R.id.rb_record_time_5:
                setScreenControlRbCheck(rbRecordTime5, CustomValue.RADIO_BTN_TYPE_RECORD_TIME);
                mSharedPreferencesTool.saveRecordTime(5);
                break;
            case R.id.rb_collision_value_low:
                setScreenControlRbCheck(rbCollisionLow, CustomValue.RADIO_BTN_TYPE_COLLISION);
                mSharedPreferencesTool.saveCollisionLevel(1);
                break;
            case R.id.rb_collision_value_middle:
                setScreenControlRbCheck(rbCollisionMiddle, CustomValue.RADIO_BTN_TYPE_COLLISION);
                mSharedPreferencesTool.saveCollisionLevel(2);
                break;
            case R.id.rb_collision_value_high:
                setScreenControlRbCheck(rbCollisionHigh, CustomValue.RADIO_BTN_TYPE_COLLISION);
                mSharedPreferencesTool.saveCollisionLevel(3);
                break;
            case R.id.rb_collision_value_close:
                setScreenControlRbCheck(rbCollisionClose, CustomValue.RADIO_BTN_TYPE_COLLISION);
                mSharedPreferencesTool.saveCollisionLevel(0);
                break;
            case R.id.rb_adas_value_low:
                setScreenControlRbCheck(rbADASLow, CustomValue.RADIO_BTN_TYPE_ADAS);
                mSharedPreferencesTool.saveADALevel(1);
                break;
            case R.id.rb_adas_value_middle:
                setScreenControlRbCheck(rbADASMiddle, CustomValue.RADIO_BTN_TYPE_ADAS);
                mSharedPreferencesTool.saveADALevel(2);
                break;
            case R.id.rb_adas_value_high:
                setScreenControlRbCheck(rbADASHigh, CustomValue.RADIO_BTN_TYPE_ADAS);
                mSharedPreferencesTool.saveADALevel(3);
                break;
            case R.id.btn_dvr_bt:
                Intent bt = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(bt);
                finish();
                break;
            case R.id.btn_dvr_format:
//                mSettingsUtils.formatMedia(StoragePaTool.getStoragePath(true));
                finishAffinity();
                formatSDCard();
                break;
            case R.id.btn_dvr_settings:
                startSettings();
                break;
            case R.id.rb_screen_control_day:
                setDayOrNightBtnCheck(rbDay);
//                mSharedPreferencesTool.saveBoolean("btn_day", true);
//                mSharedPreferencesTool.saveBoolean("btn_night", false);
                sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_BRIGHTNESS, 100);
                break;
            case R.id.rb_screen_control_night:
                setDayOrNightBtnCheck(rbNight);
//                mSharedPreferencesTool.saveBoolean("btn_night", true);
//                mSharedPreferencesTool.saveBoolean("btn_day", false);
                sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_BRIGHTNESS, 40);
                break;
            case R.id.rb_screen_control_auto:
                setAutoBrightnessCheck(true);
                break;
            case R.id.ll_btn_wifi:
//                sendMessageToHomeActivity(CustomValue.HANDLE_POP_WIFI_BTN);
                wifiOpenOrClose(!llBtnWifi.isSelected());
                break;
            case R.id.ll_btn_hotspot:
                wifiUtils.setWifiApState(!wifiUtils.isWifiApOpen());
//                sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_BTN, 1);
                break;
            case R.id.ll_btn_gps:
                mSettingsUtils.openGPS(!mSettingsUtils.isGpsOpen());
                sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_BTN, 2);
                break;
            case R.id.ll_btn_mobile_data:
                if (mSettingsUtils.isHasSimCard()) {
                    mSettingsUtils.setDataEnabled(!mSettingsUtils.getDataEnabled());
                    sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_BTN, 3);
                } else {
                    ToastTool.showToast(R.string.no_sim_card);
                }
                break;
            case R.id.ll_btn_bluetooth:
                mSettingsUtils.openOrCloseBT(!llBtnBlueTooth.isSelected());
                sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_BTN, 4);
                break;
            case R.id.iv_switch_fm:
                mSettingsUtils.openOrCloseFM(!ivFmSwitch.isSelected());
                updateBtnFM(mSettingsUtils.getFmStatus());
                break;
        }
    }

    public void formatSDCard() {
        if (CustomValue.NOT_DVR) {
            mSettingsUtils.startFormatting();
        } else {
            Intent intent = new Intent(CustomValue.ACTION_FORMAT_SD_CARD);
            sendBroadcast(intent);
        }
    }

    private void startSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.d(TAG, "startSettings: " + e.toString());
        }
    }

    private void setLeftBtnSelected(LinearLayout leftBtn, LinearLayout layout) {
        cleanLeftButton();
        leftBtn.setSelected(true);
        layout.setVisibility(View.VISIBLE);
    }

    private void setScreenControlRbCheck(RadioButton btn, int type) {
        switch (type) {
            case CustomValue.RADIO_BTN_TYPE_SCREEN_OFF_TIMEOUT:
                cleanFunctionRBCheck();
                rbScreenSaver.setChecked(true);
                cleanTimeRBCheck();
                break;
            case CustomValue.RADIO_BTN_TYPE_RECORD_TIME:
                cleanRecordTimeRBCheck();
                break;
            case CustomValue.RADIO_BTN_TYPE_ADAS:
                cleanADASRBCheck();
                break;
            case CustomValue.RADIO_BTN_TYPE_COLLISION:
                cleanCollisionRBCheck();
                break;
            case CustomValue.RADIO_BTN_TYPE_SCREEN_CONTROL:
                cleanFunctionRBCheck();
                break;
        }
        btn.setChecked(true);
    }

    private void setAutoBrightnessCheck(boolean value) {
        rbAuto.setChecked(value);
        mSharedPreferencesTool.saveAutoBrightness(value);
        Log.d(TAG, "setAutoBrightnessCheck: " + value);
    }

    private void initScreenOutTime() {
        int timeType = mSettingsUtils.getScreenOutTime();
        // max ScreenOffTimeOut
        if (mSharedPreferencesTool.isFirstStart()) {
            setMaxScreenOffTimeOut();
            mSharedPreferencesTool.saveFirstStart();
            return;
        }
        Log.d(TAG, "initScreenOutTime: " + timeType);
        cleanTimeRBCheck();
        cleanFunctionRBCheck();
        if (timeType == 1) {
            rbTime1.setChecked(true);
        } else if (timeType == 2) {
            rbTime5.setChecked(true);
        } else if (timeType == 3) {
            rbTime30.setChecked(true);
        } else {
            rbBright.setChecked(true);
        }
        if (timeType != 4) {
            rbScreenSaver.setChecked(true);
        }
    }

    private void initRecordTime() {
        int time = mSharedPreferencesTool.getRecordTime();
        RadioButton radioButton;
        if (time == 1) {
            radioButton = rbRecordTime1;
        } else if (time == 3) {
            radioButton = rbRecordTime3;
        } else {
            radioButton = rbRecordTime5;
        }
        setScreenControlRbCheck(radioButton, CustomValue.RADIO_BTN_TYPE_RECORD_TIME);
    }

    private void initADASLevel() {
        int level = mSharedPreferencesTool.getADASLevel();
        RadioButton radioButton;
        if (level == 1) {
            radioButton = rbADASLow;
        } else if (level == 2) {
            radioButton = rbADASMiddle;
        } else {
            radioButton = rbADASHigh;
        }
        setScreenControlRbCheck(radioButton, CustomValue.RADIO_BTN_TYPE_ADAS);
    }

    private void initCollisionLevel() {
        int level = mSharedPreferencesTool.getCollisionLevel();
        RadioButton radioButton;
        if (level == 0) {
            radioButton = rbCollisionClose;
        } else if (level == 1) {
            radioButton = rbCollisionLow;
        } else if (level == 2) {
            radioButton = rbCollisionMiddle;
        } else {
            radioButton = rbCollisionHigh;
        }
        setScreenControlRbCheck(radioButton, CustomValue.RADIO_BTN_TYPE_COLLISION);
    }

    private void setDayOrNightBtnCheck(RadioButton btn) {
        cleanDayOrNightRBCheck();
        btn.setChecked(true);
    }

    private void sendMessage(int what) {
        if (mListener != null) {
            mListener.sendMessageToActivity(what);
        }
    }

    private void sendMessageToHomeActivity(int messageCode) {
        mHandler.sendEmptyMessage(messageCode);
    }

    public void sendMessageToHomeActivity(int num, int type) {
        Message message = Message.obtain();
        message.what = num;
        message.arg1 = type;
        mHandler.sendMessage(message);
    }

    private void sendMessageToHomeActivity(int num, int type, int enable) {
        Message message = Message.obtain();
        message.what = num;
        message.arg1 = type;
        message.arg2 = enable;
        mHandler.sendMessage(message);
    }

    private void cleanLeftButton() {
        llBtnWireless.setSelected(false);
        llBtnScreenControl.setSelected(false);
        llBtnOther.setSelected(false);
        llBtnDVR.setSelected(false);

        llWireless.setVisibility(View.GONE);
        llScreenControl.setVisibility(View.GONE);
        llOther.setVisibility(View.GONE);
        llDVR.setVisibility(View.GONE);
    }

    private void cleanDayOrNightRBCheck() {
        rbDay.setChecked(false);
        rbNight.setChecked(false);
    }

    private void cleanFunctionRBCheck() {
        rbCloseScreen.setChecked(false);
        rbBright.setChecked(false);
        rbScreenSaver.setChecked(false);
    }

    private void cleanTimeRBCheck() {
        rbTime1.setChecked(false);
        rbTime5.setChecked(false);
        rbTime30.setChecked(false);
    }

    private void cleanRecordTimeRBCheck() {
        rbRecordTime1.setChecked(false);
        rbRecordTime3.setChecked(false);
        rbRecordTime5.setChecked(false);
    }

    private void cleanCollisionRBCheck() {
        rbCollisionLow.setChecked(false);
        rbCollisionMiddle.setChecked(false);
        rbCollisionHigh.setChecked(false);
        rbCollisionClose.setChecked(false);
    }

    private void cleanADASRBCheck() {
        rbADASLow.setChecked(false);
        rbADASMiddle.setChecked(false);
        rbADASHigh.setChecked(false);
    }

    private void setPopupWindowListener() {
        llBtnWireless.setOnClickListener(this);
        llBtnScreenControl.setOnClickListener(this);
        llBtnOther.setOnClickListener(this);
        llBtnDVR.setOnClickListener(this);

        seekBarBrightness.setOnSeekBarChangeListener(this);
        seekBarVolume.setOnSeekBarChangeListener(this);
        ivFmSwitch.setOnClickListener(this);

    }

    private void updateWifiBtn() {
        if (llBtnWifi.isSelected()) {
            WifiSwitchClose();
        } else {
            WifiSwitchOpen();
        }
    }

    private void WifiSwitchClose() {
        llBtnWifi.setSelected(false);
//        wifiUtils.closeWifi();
        tvWifi.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
        tvWifiStatus.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
        tvWifiStatus.setText(R.string.setting_closed);
    }

    private void WifiSwitchOpen() {
//        if (!wifiUtils.isWifiEnable()) {
//            wifiUtils.openWifi();
//        }
        llBtnWifi.setSelected(true);
        tvWifi.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
        tvWifiStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
        tvWifiStatus.setText(R.string.setting_opened);
    }

    private void wifiOpenOrClose(boolean isOpen) {
        if (isOpen) {
            if (!wifiUtils.isWifiEnable()) {
                wifiUtils.openWifi();
            }
        } else {
            wifiUtils.closeWifi();
        }
    }

    private void gpsOpenOrClose(boolean isOpen) {
        updateBtnGPS(!isOpen);
    }

    private void updateWifiBtnStatus(boolean isOpen) {
        if (isOpen) {
            llBtnWifi.setSelected(true);
            tvWifi.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
            tvWifiStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
            tvWifiStatus.setText(R.string.setting_opened);
        } else {
            llBtnWifi.setSelected(false);
            tvWifi.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
            tvWifiStatus.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
            tvWifiStatus.setText(R.string.setting_closed);
        }
    }

    private void settingsSwitchOff(LinearLayout llButton, ImageView ivIcon, TextView tvStatus) {
        llButton.setSelected(false);
        ivIcon.setSelected(false);
        tvStatus.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
        tvStatus.setText(R.string.setting_closed);
    }

    private void settingsSwitchOn(LinearLayout llButton, ImageView ivIcon, TextView tvStatus) {
        llButton.setSelected(true);
        ivIcon.setSelected(true);
        tvStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
        tvStatus.setText(R.string.setting_opened);
    }

    private void update4GBtn(boolean isOpen) {
        llBtnMobileData.setSelected(isOpen);
        if (isOpen) {
            tvMobileDataStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
            tvMobileDataStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
            tvMobileDataStatus.setText(R.string.setting_opened);
        } else {
            tvWifi.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
            tvMobileDataStatus.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
            tvMobileDataStatus.setText(R.string.setting_closed);
        }
    }

    private void update4GState(boolean isOpen) {
        Log.d(TAG, "update4GState: " + isOpen);
        if (isOpen) {
            tv4GStatus.setText(R.string.mobile_data_status_connected);
            tv4GStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
        } else {
            tv4GStatus.setText(R.string.mobile_data_status_unconnected);
            tv4GStatus.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
        }
    }


    /**
     * 按钮选中与selected相反
     *
     * @param id
     */
    private void updateSettingButton(int id) {
        LinearLayout llButton;
        ImageView ivIcon;
        TextView tvStatus;
        switch (id) {
            case 1:
                llButton = llBtnHotspot;
                ivIcon = ivHotspot;
                tvStatus = tvHotspotStatus;
                break;
            case 2:
                llButton = llBtnGps;
                ivIcon = ivGps;
                tvStatus = tvGpsStatus;
                break;
            case 3:
                llButton = llBtnMobileData;
                ivIcon = ivMobileData;
                tvStatus = tvMobileDataStatus;
                break;
            default:
                llButton = llBtnBlueTooth;
                ivIcon = ivBlueTooth;
                tvStatus = tvBlueTooth;
//                mSettingsUtils.openOrCloseBT(!llBtnBlueTooth.isSelected());
                break;
        }
        boolean isSelector = llButton.isSelected();
        if (isSelector) {
            settingsSwitchOff(llButton, ivIcon, tvStatus);
        } else {
            settingsSwitchOn(llButton, ivIcon, tvStatus);
        }

//        if (id == 3) {
//            update4GState(!isSelector);
//        }
    }

    /**
     * 根据同行者语音命令开启和关闭
     *
     * @param btnType 区分按钮
     * @param enable  是否选中
     */
    private void updateSettingButtonByTXZ(int btnType, int enable) {
        LinearLayout llButton;
        ImageView ivIcon;
        TextView tvStatus;
        boolean switchOpen;
        if (enable == 1) {
            switchOpen = true;
        } else {
            switchOpen = false;
        }
        switch (btnType) {
            case 1:
                llButton = llBtnHotspot;
                ivIcon = ivHotspot;
                tvStatus = tvHotspotStatus;
                break;
            case 2:
                llButton = llBtnGps;
                ivIcon = ivGps;
                tvStatus = tvGpsStatus;
                mSettingsUtils.openGPS(switchOpen);
                break;
            case 3:
                llButton = llBtnMobileData;
                ivIcon = ivMobileData;
                tvStatus = tvMobileDataStatus;
//                update4GState(switchOpen);
                break;
            default:
                llButton = llBtnBlueTooth;
                ivIcon = ivBlueTooth;
                tvStatus = tvBlueTooth;
//                mSettingsUtils.openOrCloseBT(switchOpen);
                break;
        }
        if (switchOpen) {
            settingsSwitchOn(llButton, ivIcon, tvStatus);
        } else {
            settingsSwitchOff(llButton, ivIcon, tvStatus);
        }
    }


    /**
     * 更新拖动后的seekBar值
     *
     * @param type 区分seekBar
     */
    private void updateSeekBarProgress(int type) {
        if (type == 0) {
            int progress = seekBarBrightness.getProgress();
            mSettingsUtils.progressChangeToBrightness(progress);
            Log.d(TAG, "updateSeekBarProgress: " + progress);
            tvBrightnessValue.setText(String.valueOf(progress));
        } else {
            int volume = seekBarVolume.getProgress();
            mSettingsUtils.setVolume(volume);
            tvVolumeValue.setText(String.valueOf(volume));
        }
    }

    /**
     * @param value 屏幕亮度值
     */
    private void updateBrightnessByProgress(int value) {
        String progressValue = String.valueOf(value);
        tvBrightnessValue.setText(progressValue);
        seekBarBrightness.setProgress(value);
        mSettingsUtils.progressChangeToBrightness(value);
    }

    /**
     * 根据系统音量更新声音进度条
     */
    private void updateVolume() {
        int volume = mSettingsUtils.getCurrentVolume();
        tvVolumeValue.setText(String.valueOf(volume));
        seekBarVolume.setProgress(volume);
    }

    /**
     * 根据系统屏幕亮度值更新亮度进度条
     */
    private void updateBrightness() {
        int brightness = mSettingsUtils.getScreenBrightnessPercentageValue();
        Log.d(TAG, "updateBrightness: brightness " + brightness);
        seekBarBrightness.setProgress(brightness);
        tvBrightnessValue.setText(String.valueOf(brightness));
    }

    private void setMaxScreenOffTimeOut(){
        cleanFunctionRBCheck();
        cleanTimeRBCheck();
        rbBright.setChecked(true);
        mSettingsUtils.setScreenOffTimeOut(Integer.MAX_VALUE);
    }

    public void cleanHandle() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStopTrackingTouch: ");
        if (seekBar.getId() == R.id.sb_brightness) {
            sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_SEEK_BAR, 0);
        } else {
            sendMessageToHomeActivity(CustomValue.HANDLE_POP_UPDATE_SEEK_BAR, 1);
        }
        if (seekBar.getId() == R.id.sb_brightness) {
            setAutoBrightnessCheck(false);
            cleanDayOrNightRBCheck();
        }
    }

    @Override
    public void openOrClose(int type, boolean state) {
        Log.d(TAG, "openOrClose: " + type + " state " + state);
        Message message = Message.obtain();
        message.what = CustomValue.HANDLE_POP_UPDATE_BTN_BY_LISTENER;

        switch (type) {
            case CustomValue.TYPE_WIFI_STATE:
                message.arg1 = 2;
                break;
            case CustomValue.TYPE_BT:
                message.arg1 = 4;
                break;
            case CustomValue.TYPE_FM:
                message.arg1 = 5;
                break;
            case CustomValue.TYPE_WIFI_AP:
                message.arg1 = 6;
                break;
            case CustomValue.TYPE_MOBILE:
                message.arg1 = 7;
                break;
            case CustomValue.TYPE_VOLUME:
                message.arg1 = 8;
                break;
        }
//        if (type == CustomValue.TYPE_WIFI_STATE) {//wifi
//            message.arg1 = 2;
//        }
//        if (type == CustomValue.TYPE_BT) {//bt
//            message.arg1 = 4;
//        }
//        if (type == CustomValue.TYPE_FM) {//fm
//            message.arg1 = 5;
//        }
//        if (type == CustomValue.TYPE_WIFI_AP) {//wifi ap
//            message.arg1 = 6;
//        }
//        if (type == CustomValue.TYPE_MOBILE) {//mobile network
//            message.arg1 = 7;
//        }

        message.obj = state;
        if (mHandler != null) {
            mHandler.sendMessage(message);
        }
    }


    private void updateBtnByListener(int btnType, boolean isOpen) {
        LinearLayout llButton;
        ImageView ivIcon;
        TextView tvStatus;

        switch (btnType) {
            case 1:
                llButton = llBtnHotspot;
                ivIcon = ivHotspot;
                tvStatus = tvHotspotStatus;
                break;
            case 2:
                llButton = llBtnGps;
                ivIcon = ivGps;
                tvStatus = tvGpsStatus;
                break;
            case 3:
                llButton = llBtnMobileData;
                ivIcon = ivMobileData;
                tvStatus = tvMobileDataStatus;
                break;
            default:
                llButton = llBtnBlueTooth;
                ivIcon = ivBlueTooth;
                tvStatus = tvBlueTooth;
                break;
        }
        if (isOpen) {
            settingsSwitchOn(llButton, ivIcon, tvStatus);
        } else {
            settingsSwitchOff(llButton, ivIcon, tvStatus);
        }
    }

    private void updateBtnFM(boolean b) {
        ivFmSwitch.setSelected(b);
    }

    private void updateBtnHotspot(boolean b) {
        ivHotspot.setSelected(b);
        if (b) {
            tvHotspotStatus.setText(R.string.setting_opened);
            tvHotspotStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
        } else {
            tvHotspotStatus.setText(R.string.setting_closed);
            tvHotspotStatus.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
        }
    }

    private void updateBtnGPS(boolean b) {
        llBtnGps.setSelected(b);
        ivGps.setSelected(b);
        if (b) {
            tvGpsStatus.setText(R.string.setting_opened);
            tvGpsStatus.setTextColor(mContext.getResources().getColor(R.color.colorBlue));
        } else {
            tvGpsStatus.setText(R.string.setting_closed);
            tvGpsStatus.setTextColor(mContext.getResources().getColor(R.color.colorWhite));
        }
    }

    private static class InnerHandler extends Handler {
        private final WeakReference<SettingsWindowActivity> activityWeakReference;
        private SettingsWindowActivity mPopupWindow;

        private InnerHandler(SettingsWindowActivity popupWindow) {
            this.activityWeakReference = new WeakReference<>(popupWindow);
            mPopupWindow = activityWeakReference.get();

        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "handleMessage: " + msg.what);
            switch (msg.what) {
                case CustomValue.HANDLE_POP_WIFI_BTN://wifi按钮
                    mPopupWindow.updateWifiBtn();
                    break;
                case CustomValue.HANDLE_POP_UPDATE_BTN:// 更新按钮状态
                    mPopupWindow.updateSettingButton(msg.arg1);
                    break;
                case CustomValue.HANDLE_POP_UPDATE_BRIGHTNESS:// 更新屏幕亮度seekBar Progress
                    mPopupWindow.updateBrightnessByProgress(msg.arg1);
                    break;
                case CustomValue.HANDLE_POP_UPDATE_SEEK_BAR:// 更新seekBar Progress
                    mPopupWindow.updateSeekBarProgress(msg.arg1);
                    break;
                case CustomValue.HANDLE_POP_UPDATE_DATA:
                    mPopupWindow.setData();
                    break;
                case CustomValue.HANDLE_POP_UPDATE_BTN_TXZ:
                    mPopupWindow.updateSettingButtonByTXZ(msg.arg1, msg.arg2);
                    break;
                case CustomValue.HANDLE_POP_UPDATE_BTN_BY_LISTENER:
                    switch (msg.arg1) {
                        case 2:
                            mPopupWindow.updateWifiBtnStatus((Boolean) msg.obj);
                            break;
                        case 4:
                            mPopupWindow.updateBtnByListener(msg.arg1, (Boolean) msg.obj);
                            break;
                        case 5:
                            mPopupWindow.updateBtnFM((Boolean) msg.obj);
                            break;
                        case 6:
                            mPopupWindow.updateBtnHotspot((Boolean) msg.obj);
                            break;
                        case 7:
                            mPopupWindow.update4GState((Boolean) msg.obj);
                            break;
                        case 8:
                            mPopupWindow.updateVolume();
                            break;
                    }
                    break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
//        registerSettingsReceiver();
        if (SystemUIApplication.getInstance().getHideNavigationBar()) {
            if (CustomValue.SCREEN_3) {
                hideNavigationBar();
            }
        }
        sendMessageToDVR(true);
//        registerGPSContentObserver();
//        registerBrightnessContentObserver();
    }

    /**
     * 注册监听屏幕亮度变化
     */
    private void registerBrightnessContentObserver() {
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), true,
                mBrightnessObserver);
    }

    private ContentObserver mBrightnessObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "onChange:selfChange " + selfChange);
            updateBrightness();
        }
    };

    /**
     * 注册监听GPS状态变化
     */
    private void registerGPSContentObserver() {
        Log.d(TAG, "registerGPSContentObserver: ");
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.System.LOCATION_PROVIDERS_ALLOWED),
                false, mGpsContentObserver);
    }

    private final ContentObserver mGpsContentObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            boolean enabled = mSettingsUtils.isGpsOpen();
            Log.d(TAG, "gps onChange: " + enabled);
        }
    };

    private void unRegisterContentObserver() {
        if (mGpsContentObserver != null) {
            getContentResolver().unregisterContentObserver(mGpsContentObserver);
        }
        if (mBrightnessObserver != null) {
            getContentResolver().unregisterContentObserver(mBrightnessObserver);
        }
    }

    /**
     * 设置 app 不随着系统字体的调整而变化
     */
    @Override
    public Resources getResources() {
        Resources res = super.getResources();
        Configuration config = new Configuration();
        config.setToDefaults();
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
    }

    @Override
    protected void onStop() {
        super.onStop();
        sendMessageToDVR(false);
//        unRegisterContentObserver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "system activity onDestroy: ");
        cleanHandle();
    }

    public static final String ACTION_SETTINGS_WINDOW = "com.android.systemui.settings_window_state";

    private void sendMessageToDVR(boolean isShow) {
        Intent intent = new Intent(ACTION_SETTINGS_WINDOW);
        intent.putExtra("window_state",isShow);
        sendBroadcast(intent);
    }

}
