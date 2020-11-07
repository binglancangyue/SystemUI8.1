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

package com.android.keyguard;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.util.EmergencyAffordanceManager;
import com.android.settingslib.WirelessUtils;

import java.util.List;

/**
 * This class implements a smart emergency button that updates itself based
 * on telephony state.  When the phone is idle, it is an emergency call button.
 * When there's a call in progress, it presents an appropriate message and
 * allows the user to return to the call.
 */
public class EmergencyButton extends Button {
    private static final Intent INTENT_EMERGENCY_DIAL = new Intent()
            .setAction("com.android.phone.EmergencyDialer.DIAL")
            .setPackage("com.android.phone")
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);

    private static final String LOG_TAG = "EmergencyButton";
    private final EmergencyAffordanceManager mEmergencyAffordanceManager;
    /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
    private static final boolean VDBG = true;
    /* @} */

    private int mDownX;
    private int mDownY;
    // SPRD: Bug695136 add the feature of calling 112 SOS after press long click.
    private final boolean mEnableSimpleSOS;

    /* SPRD: 739190  @{ */
    private final int MIN_CLICK_DELAY_TIME = 500;
    private long lastClickTime = 0;
    /* @} */

    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
            updateEmergencyCallButton();
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton();
        }

        /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
        @Override
        public void onRefreshCarrierInfo() {
            updateEmergencyCallButton();
        }
        /* @} */

        /* For SubsidyLock feature @{ */
        @Override
        public void onSubsidyDeviceLock(int mode) {
            updateEmergencyCallButton();
        }

        @Override
        public void onSubsidyEnterCode() {
            updateEmergencyCallButton();
        }
        /* @} */
    };
    private boolean mLongPressWasDragged;

    public interface EmergencyButtonCallback {
        public void onEmergencyButtonClickedWhenInCall();
        // SPRD: fix for bug 786062
        public void onEmergencyButtonClickedStartActivity();
    }

    private LockPatternUtils mLockPatternUtils;
    private PowerManager mPowerManager;
    private EmergencyButtonCallback mEmergencyButtonCallback;

    private final boolean mIsVoiceCapable;
    private final boolean mEnableEmergencyCallWhileSimLocked;
    /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
    private IntentFilter mIntentFilter;
    private ServiceStateChangeReciver mServiceStateChangeReciver;
    // SPRD: during power on the phone, we cannot read ss from KeyguardUpdateMonitor,
    // we can use mStickySS to save servicestate broadcast the last time.
    private ServiceState mStickySS = null;
    /* @} */

    public EmergencyButton(Context context) {
        this(context, null);
    }

    public EmergencyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsVoiceCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        mEnableEmergencyCallWhileSimLocked = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enable_emergency_call_while_sim_locked);
        mEmergencyAffordanceManager = new EmergencyAffordanceManager(context);
        /* SPRD: Bug695136 add the feature of calling 112 SOS after press long click @{ */
        mEnableSimpleSOS = mContext.getResources().getBoolean(
                R.bool.config_enableSimpleSOS);
        Log.d(LOG_TAG, "mEnableSimpleSOS : " + mEnableSimpleSOS);
        /* @} */
        this.setTextColor(ColorStateList.valueOf(0xE3FFFFFF));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        if (mServiceStateChangeReciver == null) {
            mServiceStateChangeReciver = new ServiceStateChangeReciver();
        }
        mContext.registerReceiver(mServiceStateChangeReciver, mIntentFilter);
        /* @} */
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        if (mServiceStateChangeReciver != null) {
            mContext.unregisterReceiver(mServiceStateChangeReciver);
            mServiceStateChangeReciver = null;
        }
        /* @} */
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
                /* SPRD: 739190  @{ */
                long currentTime = System.currentTimeMillis();
                if (isClickable() &&
                        (currentTime - lastClickTime > MIN_CLICK_DELAY_TIME)) {
                    lastClickTime = currentTime;
                    takeEmergencyCallAction();
                } else {
                    Log.d(LOG_TAG, "can not takeEmergencyCallAction : " + getText());
                }
                /* @} */
                /* @} */
            }
        });
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                /* SPRD: Bug695136 add the feature of calling 112 SOS after press long click @{ */
                Log.d(LOG_TAG, "call onLongClick mcc : " + getMcc());
                if (mEnableSimpleSOS && getMcc() != null
                        && (getMcc().equals("404") || getMcc().equals("405"))) {
                    Intent callIntent = new Intent(
                            Intent.ACTION_CALL_EMERGENCY);
                    callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    callIntent.setData(Uri.fromParts(PhoneAccount.SCHEME_TEL,
                            "112", null));
                    mContext.startActivity(callIntent);
                }
                /* @} */
                if (!mLongPressWasDragged
                        && mEmergencyAffordanceManager.needsEmergencyAffordance()) {
                    mEmergencyAffordanceManager.performEmergencyCall();
                    return true;
                }
                return false;
            }
        });
        updateEmergencyCallButton();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDownX = x;
            mDownY = y;
            mLongPressWasDragged = false;
        } else {
            final int xDiff = Math.abs(x - mDownX);
            final int yDiff = Math.abs(y - mDownY);
            int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
            if (Math.abs(yDiff) > touchSlop || Math.abs(xDiff) > touchSlop) {
                mLongPressWasDragged = true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performLongClick() {
        return super.performLongClick();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateEmergencyCallButton();
    }

    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    public void takeEmergencyCallAction() {
        MetricsLogger.action(mContext, MetricsEvent.ACTION_EMERGENCY_CALL);
        // TODO: implement a shorter timeout once new PowerManager API is ready.
        // should be the equivalent to the old userActivity(EMERGENCY_CALL_TIMEOUT)
        mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        try {
            ActivityManager.getService().stopSystemLockTaskMode();
        } catch (RemoteException e) {
            Slog.w(LOG_TAG, "Failed to stop app pinning");
        }
        if (isInCall()) {
            resumeCall();
            if (mEmergencyButtonCallback != null) {
                mEmergencyButtonCallback.onEmergencyButtonClickedWhenInCall();
            }
        } else {
            KeyguardUpdateMonitor.getInstance(mContext).reportEmergencyCallAction(
                    true /* bypassHandler */);
            getContext().startActivityAsUser(INTENT_EMERGENCY_DIAL,
                    ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(),
                    new UserHandle(KeyguardUpdateMonitor.getCurrentUser()));
            /* SPRD: fix for bug 786062 @{ */
            if (mEmergencyButtonCallback != null) {
                mEmergencyButtonCallback.onEmergencyButtonClickedStartActivity();
            }
            /* @} */
        }
    }

    private void updateEmergencyCallButton() {
        boolean visible = false;
        if (mIsVoiceCapable) {
            // Emergency calling requires voice capability.
            if (isInCall()) {
                visible = true; // always show "return to call" if phone is off-hook
            } else {
                final boolean simLocked = KeyguardUpdateMonitor.getInstance(mContext)
                        .isSimPinVoiceSecure();
                // For SubsidyLock feature
                final boolean subSidyLocked = KeyguardSubsidyLockController.getInstance(mContext)
                        .isSubsidyLock();
                if (simLocked) {
                    // Some countries can't handle emergency calls while SIM is locked.
                    visible = mEnableEmergencyCallWhileSimLocked;
                } else if (subSidyLocked){//When in SubsidyLock state, need show emergency button
                    visible = true;
                }
                else {
                    // Only show if there is a secure screen (pin/pattern/SIM pin/SIM puk);
                    visible = mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser());
                }
            }
        }
        if (visible) {
            setVisibility(View.VISIBLE);

            int textId;
            if (isInCall()) {
                textId = com.android.internal.R.string.lockscreen_return_to_call;
            } else {
                /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
                if (isEmergencyCallAllowed()) {
                    textId = com.android.internal.R.string.lockscreen_emergency_call;
                    setText(textId);
                    setClickable(true);
                } else {
                    setClickable(false);
                    textId = R.string.lockscreen_no_available_network;
                }
                /* @} */
            }
            setText(textId);
        } else {
            setVisibility(View.GONE);
        }
    }

    public void setCallback(EmergencyButtonCallback callback) {
        mEmergencyButtonCallback = callback;
    }

    /**
     * Resumes a call in progress.
     */
    private void resumeCall() {
        getTelecommManager().showInCallScreen(false);
    }

    /**
     * @return {@code true} if there is a call currently in progress.
     */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    /* SPRD: Bug695136 add the feature of calling 112 SOS after press long click @{ */
    private String getMcc () {
        String mccMnc = TelephonyManager.getDefault().getSimOperator();
        String mcc = "";
        if (mccMnc != null && mccMnc.length() > 2) {
            mcc = mccMnc.substring(0, 3);
        }
        return mcc;
    }
    /* @} */

    /* SPRD: FEATURE_SHOW_EMERGENCY_BTN_IN_KEYGUARD @{ */
    private boolean isEmergencyCallAllowed() {
        List<SubscriptionInfo> subs = KeyguardUpdateMonitor.getInstance(mContext)
                .getSubscriptionInfo(false);
        final int subSize = subs.size();
        ServiceState ss = null;
        if (VDBG) {
            Log.d(LOG_TAG, "isEmergencyCallAllowed, subSize: " + subSize);
        }
        if (subSize == 0) {
            // no active subinfo, it may indicate that there is no sim.
            return true;
        }

        for (int i = 0; i < subSize; i++) {
            int subId = subs.get(i).getSubscriptionId();
            ss = KeyguardUpdateMonitor.getInstance(mContext)
                    .mServiceStates.get(subId);
            if (VDBG) {
                Log.d(LOG_TAG, "subId: " + subId + "SS: " + ss);
            }
            if (ss != null) {
                if (ss.isEmergencyOnly() || hasService(ss)) {
                    return true;
                }
            }
        }
        if (mStickySS != null) {
            if (VDBG) {
                Log.d(LOG_TAG, "mStickySS: " + mStickySS);
            }
            if (mStickySS.isEmergencyOnly() || hasService(mStickySS)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasService(ServiceState ss) {
        if (ss != null) {
            switch (ss.getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                    return false;
                case ServiceState.STATE_POWER_OFF:
                    if (WirelessUtils.isAirplaneModeOn(mContext)) {
                        return true;
                    }
                default:
                    return true;
            }
        }
        return false;
    }

    private class ServiceStateChangeReciver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(intent.getAction())) {
                mStickySS = ServiceState.newFromBundle(intent.getExtras());
                if (VDBG) {
                    Log.d(LOG_TAG, "ServiceStateChangedIntent: " + intent.getAction() +
                        " mStickySS= " + mStickySS);
                }
                updateEmergencyCallButton();
            }
        }
    }
    /* @} */
}
