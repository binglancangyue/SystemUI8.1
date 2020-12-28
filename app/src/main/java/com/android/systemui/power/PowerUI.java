/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.power;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HardwarePropertiesManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.activity.CustomValue;
import com.android.systemui.statusbar.phone.StatusBar;
import com.sprd.systemui.power.SprdPowerUI;

import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
//add by lidf
import android.widget.TextView;
import android.view.View;
import android.os.AsyncTask;
import android.view.WindowManager;
import android.os.PowerManager;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
//add end

public class PowerUI extends SystemUI {
    static final String TAG = "PowerUI";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final long TEMPERATURE_INTERVAL = 30 * DateUtils.SECOND_IN_MILLIS;
    private static final long TEMPERATURE_LOGGING_INTERVAL = DateUtils.HOUR_IN_MILLIS;
    private static final int MAX_RECENT_TEMPS = 125; // TEMPERATURE_LOGGING_INTERVAL plus a buffer
    //add by lidf
    public static boolean isCharging = true;
    private static final int FALLBACK_CONFIRM_SHUTDOWN_TIMEOUT = 10;
    public static final String ACTION_POWER_CONNECTED = "com.status.power.connected";
    public static final String ACTION_POWER_DISCONNECTED = "com.status.power.disconnected";
    AlertDialog mShutdownConfirmDialog;
    //add end

    private final Handler mHandler = new Handler();
    private final Receiver mReceiver = new Receiver();

    private PowerManager mPowerManager;
    private HardwarePropertiesManager mHardwarePropertiesManager;
    private WarningsUI mWarnings;
    private final Configuration mLastConfiguration = new Configuration();
    private int mBatteryLevel = 100;
    private int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private int mPlugType = 0;
    private int mInvalidCharger = 0;

    private int mLowBatteryAlertCloseLevel;
    private final int[] mLowBatteryReminderLevels = new int[2];

    private long mScreenOffTime = -1;

    private float mThresholdTemp;
    private float[] mRecentTemps = new float[MAX_RECENT_TEMPS];
    private int mNumTemps;
    private long mNextLogTime;
    AlertDialog mSleepConfirmDialog;

    // We create a method reference here so that we are guaranteed that we can remove a callback
    // by using the same instance (method references are not guaranteed to be the same object
    // each time they are created).
    private final Runnable mUpdateTempCallback = this::updateTemperatureWarning;
    /* SPRD: Modified for bug 505221/692451, add voltage high warning @{ */
    private int mBatteryHealth = BatteryManager.BATTERY_HEALTH_UNKNOWN;
    private SprdPowerUI mSprdPowerUI = null;
    private static final boolean SPRD_DEBUG =true;
    /* @} */

    private AlertDialog kd003Dialog;

    public void start() {
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mHardwarePropertiesManager = (HardwarePropertiesManager)
                mContext.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE);
        mScreenOffTime = mPowerManager.isScreenOn() ? -1 : SystemClock.elapsedRealtime();
        mWarnings = Dependency.get(WarningsUI.class);
        mLastConfiguration.setTo(mContext.getResources().getConfiguration());

        ContentObserver obs = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                updateBatteryWarningLevels();
            }
        };
        final ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL),
                false, obs, UserHandle.USER_ALL);
        updateBatteryWarningLevels();
        mReceiver.init();

        // Check to see if we need to let the user know that the phone previously shut down due
        // to the temperature being too high.
        showThermalShutdownDialog();

        initTemperatureWarning();

        /* SPRD: Modified for bug 505221/692451, add voltage high warning @{ */
        mSprdPowerUI = new SprdPowerUI(mContext);
        /* @} */

        if (CustomValue.IS_SUPPORT_ACC) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCheckAcc.start();
                }
            }, 10 * 1000);
        }

    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        final int mask = ActivityInfo.CONFIG_MCC | ActivityInfo.CONFIG_MNC;

        // Safe to modify mLastConfiguration here as it's only updated by the main thread (here).
        if ((mLastConfiguration.updateFrom(newConfig) & mask) != 0) {
            mHandler.post(this::initTemperatureWarning);
        }
    }

    void updateBatteryWarningLevels() {
        int critLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);

        final ContentResolver resolver = mContext.getContentResolver();
        int defWarnLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        int warnLevel = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, defWarnLevel);
        if (warnLevel == 0) {
            warnLevel = defWarnLevel;
        }
        if (warnLevel < critLevel) {
            warnLevel = critLevel;
        }

        mLowBatteryReminderLevels[0] = warnLevel;
        mLowBatteryReminderLevels[1] = critLevel;
        mLowBatteryAlertCloseLevel = mLowBatteryReminderLevels[0]
                + mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_lowBatteryCloseWarningBump);
    }

    /**
     * Buckets the battery level.
     *
     * The code in this function is a little weird because I couldn't comprehend
     * the bucket going up when the battery level was going down. --joeo
     *
     * 1 means that the battery is "ok"
     * 0 means that the battery is between "ok" and what we should warn about.
     * less than 0 means that the battery is low
     */
    private int findBatteryLevelBucket(int level) {
        if (level >= mLowBatteryAlertCloseLevel) {
            return 1;
        }
        if (level > mLowBatteryReminderLevels[0]) {
            return 0;
        }
        final int N = mLowBatteryReminderLevels.length;
        for (int i=N-1; i>=0; i--) {
            if (level <= mLowBatteryReminderLevels[i]) {
                return -1-i;
            }
        }
        throw new RuntimeException("not possible!");
    }

    private final class Receiver extends BroadcastReceiver {

        public void init() {
            // Register for Intent broadcasts for...
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            // Add for Bug#762212 the low-battery warning is disapear even in low power mode
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            //add by lidf
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(CustomValue.ACTION_GO_TO_SLEEP);

            //add end
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Slog.w(TAG, "onReceive " + action);
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                final int oldBatteryLevel = mBatteryLevel;
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
                final int oldBatteryStatus = mBatteryStatus;
                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                final int oldPlugType = mPlugType;
                mPlugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 1);
                final int oldInvalidCharger = mInvalidCharger;
                mInvalidCharger = intent.getIntExtra(BatteryManager.EXTRA_INVALID_CHARGER, 0);

                /* SPRD: Modified for bug 505221/692451, add voltage high warning @{ */
                final int oldBatteryHealth = mBatteryHealth;
                mBatteryHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH,
                    BatteryManager.BATTERY_HEALTH_UNKNOWN);
                /* @} */

                final boolean plugged = mPlugType != 0;
                final boolean oldPlugged = oldPlugType != 0;

                int oldBucket = findBatteryLevelBucket(oldBatteryLevel);
                int bucket = findBatteryLevelBucket(mBatteryLevel);

                //Open debug log.
                if (DEBUG || SPRD_DEBUG) {
                    Slog.d(TAG, "buckets   ....." + mLowBatteryAlertCloseLevel
                            + " .. " + mLowBatteryReminderLevels[0]
                            + " .. " + mLowBatteryReminderLevels[1]);
                    Slog.d(TAG, "level          " + oldBatteryLevel + " --> " + mBatteryLevel);
                    Slog.d(TAG, "status         " + oldBatteryStatus + " --> " + mBatteryStatus);
                    Slog.d(TAG, "plugType       " + oldPlugType + " --> " + mPlugType);
                    Slog.d(TAG, "invalidCharger " + oldInvalidCharger + " --> " + mInvalidCharger);
                    Slog.d(TAG, "bucket         " + oldBucket + " --> " + bucket);
                    Slog.d(TAG, "plugged        " + oldPlugged + " --> " + plugged);
                    /* SPRD: Modified for bug 505221, add voltage high warning @{ */
                    Slog.d(TAG, "health         " + oldBatteryHealth + " --> " + mBatteryHealth);
                }

                mWarnings.update(mBatteryLevel, bucket, mScreenOffTime);
                if (oldInvalidCharger == 0 && mInvalidCharger != 0) {
                    Slog.d(TAG, "showing invalid charger warning");
                    mWarnings.showInvalidChargerWarning();
                    return;
                } else if (oldInvalidCharger != 0 && mInvalidCharger == 0) {
                    mWarnings.dismissInvalidChargerWarning();
                } else if (mWarnings.isInvalidChargerWarningShowing()) {
                    // if invalid charger is showing, don't show low battery
                    return;
                }

                /* SPRD: Modified for bug 505221, add voltage high warning @{ */
                if(mSprdPowerUI != null){
                    mSprdPowerUI.checkBatteryHealth(plugged, oldPlugged, mBatteryHealth, oldBatteryHealth);
                }
                /*@}*/

                boolean isPowerSaver = mPowerManager.isPowerSaveMode();
                if (!plugged
                        && !isPowerSaver
                        && (bucket < oldBucket || oldPlugged)
                        && mBatteryStatus != BatteryManager.BATTERY_STATUS_UNKNOWN
                        && bucket < 0) {

                    // only play SFX when the dialog comes up or the bucket changes
                    final boolean playSound = bucket != oldBucket || oldPlugged;
                    mWarnings.showLowBatteryWarning(playSound);
                } else if (isPowerSaver || plugged || (bucket > oldBucket && bucket > 0)) {
                    mWarnings.dismissLowBatteryWarning();
                } else {
                    mWarnings.updateLowBatteryWarning();
                }

                if (plugged && mBatteryLevel < mLowBatteryReminderLevels[1] &&
                    (mBatteryLevel != oldBatteryLevel || !oldPlugged)) {
                    mWarnings.showCriticalBatteryWarningDialog();
                } else if (!plugged || mBatteryLevel >= mLowBatteryReminderLevels[1]){
                    mWarnings.dismissCriticalBatteryWarningDialog();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOffTime = SystemClock.elapsedRealtime();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOffTime = -1;
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                mWarnings.userSwitched();
            // Add for Bug#762212 the low-battery warning is disapear even in low power mode <--BEG
            } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
                boolean isPowerSaver = mPowerManager.isPowerSaveMode();
                if (isPowerSaver) {
                    mWarnings.dismissLowBatteryWarning();
                    mWarnings.dismissCriticalBatteryWarningDialog();
                }
            // Add for Bug#762212 the low-battery warning is disapear even in low power mode <--END
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                powerDisconnected();
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                powerConnected();
            } else if (CustomValue.ACTION_GO_TO_SLEEP.equals(action)) {
                showConfirmSleep();
                Slog.w(TAG, "showConfirmSleep");
            } else {
                Slog.w(TAG, "unknown intent: " + intent);
            }
        }
    };

    private void initTemperatureWarning() {
        ContentResolver resolver = mContext.getContentResolver();
        Resources resources = mContext.getResources();
        if (Settings.Global.getInt(resolver, Settings.Global.SHOW_TEMPERATURE_WARNING,
                resources.getInteger(R.integer.config_showTemperatureWarning)) == 0) {
            return;
        }

        mThresholdTemp = Settings.Global.getFloat(resolver, Settings.Global.WARNING_TEMPERATURE,
                resources.getInteger(R.integer.config_warningTemperature));

        if (mThresholdTemp < 0f) {
            // Get the throttling temperature. No need to check if we're not throttling.
            float[] throttlingTemps = mHardwarePropertiesManager.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                    HardwarePropertiesManager.TEMPERATURE_SHUTDOWN);
            if (throttlingTemps == null
                    || throttlingTemps.length == 0
                    || throttlingTemps[0] == HardwarePropertiesManager.UNDEFINED_TEMPERATURE) {
                return;
            }
            mThresholdTemp = throttlingTemps[0] -
                    resources.getInteger(R.integer.config_warningTemperatureTolerance);
        }

        setNextLogTime();

        // This initialization method may be called on a configuration change. Only one set of
        // ongoing callbacks should be occurring, so remove any now. updateTemperatureWarning will
        // schedule an ongoing callback.
        mHandler.removeCallbacks(mUpdateTempCallback);

        // We have passed all of the checks, start checking the temp
        updateTemperatureWarning();
    }

    private void showThermalShutdownDialog() {
        if (mPowerManager.getLastShutdownReason()
                == PowerManager.SHUTDOWN_REASON_THERMAL_SHUTDOWN) {
            mWarnings.showThermalShutdownWarning();
        }
    }

    //add by lidf
    private void powerConnected() {
        isCharging = true;
        dismissConfirmShutdown();
        mContext.sendBroadcast(new Intent(ACTION_POWER_CONNECTED));
    }

    private void powerDisconnected() {
        isCharging = false;
        showConfirmShutdown();
//        kd003ShotDown();
        mContext.sendBroadcast(new Intent(ACTION_POWER_DISCONNECTED));
    }

    void shutDown() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //add by chengyuzhou
                Intent shotdownIntent = new Intent("TXZ_ACTION_CLOSE_SCREEN");
                mContext.sendBroadcast(shotdownIntent);
                Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(intent, UserHandle.CURRENT);
            }
        });
    }


    TextView outtime;

    void showConfirmShutdown() {
        if (mShutdownConfirmDialog != null && mShutdownConfirmDialog.isShowing()) {
            return;
        }
        String str = mContext.getString(R.string.shutdown_confirm_time_out_desc);
        final AlertDialog dialog;
        if (mContext.getResources().getBoolean(
                R.bool.config_showShutdownConfirmButton)) {
            dialog = new AlertDialog.Builder(mContext).create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY);
            dialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;

            dialog.show();
            View view = View.inflate(mContext, R.layout.dialog_layout_kd003, null);
            TextView guanji = (TextView) view.findViewById(R.id.btn_downnow);
            TextView chongqi = (TextView) view.findViewById(R.id.btn_dontdow);
            outtime = (TextView) view.findViewById(R.id.tv_downtime);
            guanji.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    shutDown();
                    dialog.dismiss();
                }
            });
            chongqi.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    dialog.dismiss();
                }
            });
            dialog.getWindow().setContentView(view);

        } else {
            dialog = new AlertDialog.Builder(mContext)
                    .setTitle(com.android.internal.R.string.power_off)
                    .setMessage(String.format(
                            str, Integer.toString(FALLBACK_CONFIRM_SHUTDOWN_TIMEOUT)))
                    .create();
        }

        mShutdownConfirmDialog = dialog;
        new AsyncTask() {
            @Override
            protected Object doInBackground(Object... arg0) {
                int time = FALLBACK_CONFIRM_SHUTDOWN_TIMEOUT;
                while (time >= 0 && dialog.isShowing()) {
                    publishProgress(time);
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                    time--;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Object result) {
                super.onPostExecute(result);
                if (dialog.isShowing()) {
                    dialog.dismiss();
                    shutDown();
                }
            }

            @Override
            protected void onProgressUpdate(Object... values) {
                super.onProgressUpdate(values);
                int time = (Integer) values[0];
                String str = mContext.getString(R.string.shutdown_confirm_time_out_desc);
                //         dialog.setMessage(String.format(str, Integer.toString(time)));
                outtime.setText(String.format(str, Integer.toString(time)));
            }
        }.execute();

    }


    /**
     * by lym kd003 shot down style
     */
    void kd003ShotDown() {
/*        if (kd003Dialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            View view = View.inflate(mContext, R.layout.dialog_layout_kd003, null);
            builder.setView(view);
            TextView title = view.findViewById(R.id.tv_dialog_title);
            TextView message = view.findViewById(R.id.tv_dialog_message);
            title.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kd003Dialog.dismiss();
                    shutDown();
                }
            });
            message.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kd003Dialog.dismiss();
                }
            });
            kd003Dialog = builder.create();
            kd003Dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY);
            kd003Dialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        }
        showAlertDialog(kd003Dialog);*/
    }

    private void showAlertDialog(AlertDialog alertDialog) {
        if (!alertDialog.isShowing()) {
            alertDialog.show();
            WindowManager.LayoutParams params =
                    alertDialog.getWindow().getAttributes();
            params.width = (int) mContext.getResources().getDimension(R.dimen.kd003DialogWidth);
            alertDialog.getWindow().setAttributes(params);
        }
    }

    /**
     * add by lym
     */
    void showConfirmSleep() {
        if (mSleepConfirmDialog != null && mSleepConfirmDialog.isShowing()) {
            return;
        }
        String str = mContext.getString(R.string.sleep_confirm_time_out_desc);
        final AlertDialog dialog;
        if (mContext.getResources().getBoolean(
                R.bool.config_showShutdownConfirmButton)) {
            dialog = new AlertDialog.Builder(mContext).create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY);
            dialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;

            dialog.show();
            View view = View.inflate(mContext, R.layout.bx_usbchanger, null);
            TextView guanji = (TextView) view.findViewById(R.id.btn_downnow);
            guanji.setText(R.string.sleepnow);
            TextView chongqi = (TextView) view.findViewById(R.id.btn_dontdow);
            outtime = (TextView) view.findViewById(R.id.tv_downtime);
            chongqi.setText(R.string.cancel);
            TextView title=view.findViewById(R.id.tv_dialog_title);
            title.setText(R.string.is_sleep);
            guanji.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    goToSleep();
                    dialog.dismiss();
                }
            });
            chongqi.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    dialog.dismiss();
                }
            });
            dialog.getWindow().setContentView(view);

        } else {
            dialog = new AlertDialog.Builder(mContext)
                    .setTitle(com.android.internal.R.string.power_off)
                    .setMessage(String.format(
                            str, Integer.toString(FALLBACK_CONFIRM_SHUTDOWN_TIMEOUT)))
                    .create();
        }

        mSleepConfirmDialog = dialog;
        new AsyncTask() {
            @Override
            protected Object doInBackground(Object... arg0) {
                int time = FALLBACK_CONFIRM_SHUTDOWN_TIMEOUT;
                while (time >= 0 && dialog.isShowing()) {
                    publishProgress(time);
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                    time--;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Object result) {
                super.onPostExecute(result);
                if (dialog.isShowing()) {
                    dialog.dismiss();
                    goToSleep();
                }
            }

            @Override
            protected void onProgressUpdate(Object... values) {
                super.onProgressUpdate(values);
                int time = (Integer) values[0];
                String str = mContext.getString(R.string.sleep_confirm_time_out_desc);
                //         dialog.setMessage(String.format(str, Integer.toString(time)));
                outtime.setText(String.format(str, Integer.toString(time)));
            }
        }.execute();

    }

    private void goToSleep(){
        mPowerManager.goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
    }


    void dismissConfirmShutdown() {
        if (mShutdownConfirmDialog != null && mShutdownConfirmDialog.isShowing()) {
            mShutdownConfirmDialog.dismiss();
        }
        if (kd003Dialog != null && mShutdownConfirmDialog.isShowing()) {
            kd003Dialog.dismiss();
        }
    }
    //add end

    @VisibleForTesting
    protected void updateTemperatureWarning() {
        float[] temps = mHardwarePropertiesManager.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                HardwarePropertiesManager.TEMPERATURE_CURRENT);
        if (temps.length != 0) {
            float temp = temps[0];
            mRecentTemps[mNumTemps++] = temp;

            StatusBar statusBar = getComponent(StatusBar.class);
            if (statusBar != null && !statusBar.isDeviceInVrMode()
                    && temp >= mThresholdTemp) {
                logAtTemperatureThreshold(temp);
                mWarnings.showHighTemperatureWarning();
            } else {
                mWarnings.dismissHighTemperatureWarning();
            }
        }

        logTemperatureStats();

        mHandler.postDelayed(mUpdateTempCallback, TEMPERATURE_INTERVAL);
    }

    private void logAtTemperatureThreshold(float temp) {
        StringBuilder sb = new StringBuilder();
        sb.append("currentTemp=").append(temp)
                .append(",thresholdTemp=").append(mThresholdTemp)
                .append(",batteryStatus=").append(mBatteryStatus)
                .append(",recentTemps=");
        for (int i = 0; i < mNumTemps; i++) {
            sb.append(mRecentTemps[i]).append(',');
        }
        Slog.i(TAG, sb.toString());
    }

    /**
     * Calculates and logs min, max, and average
     * {@link HardwarePropertiesManager#DEVICE_TEMPERATURE_SKIN} over the past
     * {@link #TEMPERATURE_LOGGING_INTERVAL}.
     */
    private void logTemperatureStats() {
        if (mNextLogTime > System.currentTimeMillis() && mNumTemps != MAX_RECENT_TEMPS) {
            return;
        }

        if (mNumTemps > 0) {
            float sum = mRecentTemps[0], min = mRecentTemps[0], max = mRecentTemps[0];
            for (int i = 1; i < mNumTemps; i++) {
                float temp = mRecentTemps[i];
                sum += temp;
                if (temp > max) {
                    max = temp;
                }
                if (temp < min) {
                    min = temp;
                }
            }

            float avg = sum / mNumTemps;
            Slog.i(TAG, "avg=" + avg + ",min=" + min + ",max=" + max);
            MetricsLogger.histogram(mContext, "device_skin_temp_avg", (int) avg);
            MetricsLogger.histogram(mContext, "device_skin_temp_min", (int) min);
            MetricsLogger.histogram(mContext, "device_skin_temp_max", (int) max);
        }
        setNextLogTime();
        mNumTemps = 0;
    }

    private void setNextLogTime() {
        mNextLogTime = System.currentTimeMillis() + TEMPERATURE_LOGGING_INTERVAL;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("mLowBatteryAlertCloseLevel=");
        pw.println(mLowBatteryAlertCloseLevel);
        pw.print("mLowBatteryReminderLevels=");
        pw.println(Arrays.toString(mLowBatteryReminderLevels));
        pw.print("mBatteryLevel=");
        pw.println(Integer.toString(mBatteryLevel));
        pw.print("mBatteryStatus=");
        pw.println(Integer.toString(mBatteryStatus));
        pw.print("mPlugType=");
        pw.println(Integer.toString(mPlugType));
        pw.print("mInvalidCharger=");
        pw.println(Integer.toString(mInvalidCharger));
        pw.print("mScreenOffTime=");
        pw.print(mScreenOffTime);
        if (mScreenOffTime >= 0) {
            pw.print(" (");
            pw.print(SystemClock.elapsedRealtime() - mScreenOffTime);
            pw.print(" ago)");
        }
        pw.println();
        pw.print("soundTimeout=");
        pw.println(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.LOW_BATTERY_SOUND_TIMEOUT, 0));
        pw.print("bucket: ");
        pw.println(Integer.toString(findBatteryLevelBucket(mBatteryLevel)));
        pw.print("mThresholdTemp=");
        pw.println(Float.toString(mThresholdTemp));
        pw.print("mNextLogTime=");
        pw.println(Long.toString(mNextLogTime));
        mWarnings.dump(pw);
    }

    public interface WarningsUI {
        void update(int batteryLevel, int bucket, long screenOffTime);
        void dismissLowBatteryWarning();
        void showLowBatteryWarning(boolean playSound);
        void dismissCriticalBatteryWarningDialog();
        void showCriticalBatteryWarningDialog();
        void dismissInvalidChargerWarning();
        void showInvalidChargerWarning();
        void updateLowBatteryWarning();
        boolean isInvalidChargerWarningShowing();
        void dismissHighTemperatureWarning();
        void showHighTemperatureWarning();
        void showThermalShutdownWarning();
        void dump(PrintWriter pw);
        void userSwitched();
    }

    //ACC start
    private static final String ACC_STATE = "bx_acc_state";
    private boolean isCheckAcc = true;
    private static final int STATE_ACC_OFF = 0;
    private static final int STATE_ACC_ON = 1;
    private int mAccState = -1;
    private char accValue = '0';
    private char[] accBuf = new char[5];
    private static final int TIME_CHECK_ACC_DURATION = 1 * 1000;

    private Thread mCheckAcc = new Thread(new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "start to check acc...");
            while (isCheckAcc) {
                readAcc();
                accValue = accBuf[2];
                Log.d(TAG, "run() accValue = " + accValue);
                if (accValue == '1' && mAccState != STATE_ACC_ON) {
                    mAccState = STATE_ACC_ON;
                    Settings.Global.putInt(mContext.getContentResolver(), ACC_STATE, STATE_ACC_ON);
                    sendStateCode(0);
                } else if (accValue == '0' && mAccState != STATE_ACC_OFF) {
                    mAccState = STATE_ACC_OFF;
                    Settings.Global.putInt(mContext.getContentResolver(), ACC_STATE, STATE_ACC_OFF);
                    sendStateCode(1);
                }
                try {
                    Thread.sleep(TIME_CHECK_ACC_DURATION);
                } catch (InterruptedException e) {
                    Log.e(TAG, "mCheckAcc InterruptedException " + e.getMessage());
                    isCheckAcc = false;
                }
            }
            Log.i(TAG, "check acc end ...");
        }
    });

    private void readAcc() {
        FileReader reader = null;
        try {
            reader = new FileReader(CustomValue.ACC_PATH);
            reader.read(accBuf);
        } catch (IOException ex) {
            isCheckAcc = false;
            Log.e(TAG, "readAcc() IOException " + ex.getMessage());
        } catch (NumberFormatException e) {
            isCheckAcc = false;
            Log.e(TAG, "readAcc() IOException " + e.getMessage());
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e2) {
                }
            }
        }
    }
    //end

    private void sendStateCode(int code) {
        Intent it = new Intent("com.transiot.kardidvr003");
        it.putExtra("machineState", code);
        mContext.sendBroadcast(it);
    }

}

