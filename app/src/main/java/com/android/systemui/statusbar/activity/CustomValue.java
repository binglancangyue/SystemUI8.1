package com.android.systemui.statusbar.activity;

import com.android.systemui.R;

/**
 * @author Altair
 * @date :2019.10.24 下午 02:33
 * @description: 通用参数
 */
public class CustomValue {
    public final static String ACTION_TXZ_RECV = "com.txznet.adapter.recv";
    public final static String ACTION_TXZ_SEND = "com.txznet.adapter.send";
    public static final String ACTION_START_PROCESS = "android.system.action.START_PROCESS";
    public final static String SP_NAME = "settings";
    public final static String ACTION_TXZ_OPEN = "com.bixin.launcher_t20.txz.open";
    public final static String ACTION_QUICK_SETTINGS_VIEW = "com.bixin.launcher_t20.txz.settings";
    public static final String ACTION_FM_STATE_CHANGED = "android.car.action.FM_STATE_CHANGED";
    public final static String ACTION_SEND_TO_SYSTEM_UI = "com.bixin.speechrecognitiontool.send";
    public final static String ACTION_UPDATE_SETTING_WINDOW = "com.android.systemui.setting.update";
    public final static String ACTION_UPDATE_BY_TXZ = "com.bixin.update.txz";
    public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    public static final String ACTION_SHOW_SETTING_WINDOW = "com.android.systemui.show_setting_window";
    public static final String ACTION_SET_DVR_RECORD_TIME = "com.android.systemui.SET_DVR_RECORD_TIME";
    public static final String ACTION_SET_G_SENSOR_LEVEL = "com.android.systemui.SET_G_SENSOR_LEVEL";
    public static final String ACTION_SET_ADAS_LEVEL = "com.android.systemui.SET_ADAS_LEVEL";
    public static final String ACTION_FORMAT_SD_CARD = "com.android.systemui.FORMAT_SD_CARD";
    public static final String ACTION_OPEN_DVR_CAMERA = "com.android.systemui.OPEN_CAMERA";
    public static final String ACTION_UPDATE_BRIGHTNESS_BY_TIME = "com.android.systemui.UPDATE_BRIGHTNESS_BY_TIME";
    public static final String ACTION_DVR_STATE = "com.bx.carDVR.action_dvr_state";
    public static final String ACTION_SHOW_STOP_RECORDING_DIALOG = "com.bx.carDVR.action.show_dialog";
    public final static String ACTION_GET_WEATHER = "com.bixin.speechrecognitiontool.action_get_weather";
    public final static String ACTION_UPDATE_WEATHER = "com.bixin.speechrecognitiontool.action_update_weather";
    public static final String ACTION_GO_TO_SLEEP = "com.android.systemui.action_go_to_sleep";
    public final static String ACTION_HIDE_NAVIGATION = "com.bixin.launcher.action.hide_navigation";
    public final static String ACTION_SETTINGS_FUNCTION="com.bixin.launcher.action.settings_function";
    public static final String ACTION_TW_STATE = "com.transiot.kardidvr003.machineState";

    /***平台***/
    public static final boolean SCREEN_3 = false;//kd002 3寸屏  不需要语音识别
    public static final boolean SCREEN_3_BX = false;//比心3寸屏
    public static final boolean SCREEN_3IN_KD003 = true;//3寸屏 多了个后摄像头按钮
    public static final boolean SCREEN_439IN = false;//4.39寸屏
    public static final boolean IS_966 = false;//9.66寸 车镜

    /***特殊需求***/
    public static final boolean ENGLISH_VERSION = false;//英文版本 不需要语音识别
    public static final boolean NOT_MOBILE_NETWORK = false;//7in 英文版 无移动网络
    public static final boolean NOT_MOBILE_WIFI = false;//7in 英文版 无wifi
    public static final boolean NOT_DVR = false;//7in 英文版 无DVR

    /**
     * ICON_TYPE: 导航栏图标样式
     * 0:3in_kd002  1:3in_kd003  2:7in  3:9.66in  4:bx3in
     * <p>
     * STATUS_BAR_ICON_TYPE: 状态栏图标样式
     * 0:默认(系统原样式) 1:7in
     */
    public static final int ICON_TYPE = 1;
    public static final int STATUS_BAR_ICON_TYPE = 0;

    public static final int[] HOME_ICONS = {
            R.drawable.icon_home_3in, R.drawable.ic_home_3in_kd003,
            R.drawable.icon_home_7in, R.drawable.icon_home_7in,
            R.drawable.ic_bx_btn_home_3in};

    public static final int[] BACK_ICONS = {
            R.drawable.icon_back_3in, R.drawable.selector_kd003_back,
            R.drawable.icon_back_7in, R.drawable.ic_bx_btn_back,
            R.drawable.ic_bx_btn_back_3in};

    public static final int[] FRONT_CAMERA_ICONS = {
            R.drawable.icon_camera_3in, R.drawable.selector_kd003_camera,
            R.drawable.icon_camera_7in, R.drawable.ic_bx_btn_streaming,
            R.drawable.ic_bx_btn_streaming};

    public static final int[] BACK_CAMERA_ICONS = {
            R.drawable.ic_back_facing_3in_kd003, R.drawable.selector_kd003_back_camera,
            R.drawable.ic_back_facing_3in_kd003, R.drawable.ic_back_facing_3in_kd003,
            R.drawable.ic_back_facing_3in_kd003};

    public static final int[] VOICE_ICONS = {
            R.drawable.icon_voice, R.drawable.selector_kd003_cloud,
            R.drawable.icon_voice_7in, R.drawable.home_voice_selector,
            R.drawable.selector_home_voice};

    public static final int[] SETTINGS_ICONS = {
            R.drawable.icon_settings, R.drawable.selector_kd003_recording,
            R.drawable.icon_setttings_7in, R.drawable.icon_setttings_7in,
            R.drawable.ic_bx_btn_settings_3in};

    public static final int[] LOCATION_STATUS_ICON = {
            R.drawable.stat_sys_location, R.drawable.stat_sys_location_bx_7in};

    public static final int[] BT_STATUS_ICON = {
            R.drawable.stat_sys_data_bluetooth, R.drawable.stat_bx_sys_data_bluetooth};
    public static final int[] BT_STATUS_ICON_CONNECTED = {
            R.drawable.stat_sys_data_bluetooth_connected, R.drawable.stat_bx_sys_data_bluetooth_connected};

    public static final int HANDLE_POP_WIFI_BTN = 1;
    public static final int HANDLE_POP_UPDATE_BTN = 2;
    public static final int HANDLE_POP_UPDATE_SEEK_BAR = 3;
    public static final int HANDLE_POP_UPDATE_BRIGHTNESS = 4;
    public static final int HANDLE_POP_UPDATE_DATA = 5;
    public static final int HANDLE_POP_UPDATE_BTN_TXZ = 6;
    public static final int HANDLE_POP_UPDATE_BTN_BY_LISTENER = 7;
//    public static final int HANDLE_POP_UPDATE_MOBILE_NETWORK = 8;

    public static final int RADIO_BTN_TYPE_SCREEN_OFF_TIMEOUT = 0;
    public static final int RADIO_BTN_TYPE_ADAS = 1;
    public static final int RADIO_BTN_TYPE_COLLISION = 2;
    public static final int RADIO_BTN_TYPE_RECORD_TIME = 3;
    public static final int RADIO_BTN_TYPE_SCREEN_CONTROL = 4;


    public static final int TYPE_WIFI_STATE = 1;
    public static final int TYPE_BT = 2;
    public static final int TYPE_FM = 3;
    public static final int TYPE_MOBILE = 4;
    public static final int TYPE_SDCARD = 5;
    public static final int TYPE_VOLUME = 6;
    public static final int TYPE_WIFI_AP = 7;
    public static final int TYPE_BATTERY = 8;
    public static final int TYPE_WIFI_SIGNAL = 9;
    public static final int TYPE_MOBILE_DATA_STATE = 10;
    public static final int TYPE_GPS_SIGNAL = 11;
    public static final String FM_STATE = "bx_fm_state";
    public static final String FM_VALUE = "bx_fm_value";

    public static final String ACC_PATH = "/dev/bx_gpio";
    public static final boolean IS_SUPPORT_ACC = true;

}
