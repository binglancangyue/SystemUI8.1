/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardPinBasedInputView {
    private static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardConstants.DEBUG_SIM_STATES;
    public static final String TAG = "KeyguardSimPinView";
    //SPRD:694239
    public static final String SIM_PIN_REMAINTIMES = "gsm.sim.pin.remaintimes";

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;

    private AlertDialog mRemainingAttemptsDialog;
    private int mSubId;
    private ImageView mSimImageView;
    //SPRD:694239
    private int mRemainTimes;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
            if (KeyguardConstants.DEBUG) Log.v(TAG, "onSimStateChanged(subId=" + subId + ",state=" + simState + ")");
            switch(simState) {
                // If the SIM is removed, then we must remove the keyguard. It will be put up
                // again when the PUK locked SIM is re-entered.
                case ABSENT: {
                    // SPRD: add for bug 802572
                    if (mSubId == subId) {
                        Log.d(TAG, "subId equal,mSubId = " + mSubId);
                        KeyguardUpdateMonitor.getInstance(getContext()).reportSimUnlocked(mSubId, simState);
                        // onSimStateChanged callback can fire when the SIM PIN lock is not currently
                        // active and mCallback is null.
                        if (mCallback != null) {
                            mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
                        }
                    }
                    break;
                }
                default:
                    resetState();
            }
        }
    };

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void resetState() {
        super.resetState();
        // SPRD: fix for bug 814196
        if (KeyguardConstants.DEBUG) Log.v(TAG, "Resetting state mSubId : " + mSubId);
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        /* SPRD: Bug 694239 @{ */
        if (monitor.getSimState(mSubId) != IccCardConstants.State.PIN_REQUIRED) {
            mSubId = monitor
                    .getNextSubIdForState(IccCardConstants.State.PIN_REQUIRED);
            // SPRD: fix for bug 814196
            Log.d(LOG_TAG, "getNextSubIdForState : " + mSubId);
            resetPasswordText(true, true);
        }
        /* @} */
        boolean isEsimLocked = KeyguardEsimArea.isEsimLocked(mContext, mSubId);
        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            int count = TelephonyManager.getDefault().getSimCount();
            Resources rez = getResources();
            String msg;
            int color = Color.WHITE;
            if (count < 2) {
                msg = rez.getString(R.string.kg_sim_pin_instructions);
            } else {
                SubscriptionInfo info = monitor.getSubscriptionInfoForSubId(mSubId);
                /* SPRD: fix for bug 814196 @{ */
                int phoneId = SubscriptionManager.getPhoneId(mSubId);
                Log.d(LOG_TAG, "SubscriptionInfo:"+info.toString() + "; phoneId : " + phoneId);
                if (!SubscriptionManager.isValidPhoneId(phoneId)) {
                    phoneId = info.getSimSlotIndex();
                }
                CharSequence displayName = info != null ? "SIM"
                        + Integer.toString(phoneId + 1) : ""; // don't crash
                Log.d(LOG_TAG, "displayName:"+displayName);
                /* @} */
                /* SPRD: fix for bug 742200 @{ */
                try {
                    mRemainTimes = Integer.valueOf(TelephonyManager
                            .getTelephonyProperty(info.getSimSlotIndex(),
                                    SIM_PIN_REMAINTIMES, "0"));
                } catch (NumberFormatException e) {
                    // TODO: handle exception
                    mRemainTimes = 0;
                }
                /* @} */
                msg = rez.getString(R.string.kg_sim_pin_instructions_multi,
                        displayName, mRemainTimes);
                if (info != null) {
                    color = info.getIconTint();
                }
            }
            if (isEsimLocked) {
                msg = msg + " " + rez.getString(R.string.kg_sim_lock_instructions_esim);
            }
            mSecurityMessageDisplay.setMessage(msg);
            mSimImageView.setImageTintList(ColorStateList.valueOf(color));
        } else {
            /* SPRD: Bug 694239 @{ */
            if (mCallback != null) mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
            getSimRemainingAttemptsDialog(mRemainTimes).dismiss();
        }
        KeyguardEsimArea esimButton = findViewById(R.id.keyguard_esim_area);
        esimButton.setVisibility(isEsimLocked ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetState();
    }

    @Override
    protected int getPromtReasonStringRes(int reason) {
        // No message on SIM Pin
        return 0;
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = getContext().getResources().getQuantityString(
                    R.plurals.kg_password_wrong_pin_code, attemptsRemaining, attemptsRemaining);
        } else {
            displayMessage = getContext().getString(R.string.kg_password_pin_failed);
        }
        if (DEBUG) {
            Log.d(LOG_TAG, "getPinPasswordErrorMessage:"
                    + " attemptsRemaining=" + attemptsRemaining
                    + " displayMessage=" + displayMessage);
        }
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simPinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }
        /* SPRD:694239 @{ */
        mSimImageView = (ImageView) findViewById(R.id.keyguard_sim);
        mPasswordEntry.setLimit(true);
        /* @} */
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        // SPRD: Bug 694239 add PIN verify success prompt.
        dismissProgressDialog();
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;
        private int mSubId;

        protected CheckSimPin(String pin, int subId) {
            mPin = pin;
            mSubId = subId;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                if (DEBUG) {
                    Log.v(TAG, "call supplyPinReportResultForSubscriber(subid=" + mSubId + ")");
                }
                final int[] result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService("phone")).supplyPinReportResultForSubscriber(mSubId, mPin);
                if (DEBUG) {
                    Log.v(TAG, "supplyPinReportResult returned: " + result[0] + " " + result[1]);
                }
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining);
        if (mRemainingAttemptsDialog == null) {
            Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(msg);
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(msg);
        }
        return mRemainingAttemptsDialog;
    }

    /* SPRD:694239 @{ */
    private void dismissProgressDialog() {
        postDelayed(new Runnable() {
            public void run() {
                Log.d(TAG, "dismissProgressDialog() : mSimUnlockProgressDialog ="
                        + mSimUnlockProgressDialog);
                if (mSimUnlockProgressDialog != null) {
                    mSimUnlockProgressDialog.dismiss();
                    mSimUnlockProgressDialog = null;
                    Log.d(TAG, "dismissProgressDialog(); "
                            + "prompt subscribers PIN unlock success for one second");
                }
            }
        }, 500);
    }
    /* @} */

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText();
        // SPRD:694239
        if (entry.length() < 4 || entry.length() > 8) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint);
            resetPasswordText(true /* animate */, true /* announce */);
            mCallback.userActivity();
            return;
        }

        getSimUnlockProgressDialog().show();

        if (mCheckSimPinThread == null) {
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText(), mSubId) {
                @Override
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            resetPasswordText(true /* animate */,
                                    result != PhoneConstants.PIN_RESULT_SUCCESS /* announce */);
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                if (mSimUnlockProgressDialog != null) {
                                    mSimUnlockProgressDialog.setMessage(
                                            mContext.getString(R.string.kg_sim_pin_verify_success));
                                }
                                KeyguardUpdateMonitor.getInstance(getContext())
                                        .reportSimUnlocked(mSubId);
                                mCallback.dismiss(true, KeyguardUpdateMonitor.getCurrentUser());
                                dismissProgressDialog();
                            } else {
                                if (mSimUnlockProgressDialog != null) {
                                    mSimUnlockProgressDialog.hide();
                                }
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getSimRemainingAttemptsDialog(attemptsRemaining).show();
                                    } else {
                                        // show message
                                        mSecurityMessageDisplay.setMessage(
                                                getPinPasswordErrorMessage(attemptsRemaining));
                                    }
                                } else {
                                    // "PIN operation failed!" - no idea what this was and no way to
                                    // find out. :/
                                    mSecurityMessageDisplay.setMessage(getContext().getString(
                                            R.string.kg_password_pin_failed));
                                }
                                resetState();
                                if (DEBUG) Log.d(LOG_TAG, "verifyPasswordAndUnlock "
                                        + " CheckSimPin.onSimCheckResponse: " + result
                                        + " attemptsRemaining=" + attemptsRemaining);
                            }
                            mCallback.userActivity();
                            mCheckSimPinThread = null;
                        }
                    });
                }
            };
            mCheckSimPinThread.start();
        }
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }
}

