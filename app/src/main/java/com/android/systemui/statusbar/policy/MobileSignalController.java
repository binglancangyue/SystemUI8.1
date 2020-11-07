/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.ims.internal.IImsRegisterListener;
import com.android.ims.internal.IImsServiceEx;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.SignalDrawable;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.Config;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.SubscriptionDefaults;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Objects;


public class MobileSignalController extends SignalController<
        MobileSignalController.MobileState, MobileSignalController.MobileIconGroup> {
    private final TelephonyManager mPhone;
    private final SubscriptionDefaults mDefaults;
    private final String mNetworkNameDefault;
    private final String mNetworkNameSeparator;
    private final ContentObserver mObserver;
    @VisibleForTesting
    final PhoneStateListener mPhoneStateListener;
    /* SPRD: Dual volte signalStrength display for bug 666045. @{ */
    private PhoneStateListener mDualVoLTEListener;
    private Looper mLooper;
    /* @} */
    // Save entire info for logging, we only use the id.
    final SubscriptionInfo mSubscriptionInfo;

    // @VisibleForDemoMode
    final SparseArray<MobileIconGroup> mNetworkToIconLookup;

    // Since some pieces of the phone state are interdependent we store it locally,
    // this could potentially become part of MobileState for simplification/complication
    // of code.
    private int mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int mDataState = TelephonyManager.DATA_DISCONNECTED;
    private ServiceState mServiceState;
    private SignalStrength mSignalStrength;
    private MobileIconGroup mDefaultIcons;
    private Config mConfig;
    /* SPRD: Bug 697839 add For VOLTE and VoWiFi icon. @{ */
    private boolean mIgnoreGoogleInet;
    public static final int DEFAULT_INET_CONDITION = 1;
    private final CallbackHandler mCallbackHandler;
    private boolean mIsImsListenerRegistered;
    private boolean mIsVoLteEnable;
    private boolean mIsVoLteBoard;
    /* SPRD: Bug 806908 add For VoWiFi icon @{ */
    private boolean mIsRegImsChange;
    private boolean mIsVoWifiBoard;
    /* @} */
    private IImsServiceEx mIImsServiceEx;
    public static final int IMS_FEATURE_TYPE_DEFAULT = -2;
    private int mImsType;
    private boolean mDualImsRegistered;
    private boolean mDualVolteRegistered = false;
    /* SPRD: Add for bug 747639. @{ */
    public static final int SLOT_ID_ONE = 0;
    public static final int SLOT_ID_TWO = 1;
    /* @} */
    // SPRD: Distinguish 3G type icons in Orange for bug 688768.
    private boolean mShowOrange3GIcon;
    /* @} */
    /* SPRD: modify for bug655620 @{ */
    private final static String ACTION_MODEM_CHANGE = "com.android.modemassert.MODEM_STAT_CHANGE";
    private final static String MODEM_STAT = "modem_stat";
    private final static String MODEM_ASSERT = "modem_assert";
    private final static String MODEM_RESET_ENABLE = "1";
    private final static String MODEM_RESET_KEY = "persist.sys.sprd.modemreset";
    private boolean mIsModemResetActive = false;
    private boolean mIsVolteIconShow = false;
    private int mVolteIconForSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mVolteIconResId = 0;
    private boolean mEnableRefreshSignalStrengths = true;
    private boolean mEnableRefreshServiceState = true;
    private boolean mEnableRefreshMobileDataIndicator = true;
    private boolean mEnableRefreshDataConnectionState = true;
    private boolean mEnableRefreshVoLteServiceState = true;
    private final static int MODEM_RESET_TIME_OUT = 20 * 1000;
    private final static int MODEM_RESET_MSG = 10;
    /* SPRD: modify for bug723743 @{ */
    private boolean mIsImsReceiverRegistered;
    private boolean mIsModemReciverRegistered;
    /* @} */

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MODEM_RESET_MSG:
                    Log.i(mTag, "modem reset time out,so updateTelephony and refreshImsIcons");
                    mIsModemResetActive = false;
                    mEnableRefreshSignalStrengths = true;
                    mEnableRefreshServiceState = true;
                    mEnableRefreshMobileDataIndicator = true;
                    mEnableRefreshDataConnectionState = true;
                    mEnableRefreshVoLteServiceState = true;
                    updateTelephony();
                    /* SPRD: modify by BUG 798705 @{ */
                    refreshImsIcons(null);
                    refreshVolteIndicators(mIsVolteIconShow, mVolteIconForSubId,
                                           mVolteIconResId, null);
                    /* @} */
                    notifyListeners(mCallbackHandler);
                    break;
                default:
                    return;
            }
        }
    };
    /* @} */
    // TODO: Reduce number of vars passed in, if we have the NetworkController, probably don't
    // need listener lists anymore.
    public MobileSignalController(Context context, Config config, boolean hasMobileData,
            TelephonyManager phone, CallbackHandler callbackHandler,
            NetworkControllerImpl networkController, SubscriptionInfo info,
            SubscriptionDefaults defaults, Looper receiverLooper) {
        super("MobileSignalController(" + info.getSubscriptionId() + ")", context,
                NetworkCapabilities.TRANSPORT_CELLULAR, callbackHandler,
                networkController);
        mNetworkToIconLookup = new SparseArray<>();
        mConfig = config;
        mPhone = phone;
        mDefaults = defaults;
        mSubscriptionInfo = info;
        // SPRD: Dual volte signalStrength display for bug 666045.
        mLooper = receiverLooper;
        mPhoneStateListener = new MobilePhoneStateListener(info.getSubscriptionId(),
                receiverLooper);
        mNetworkNameSeparator = getStringIfExists(R.string.status_bar_network_name_separator);
        mNetworkNameDefault = getStringIfExists(
                com.android.internal.R.string.lockscreen_carrier_default);
        /* SPRD: Bug 697839 add For VOLTE and VoWiFi icon. @{ */
        mCallbackHandler = callbackHandler;
        mIsVoLteBoard = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_device_volte_available);
        /* SPRD: Bug 806908 add For VoWiFi icon @{ */
        mIsVoWifiBoard = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_device_wfc_ims_available);
        mIsVoLteBoard = mIsVoLteBoard || ImsManager.isVolteEnabledByPlatform(mContext);
        mIsVoWifiBoard = mIsVoWifiBoard || ImsManager.isWfcEnabledByPlatform(mContext);
        mIsRegImsChange = mIsVoLteBoard || mIsVoWifiBoard;
        Log.d(mTag, "mIsVoLteBoard = " + mIsVoLteBoard + " mIsVoWifiBoard = " + mIsVoWifiBoard);
        /* @} */
        mIgnoreGoogleInet = mContext.getResources().getBoolean(
                R.bool.config_ignore_google_inet_validation);
        /* @} */
        // SPRD: Distinguish 3G type icons in Orange for bug 688768.
        mShowOrange3GIcon = mContext.getResources().getBoolean(
                R.bool.config_show_orange_3g_statusbar_icon);

        mapIconSets();

        String networkName = info.getCarrierName() != null ? info.getCarrierName().toString()
                : mNetworkNameDefault;
        mLastState.networkName = mCurrentState.networkName = networkName;
        mLastState.networkNameData = mCurrentState.networkNameData = networkName;
        mLastState.enabled = mCurrentState.enabled = hasMobileData;
        mLastState.iconGroup = mCurrentState.iconGroup = mDefaultIcons;
        // Get initial data sim state.
        updateDataSim();
        mObserver = new ContentObserver(new Handler(receiverLooper)) {
            @Override
            public void onChange(boolean selfChange) {
                updateTelephony();
            }
        };
    }

    public void setConfiguration(Config config) {
        mConfig = config;
        mapIconSets();
        updateTelephony();
        // SPRD: modify by BUG 711986
        notifyListeners();
    }

    public int getDataContentDescription() {
        return getIcons().mDataContentDescription;
    }

    public void setAirplaneMode(boolean airplaneMode) {
        mCurrentState.airplaneMode = airplaneMode;
        notifyListenersIfNecessary();
    }

    public void setUserSetupComplete(boolean userSetup) {
        mCurrentState.userSetup = userSetup;
        notifyListenersIfNecessary();
    }

    @Override
    public void updateConnectivity(BitSet connectedTransports, BitSet validatedTransports) {
        boolean isValidated = validatedTransports.get(mTransportType);
        mCurrentState.isDefault = connectedTransports.get(mTransportType);
        // Only show this as not having connectivity if we are default.
        /* SPRD: Bug 693278 add For Data and Vowifi Tile. @{ */
        if (mIgnoreGoogleInet) {
            mCurrentState.inetCondition = DEFAULT_INET_CONDITION;
        } else {
            mCurrentState.inetCondition = (isValidated || !mCurrentState.isDefault) ? 1 : 0;
        }
        /* @} */
        notifyListenersIfNecessary();
    }

    public void setCarrierNetworkChangeMode(boolean carrierNetworkChangeMode) {
        mCurrentState.carrierNetworkChangeMode = carrierNetworkChangeMode;
        updateTelephony();
    }

    /**
     * Start listening for phone state changes.
     */
    public void registerListener() {
        mPhone.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY
                        | PhoneStateListener.LISTEN_CARRIER_NETWORK_CHANGE);
        mContext.getContentResolver().registerContentObserver(Global.getUriFor(Global.MOBILE_DATA),
                true, mObserver);
        mContext.getContentResolver().registerContentObserver(Global.getUriFor(
                Global.MOBILE_DATA + mSubscriptionInfo.getSubscriptionId()),
                true, mObserver);
        /* SPRD: only show one sim icon after many time hotplug for bug 672530. @{ */
        if (mIsRegImsChange) {// SPRD: Bug 806908 add For VoWiFi icon
            Log.d(mTag, "registerListener ,mIsVoLteBoard: " + mIsVoLteBoard + " address: "
                  + super.toString());
            IntentFilter filter = new IntentFilter();
            filter.addAction(ImsManager.ACTION_IMS_SERVICE_UP);
            filter.addAction(ImsManager.ACTION_IMS_SERVICE_DOWN);
            mContext.registerReceiver(mImsIntentReceiver, filter);
            // SPRD: modify for bug723743
            mIsImsReceiverRegistered = true;
            tryRegisterImsListener();
        }
        /* @} */
        // SPRD: modify for bug693456
        mContext.registerReceiver(mModemStateChangeReciver, new IntentFilter(ACTION_MODEM_CHANGE));
        // SPRD: modify for bug723743
        mIsModemReciverRegistered = true;
        /* SPRD: Dual volte signalStrength display for bug 666045. @{ */
        refreshDualVoLTEListener();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
        /* @} */
    }

    /**
     * Stop listening for phone state changes.
     */
    public void unregisterListener() {
        mPhone.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        /* SPRD: Bug 697839 add For VOLTE and VoWiFi icon. @{ */
        try {
             if (mIsImsListenerRegistered) {
                 mIsImsListenerRegistered = false;
                 if (mIImsServiceEx != null) {
                     mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
                 }
             }
             /* SPRD: modify for bug723743 @{ */
             if (mIsImsReceiverRegistered) {
                 mContext.unregisterReceiver(mImsIntentReceiver);
                 mIsImsReceiverRegistered = false;
             }
             if (mIsModemReciverRegistered) {
                 mContext.unregisterReceiver(mModemStateChangeReciver);
                 mIsModemReciverRegistered = false;
             }
             /* @} */
        } catch (RemoteException e) {
             Log.e(mTag, "RemoteException: " + e);
        }
        /* @} */
        mContext.getContentResolver().unregisterContentObserver(mObserver);
        /* SPRD: Dual volte signalStrength display for bug 666045. @{ */
        if (mDualVoLTEListener != null) {
            mPhone.listen(mDualVoLTEListener, PhoneStateListener.LISTEN_NONE);
        }
        mContext.unregisterReceiver(mReceiver);
        /* @} */
    }

    /* SPRD: modify for bug655620 @{ */
    private BroadcastReceiver mModemStateChangeReciver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MODEM_CHANGE.equals(intent.getAction())) {
                String state = intent.getStringExtra(MODEM_STAT);
                Log.d(mTag,
                        "modem start state : " + state + "  modemreset:" + SystemProperties.get(
                                MODEM_RESET_KEY));
                if (MODEM_ASSERT.equals(state)) {
                    if (MODEM_RESET_ENABLE.equals(SystemProperties.get(MODEM_RESET_KEY))) {
                        Log.d(mTag, "modem reset open ");
                        mIsModemResetActive = true;
                        mEnableRefreshSignalStrengths = false;
                        mEnableRefreshServiceState = false;
                        mEnableRefreshDataConnectionState = false;
                        mEnableRefreshVoLteServiceState = false;
                        mEnableRefreshMobileDataIndicator = false;
                        Message msg = new Message();
                        msg.what = MODEM_RESET_MSG;
                        // SPRD: modify for bug655620
                        mHandler.removeMessages(MODEM_RESET_MSG);
                        mHandler.sendMessageDelayed(msg, MODEM_RESET_TIME_OUT);
                    }
                }
            }
        }
    };

    /* SPRD: modify by BUG 798705 @{ */
    private void refreshVolteIndicators(boolean show, int subId, int resId,
                                        SignalCallback callback) {
        if (DEBUG) {
            Log.d(mTag, "refreshVolteIndicators show = " + show
                    + " subId = " + subId + " resId = " + resId);
        }
        mIsVolteIconShow = show;
        mVolteIconForSubId = subId;
        mVolteIconResId = resId;
        if (mIsModemResetActive
                && !mIsVolteIconShow) {
            Log.i(mTag, "modem reset and volte is disable");
            mEnableRefreshVoLteServiceState = false;
            return;
        } else {
            mEnableRefreshVoLteServiceState = true;
        }
        if (mEnableRefreshVoLteServiceState) {
            if (callback != null && callback instanceof VendorSignalCallback) {
                ((VendorSignalCallback) callback).setMobileVolteIndicators(
                        mIsVolteIconShow, mVolteIconForSubId, mVolteIconResId);
            } else {
                mCallbackHandler.setMobileVolteIndicators(mIsVolteIconShow,
                        mVolteIconForSubId,
                        mVolteIconResId);
            }
        }
    }
    /* @} */

    /* SPRD: Bug 826505 add For VoWiFi icon refresh. @{ */
    private void refreshVoWiFiIndicators(boolean show, SignalCallback callback) {
        if (DEBUG) {
            Log.d(mTag, "refreshVoWiFiIndicators show = " + show);
        }
        if (callback != null && callback instanceof VendorSignalCallback) {
            ((VendorSignalCallback) callback).setMobileVoWifiIndicators(show);
        } else {
            mCallbackHandler.setMobileVoWifiIndicators(show);
        }
    }
    /* @} */

    /* SPRD: Bug 697839 add For VOLTE and VoWiFi icon. @{ */
    private BroadcastReceiver mImsIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (ImsManager.ACTION_IMS_SERVICE_DOWN.equals(intent.getAction())) {
                mIsImsListenerRegistered = false;
            }
            tryRegisterImsListener();
        }
    };

    private synchronized void tryRegisterImsListener() {
        if (mIsRegImsChange) {// SPRD: Bug 806908 add For VoWiFi icon
            mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
            if (mIImsServiceEx != null) {
                try{
                    if (!mIsImsListenerRegistered) {
                        mIsImsListenerRegistered = true;
                        mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                    }
                } catch (RemoteException e) {
                    Log.e(mTag, "regiseterforImsException: "+ e);
                }
            }
        }
    }

    private final IImsRegisterListener.Stub mImsUtListenerExBinder =
            new IImsRegisterListener.Stub() {
        public void imsRegisterStateChange(boolean isRegistered) {
            Log.d(mTag, "imsRegisterStateChange. isRegistered: " + isRegistered);
            if (mIsRegImsChange) {// SPRD: Bug 806908 add For VoWiFi icon
                if (DEBUG) {
                    Log.d(mTag, "imsRegisterStateChange - mIsDualVoLTEActive = " +
                            ImsManagerEx.isDualVoLTEActive() +
                            " mIsDualLte = " + ImsManagerEx.isDualLteModem());
                }
                /* mIsVoLteEnable: Main sim card IMS registration status.
                 *     true if main sim card is registered on VoLTE or VoWiFi.
                 */
                if (mIsVoLteEnable != isRegistered) {
                    mIsVoLteEnable = isRegistered;
                }
                /* SPRD: Add for Vowifi in Dual VoLTE. See bug #754083. @{ */
                /* SPRD: modify for bug817050 @{ */
                mImsType = IMS_FEATURE_TYPE_DEFAULT;
                try {
                    mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
                    if (mIImsServiceEx != null) {
                        mImsType = mIImsServiceEx.getCurrentImsFeature();
                    } else {
                        return;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                /* mImsType: Main sim card IMS feature type.
                 *           Ims is not registered if mImsType equals -1.
                 * isVoLTEType: true if mImsType equals 0, which means register on VoLTE.
                 * isVoWiFiType: true if mImsType equals 2  which means register on VoWiFi.
                 */
                boolean isVoWiFiType =
                        mImsType == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
                boolean isVoLTEType =
                        mImsType == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
                Log.d(mTag, "imsRegistered type: " + mImsType);

                // Single Volte situation.
                if (!ImsManagerEx.isDualVoLTEActive()
                        && !ImsManagerEx.isDualLteModem()) {
                    // Single Volte situation, so dual volte registration status must be false.
                    mDualImsRegistered = false;
                    mDualVolteRegistered = false;

                    // SPRD: Bug 826505 add For VoWiFi icon refresh.
                    refreshVoWiFiIndicators(isVoWiFiType, null); // Vowifi icon.
                    /* SPRD: modify for bug655620 @{ */
                    if (mEnableRefreshVoLteServiceState) {
                        /* Single Volte icon show depends on:
                         * mImsType : mImsType must be 0 (isVoLTEType must be true)
                         * mIsVoLteEnable : main sim card IMS registered state.
                         */
                        refreshVolteIndicators(isVoLTEType && mIsVoLteEnable,
                                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                                TelephonyIconsEx.ICON_VOLTE, null);
                    }
                // Dual Volte situation（DSDA and L+L）.
                } else {
                    int phoneId = mSubscriptionInfo.getSimSlotIndex();
                    // defaultDataPhoneId: Main sim card phoneId.
                    int defaultDataPhoneId = SubscriptionManager.from(mContext)
                            .getDefaultDataPhoneId();
                    if (SubscriptionManager.isValidPhoneId(phoneId) &&
                            SubscriptionManager.isValidPhoneId(defaultDataPhoneId)) {
                        mDualImsRegistered = ImsManagerEx.isImsRegisteredForPhone(phoneId);
                        mDualVolteRegistered = ImsManagerEx.isVoLTERegisteredForPhone(phoneId);
                        Log.d(mTag, "DualVoLTE - mDualImsRegistered: " + mDualImsRegistered +
                                " phoneId = " + phoneId +
                                " subId = " + mSubscriptionInfo.getSubscriptionId() +
                                " defaultDataPhoneId = " + defaultDataPhoneId +
                                " mDualVolteRegistered = " + mDualVolteRegistered);
                        /* SPRD: modify for bug655620,826505 @{ */
                        refreshVoWiFiIndicators(isVoWiFiType, null); // Vowifi icon.
                        if (phoneId == defaultDataPhoneId) {
                            /* Dual Volte, main sim card volte icon show depends on:
                             * mImsType : mImsType must be 0 (isVoLTEType must be true)
                             */
                            // SPRD: modify for bug868207
                            refreshVolteIndicators(mDualImsRegistered && isVoLTEType,
                                    mSubscriptionInfo.getSubscriptionId(),
                                    TelephonyIconsEx.ICON_VOLTE, null);
                        } else {
                            /* Dual Volte, vice sim card volte icon show depends on:
                             * isVolteRegistered : ImsManagerEx.isImsRegisteredForPhone(phoneId)
                             *         which means dual volte registration status by phoneId.
                             */
                            refreshVolteIndicators(mDualVolteRegistered,
                                    mSubscriptionInfo.getSubscriptionId(),
                                    TelephonyIconsEx.ICON_VOLTE, null);
                        }
                        /* @} */
                    }
                    notifyListeners();
                }
                /* @} */
                /* @} */
            }
        }
    };

    /* SPRD: modify by BUG 617025 and BUG798705 @{ */
    public void refreshImsIcons(SignalCallback callback) {
        /* SPRD: Add for VoWifi in bug 629758. @{ */
        /* SPRD: Add for Dual VoLTE. See bug #627393,748088. @{ */
        /* SPRD: Dual volte signalStrength display for bug 666045. @{ */
            if (DEBUG) {
              //SPRD: bug 866765
                Log.d(mTag, "refreshImsIcons - mIsDualVoLTEActive = " +
                        ImsManagerEx.isDualVoLTEActive() +
                        " mIsDualLte = " + ImsManagerEx.isDualLteModem() +
                        " mImsType = " + mImsType + " mDualImsRegistered = " +
                        mDualImsRegistered + " mDualVolteRegistered = " + mDualVolteRegistered);
            }
            if (ImsManagerEx.isDualVoLTEActive() || ImsManagerEx.isDualLteModem()) {
                /* SPRD: modify for bug655620,798705 and 847536. @{ */
                int defaultDataPhoneId = SubscriptionManager.from(mContext)
                        .getDefaultDataPhoneId();
                int phoneId = mSubscriptionInfo.getSimSlotIndex();
                boolean isVoLTEType =
                        mImsType == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
                if (SubscriptionManager.isValidPhoneId(phoneId) &&
                        SubscriptionManager.isValidPhoneId(defaultDataPhoneId)) {
                    if (phoneId == defaultDataPhoneId) {
                        //SPRD: bug 866765
                        refreshVolteIndicators(mDualImsRegistered && isVoLTEType,
                                mSubscriptionInfo.getSubscriptionId(),
                                TelephonyIconsEx.ICON_VOLTE, callback);
                    } else {
                        refreshVolteIndicators(mDualVolteRegistered,
                                mSubscriptionInfo.getSubscriptionId(),
                                TelephonyIconsEx.ICON_VOLTE, callback);
                    }
                }
                /* @} */
            } else if (mImsType != ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
                refreshVolteIndicators(mIsVoLteEnable,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                        TelephonyIconsEx.ICON_VOLTE, callback);

            }
    }
    /* @} */

    /* SPRD: Bug 826505 VoWiFi icon refresh when addSignalCallback in NetworkControllerImpl. @{ */
    public void refreshVoWiFiIcons(SignalCallback callback) {
        if (DEBUG) {
            Log.d(mTag, "refreshVoWiFiIcons - mImsType = " + mImsType);
        }
        refreshVoWiFiIndicators(mImsType ==
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI, callback);
    }
    /* @} */

    /* SPRD: modify by BUG 617025 @{ */
    public void refreshMobileDataConnected(SignalCallback callback) {
        if (callback != null && callback instanceof VendorSignalCallback) {
            ((VendorSignalCallback) callback).setMobileDataConnectedIndicators(
                    mCurrentState.dataConnected,
                    mSubscriptionInfo.getSubscriptionId());
        } else {
            mCallbackHandler.setMobileDataConnectedIndicators(
                    mCurrentState.dataConnected,
                    mSubscriptionInfo.getSubscriptionId());
        }
    }
    /* @} */

    protected void finalize() throws Throwable {
        try{
            if (mIsImsListenerRegistered) {
                mIsImsListenerRegistered = false;
                mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
            }
            /* SPRD: modify for bug723743 @{ */
            if (mIsImsReceiverRegistered) {
                mContext.unregisterReceiver(mImsIntentReceiver);
                mIsImsReceiverRegistered = false;
            }
            if (mIsModemReciverRegistered) {
                mContext.unregisterReceiver(mModemStateChangeReciver);
                mIsModemReciverRegistered = false;
            }
            /* @} */
        } catch (RemoteException e) {
            Log.e(mTag, "RemoteException: " + e);
        }
        super.finalize();
    }
    /* @} */

    /**
     * Produce a mapping of data network types to icon groups for simple and quick use in
     * updateTelephony.
     */
    private void mapIconSets() {
        mNetworkToIconLookup.clear();

        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UMTS, TelephonyIcons.THREE_G);

        if (!mConfig.showAtLeast3G) {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyIcons.UNKNOWN);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE, TelephonyIcons.E);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA, TelephonyIcons.ONE_X);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyIcons.ONE_X);

            mDefaultIcons = TelephonyIcons.G;
        } else {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyIcons.THREE_G);
            mDefaultIcons = TelephonyIcons.THREE_G;
        }

        /* SPRD: Bug 699665 add H+ icon. @{ */
        MobileIconGroup hGroup = TelephonyIcons.THREE_G;
        MobileIconGroup hpGroup = TelephonyIcons.THREE_G;
        if (mConfig.hspaDataDistinguishable) {
            hGroup = TelephonyIcons.H;
            hpGroup = TelephonyIconsEx.HP;
        }
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSDPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSUPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPAP, hpGroup);
        /* @} */

        if (mConfig.show4gForLte) {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.FOUR_G);
            if (mConfig.hideLtePlus) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.FOUR_G);
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.FOUR_G_PLUS);
            }
        } else {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.LTE);
            if (mConfig.hideLtePlus) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.LTE);
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.LTE_PLUS);
            }
        }
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_IWLAN, TelephonyIcons.WFC);
    }

    private int getNumLevels() {
        if (mConfig.inflateSignalStrengths) {
            return SignalStrength.NUM_SIGNAL_STRENGTH_BINS + 1;
        }
        return SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
    }

    /* SPRD: Bug 693278 add For Data and Vowifi Tile. @{ */
    @Override
    public int getCurrentIconId() {
        if (mCurrentState.iconGroup == TelephonyIcons.CARRIER_NETWORK_CHANGE) {
            return TelephonyIconsEx.ICON_CARRIER_NETWORK_CHANGE;
            // return SignalDrawable.getCarrierChangeState(getNumLevels());
        } else if (mCurrentState.connected) {
            if (mCurrentState.level <= (getNumLevels() - 1)
                    && mCurrentState.inetCondition == 1) {
                return TelephonyIconsEx.TELEPHONY_SIGNAL_STRENGTH[mCurrentState.level];
            } else {
                return TelephonyIconsEx.ICON_SIGNAL_ZERO;
            }
            /*  return SignalDrawable.getState(mCurrentState.level, getNumLevels(),
                    mCurrentState.inetCondition == 0); */
        } else if (mCurrentState.enabled) {
            return TelephonyIconsEx.ICON_NO_NETWORK;
            //  return SignalDrawable.getEmptyState(getNumLevels());
        } else {
            return 0;
        }
    }
    /* @} */

    @Override
    public int getQsCurrentIconId() {
        if (mCurrentState.airplaneMode) {
            return SignalDrawable.getAirplaneModeState(getNumLevels());
        }

        return getCurrentIconId();
    }

    @Override
    public void notifyListeners(SignalCallback callback) {
        MobileIconGroup icons = getIcons();

        String contentDescription = getStringIfExists(getContentDescription());
        String dataContentDescription = getStringIfExists(icons.mDataContentDescription);
        final boolean dataDisabled = mCurrentState.iconGroup == TelephonyIcons.DATA_DISABLED
                && mCurrentState.userSetup;

        // Show icon in QS when we are connected or data is disabled.
        boolean showDataIcon = mCurrentState.dataConnected || dataDisabled;
        IconState statusIcon = new IconState(mCurrentState.enabled && !mCurrentState.airplaneMode,
                getCurrentIconId(), contentDescription);

        int qsTypeIcon = 0;
        IconState qsIcon = null;
        String description = null;
        // Only send data sim callbacks to QS.
        if (mCurrentState.dataSim) {
            qsTypeIcon = showDataIcon ? icons.mQsDataType : 0;
            qsIcon = new IconState(mCurrentState.enabled
                    && !mCurrentState.isEmergency, getQsCurrentIconId(), contentDescription);
            description = mCurrentState.isEmergency ? null : mCurrentState.networkName;
        }
        boolean activityIn = mCurrentState.dataConnected
                && !mCurrentState.carrierNetworkChangeMode
                && mCurrentState.activityIn;
        boolean activityOut = mCurrentState.dataConnected
                && !mCurrentState.carrierNetworkChangeMode
                && mCurrentState.activityOut;
        showDataIcon &= mCurrentState.isDefault || dataDisabled;
        // SPRD: FEATURE_ALWAYS_SHOW_RAT_ICON - bug691130.
        int typeIcon = (showDataIcon || (mConfig.alwaysShowRAT && hasService())) ?
                icons.mDataType : 0;
        /* SPRD: Distinguish 3G type icons in Orange for bug 688768. @{ */
        if (mShowOrange3GIcon && getOrange3GIcon(mDataNetType) != 0
                && typeIcon != 0) {
            if (mCurrentState.dataConnected) {
                typeIcon = getOrange3GIcon(mDataNetType);
            } else {
                typeIcon = TelephonyIcons.ICON_3G;
            }
        }
        /* @} */
        /* SPRD: Bug 697839 add For VOLTE and VoWiFi icon. @{ */
        if (DEBUG) {
            Log.d(mTag, "notify - mIsDualVoLTEActive = " +
                    ImsManagerEx.isDualVoLTEActive() +
                    " mIsDualLte = " + ImsManagerEx.isDualLteModem());
        }
        /* @} */

        if (mIsModemResetActive) {
            Log.i(mTag, "modem reset so not refresh status bar");
        } else {
        // SPRD: Bug 700018 adjust SIM icons layout.
            mCallbackHandler.setMobileDataConnectedIndicators(mCurrentState.dataConnected,
                    mSubscriptionInfo.getSubscriptionId());
            callback.setMobileDataIndicators(statusIcon, qsIcon, typeIcon, qsTypeIcon,
                    activityIn, activityOut, dataContentDescription, description, icons.mIsWide,
                    mSubscriptionInfo.getSubscriptionId(), mCurrentState.roaming);
            // SPRD: Bug 862096
            refreshImsIcons(mCallbackHandler);
        }
    }

    @Override
    protected MobileState cleanState() {
        return new MobileState();
    }

    private boolean hasService() {
        if (mServiceState != null) {
            // Consider the device to be in service if either voice or data
            // service is available. Some SIM cards are marketed as data-only
            // and do not support voice service, and on these SIM cards, we
            // want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice
            // is not available.
            switch (mServiceState.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private boolean isCdma() {
        return (mSignalStrength != null) && !mSignalStrength.isGsm();
    }

    public boolean isEmergencyOnly() {
        return (mServiceState != null && mServiceState.isEmergencyOnly());
    }

    private boolean isRoaming() {
        // During a carrier change, roaming indications need to be supressed.
        if (isCarrierNetworkChangeActive()) {
            return false;
        }
        if (isCdma() && mServiceState != null) {
            final int iconMode = mServiceState.getCdmaEriIconMode();
            return mServiceState.getCdmaEriIconIndex() != EriInfo.ROAMING_INDICATOR_OFF
                    && (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                    || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH);
        } else {
            return mServiceState != null && mServiceState.getRoaming();
        }
    }

    private boolean isCarrierNetworkChangeActive() {
        return mCurrentState.carrierNetworkChangeMode;
    }

    public void handleBroadcast(Intent intent) {
        String action = intent.getAction();
        if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                    intent.getStringExtra(TelephonyIntents.EXTRA_DATA_SPN),
                    intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
            notifyListenersIfNecessary();
        } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
            updateDataSim();
            notifyListenersIfNecessary();
        }
    }

    private void updateDataSim() {
        int defaultDataSub = mDefaults.getDefaultDataSubId();
        if (SubscriptionManager.isValidSubscriptionId(defaultDataSub)) {
            mCurrentState.dataSim = defaultDataSub == mSubscriptionInfo.getSubscriptionId();
        } else {
            // There doesn't seem to be a data sim selected, however if
            // there isn't a MobileSignalController with dataSim set, then
            // QS won't get any callbacks and will be blank.  Instead
            // lets just assume we are the data sim (which will basically
            // show one at random) in QS until one is selected.  The user
            // should pick one soon after, so we shouldn't be in this state
            // for long.
            mCurrentState.dataSim = true;
        }
    }

    /**
     * Updates the network's name based on incoming spn and plmn.
     */
    void updateNetworkName(boolean showSpn, String spn, String dataSpn,
            boolean showPlmn, String plmn) {
        if (CHATTY) {
            Log.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn
                    + " spn=" + spn + " dataSpn=" + dataSpn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        StringBuilder strData = new StringBuilder();
        if (showPlmn && plmn != null) {
            str.append(plmn);
            strData.append(plmn);
        }
        if (showSpn && spn != null) {
            if (str.length() != 0) {
                str.append(mNetworkNameSeparator);
            }
            str.append(spn);
        }
        if (str.length() != 0) {
            mCurrentState.networkName = str.toString();
        } else {
            mCurrentState.networkName = mNetworkNameDefault;
        }
        if (showSpn && dataSpn != null) {
            if (strData.length() != 0) {
                strData.append(mNetworkNameSeparator);
            }
            strData.append(dataSpn);
        }
        if (strData.length() != 0) {
            mCurrentState.networkNameData = strData.toString();
        } else {
            mCurrentState.networkNameData = mNetworkNameDefault;
        }
    }

    /**
     * Updates the current state based on mServiceState, mSignalStrength, mDataNetType,
     * mDataState, and mSimState.  It should be called any time one of these is updated.
     * This will call listeners if necessary.
     */
    private final void updateTelephony() {
        if (DEBUG) {
            Log.d(mTag, "updateTelephonySignalStrength: hasService=" + hasService()
                    + " ss=" + mSignalStrength);
        }
        if (mEnableRefreshServiceState && mEnableRefreshSignalStrengths) {
            mCurrentState.connected = hasService() && mSignalStrength != null;
            if (mCurrentState.connected) {
                if (!mSignalStrength.isGsm() && mConfig.alwaysShowCdmaRssi) {
                    mCurrentState.level = mSignalStrength.getCdmaLevel();
                } else {
                    mCurrentState.level = mSignalStrength.getLevel();
                }
            }
        }
        if (mEnableRefreshDataConnectionState) {
            if (mNetworkToIconLookup.indexOfKey(mDataNetType) >= 0) {
                mCurrentState.iconGroup = mNetworkToIconLookup.get(mDataNetType);
            } else {
                mCurrentState.iconGroup = mDefaultIcons;
            }
        }
        mCurrentState.dataConnected = mCurrentState.connected
                && mDataState == TelephonyManager.DATA_CONNECTED;

        mCurrentState.roaming = isRoaming();
        if (isCarrierNetworkChangeActive()) {
            mCurrentState.iconGroup = TelephonyIcons.CARRIER_NETWORK_CHANGE;
        // SPRD: FEATURE_ALWAYS_SHOW_RAT_ICON - bug691130 & bug 833682.
        } else if (!mConfig.alwaysShowRAT && isDataDisabled() && mConfig.showDataDisable) {
            mCurrentState.iconGroup = TelephonyIcons.DATA_DISABLED;
        }
        if (isEmergencyOnly() != mCurrentState.isEmergency) {
            mCurrentState.isEmergency = isEmergencyOnly();
            mNetworkController.recalculateEmergency();
        }
        // Fill in the network name if we think we have it.
        if (mEnableRefreshServiceState) {
            if (mCurrentState.networkName == mNetworkNameDefault && mServiceState != null
                    && !TextUtils.isEmpty(mServiceState.getOperatorAlphaShort())) {
                mCurrentState.networkName = mServiceState.getOperatorAlphaShort();
            }
        }

        notifyListenersIfNecessary();
    }

    private boolean isDataDisabled() {
        return !mPhone.getDataEnabled(mSubscriptionInfo.getSubscriptionId());
    }

    /* SPRD: Distinguish 3G type icons in Orange for bug 688768. @{ */
    private int getOrange3GIcon(int dataType) {
        if (dataType == TelephonyManager.NETWORK_TYPE_EVDO_0
                || dataType == TelephonyManager.NETWORK_TYPE_EVDO_A
                || dataType == TelephonyManager.NETWORK_TYPE_EVDO_B
                || dataType == TelephonyManager.NETWORK_TYPE_EHRPD
                || dataType == TelephonyManager.NETWORK_TYPE_UMTS) {
            return TelephonyIcons.ICON_3G;
        } else if (dataType == TelephonyManager.NETWORK_TYPE_HSDPA
                || dataType == TelephonyManager.NETWORK_TYPE_HSUPA
                || dataType == TelephonyManager.NETWORK_TYPE_HSPA) {
            return TelephonyIconsEx.ICON_3G_PLUS;
        } else if (dataType == TelephonyManager.NETWORK_TYPE_HSPAP) {
            return TelephonyIconsEx.ICON_H_PLUS;
        } else {
            return 0;
        }
    }
    /* @} */

    @VisibleForTesting
    void setActivity(int activity) {
        mCurrentState.activityIn = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                || activity == TelephonyManager.DATA_ACTIVITY_IN;
        mCurrentState.activityOut = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                || activity == TelephonyManager.DATA_ACTIVITY_OUT;
        notifyListenersIfNecessary();
    }

    @Override
    public void dump(PrintWriter pw) {
        super.dump(pw);
        pw.println("  mSubscription=" + mSubscriptionInfo + ",");
        pw.println("  mServiceState=" + mServiceState + ",");
        pw.println("  mSignalStrength=" + mSignalStrength + ",");
        pw.println("  mDataState=" + mDataState + ",");
        pw.println("  mDataNetType=" + mDataNetType + ",");
    }

    class MobilePhoneStateListener extends PhoneStateListener {

        // UNISOC: modify for bug887525
        private String[] mMccMncArray;

        public MobilePhoneStateListener(int subId, Looper looper) {
            super(subId, looper);
            // UNISOC: modify for bug887525
            mMccMncArray = mContext.getResources().getStringArray(R.array.mcc_mnc_list);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            /* SPRD: Dual volte signalStrength display for bug 666045. @{ */
            int defaultDataSubId = SubscriptionManager.from(mContext)
                    .getDefaultDataSubscriptionId();
            boolean dualVoLTEState = mIsVoLteBoard && ImsManagerEx.isDualVoLTEActive() &&
                    mDualVolteRegistered;
            if (SubscriptionManager.isValidSubscriptionId(defaultDataSubId) &&
                    mSubscriptionInfo.getSubscriptionId() != defaultDataSubId &&
                    dualVoLTEState && !ImsManagerEx.isDualLteModem()) {
                return;
            }
            /* @} */
            if (DEBUG) {
                Log.d(mTag, "onSignalStrengthsChanged signalStrength=" + signalStrength +
                        ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
            }
            mSignalStrength = signalStrength;
            if (mIsModemResetActive
                    && (signalStrength.getLevel() == SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN)) {
                Log.i(mTag, "modem reset and signal strength is none or unknown");
                mEnableRefreshSignalStrengths = false;
                return;
            } else {
                mEnableRefreshSignalStrengths = true;
            }
            updateTelephony();
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            if (DEBUG) {
                Log.d(mTag, "onServiceStateChanged voiceState=" + state.getVoiceRegState()
                        + " dataState=" + state.getDataRegState());
            }
            mServiceState = state;
            if (state != null) {
                /* SPRD: FEATURE_ALWAYS_SHOW_RAT_ICON - bug691130. @{ */
                if (mConfig.alwaysShowRAT) {
                    mDataNetType = getRegNetworkType(state);
                    if (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE
                            && mServiceState != null
                            && mServiceState.isUsingCarrierAggregation()) {
                        mDataNetType = TelephonyManager.NETWORK_TYPE_LTE_CA;
                    }
                }
            }
            /* @} */
            /* UNISOC: add for bug887525 @{ */
            String mccmnc = TelephonyManager.from(mContext) == null ? ""
                    : TelephonyManager.from(mContext)
                    .getSimOperatorNumeric(mSubscriptionInfo.getSubscriptionId());
            mConfig.alwaysShowRAT = mConfig.alwaysShowRAT && !isContainSpecialMccMnc(mccmnc);
            if (DEBUG) {
                Log.d(mTag, "mConfig.alwaysShowRAT=" + mConfig.alwaysShowRAT);
            }
            /* @} */

            if (mIsModemResetActive
                    && !hasService()) {
                Log.i(mTag, "modem reset and service not ready");
                mEnableRefreshServiceState = false;
                return;
            } else {
                mEnableRefreshServiceState = true;
            }
            updateTelephony();
        }

        /* UNISOC: add for bug887525 @{ */
        private boolean isContainSpecialMccMnc(String mccmnc) {
            for (int i = 0; i < mMccMncArray.length; i++) {
                if (mccmnc.equals(mMccMncArray[i])) {
                    return true;
                }
            }
            return false;
        }
        /* @} */

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) {
                Log.d(mTag, "onDataConnectionStateChanged: state=" + state
                        + " type=" + networkType);
            }
            mDataState = state;
            mDataNetType = networkType;
            /* SPRD: FEATURE_ALWAYS_SHOW_RAT_ICON - bug691130. @{ */
            if (mConfig.alwaysShowRAT
                    && networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                mDataNetType = getRegNetworkType(mServiceState);
            }

            if (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE && mServiceState != null &&
                    mServiceState.isUsingCarrierAggregation()) {
                mDataNetType = TelephonyManager.NETWORK_TYPE_LTE_CA;
            }
            /* @} */
            if (mIsModemResetActive
                    && state != TelephonyManager.DATA_CONNECTED) {
                Log.i(mTag, "modem reset and data connected");
                mEnableRefreshDataConnectionState = false;
                return;
            } else {
                mEnableRefreshDataConnectionState = true;
            }
            updateTelephony();
        }

        @Override
        public void onDataActivity(int direction) {
            if (DEBUG) {
                Log.d(mTag, "onDataActivity: direction=" + direction);
            }
            setActivity(direction);
        }

        @Override
        public void onCarrierNetworkChange(boolean active) {
            if (DEBUG) {
                Log.d(mTag, "onCarrierNetworkChange: active=" + active);
            }
            mCurrentState.carrierNetworkChangeMode = active;

            updateTelephony();
        }
    };

    static class MobileIconGroup extends SignalController.IconGroup {
        final int mDataContentDescription; // mContentDescriptionDataType
        final int mDataType;
        final boolean mIsWide;
        final int mQsDataType;

        public MobileIconGroup(String name, int[][] sbIcons, int[][] qsIcons, int[] contentDesc,
                int sbNullState, int qsNullState, int sbDiscState, int qsDiscState,
                int discContentDesc, int dataContentDesc, int dataType, boolean isWide,
                int qsDataType) {
            super(name, sbIcons, qsIcons, contentDesc, sbNullState, qsNullState, sbDiscState,
                    qsDiscState, discContentDesc);
            mDataContentDescription = dataContentDesc;
            mDataType = dataType;
            mIsWide = isWide;
            mQsDataType = qsDataType;
        }
    }

    static class MobileState extends SignalController.State {
        String networkName;
        String networkNameData;
        boolean dataSim;
        boolean dataConnected;
        boolean isEmergency;
        boolean airplaneMode;
        boolean carrierNetworkChangeMode;
        boolean isDefault;
        boolean userSetup;
        boolean roaming;

        @Override
        public void copyFrom(State s) {
            super.copyFrom(s);
            MobileState state = (MobileState) s;
            dataSim = state.dataSim;
            networkName = state.networkName;
            networkNameData = state.networkNameData;
            dataConnected = state.dataConnected;
            isDefault = state.isDefault;
            isEmergency = state.isEmergency;
            airplaneMode = state.airplaneMode;
            carrierNetworkChangeMode = state.carrierNetworkChangeMode;
            userSetup = state.userSetup;
            roaming = state.roaming;
        }

        @Override
        protected void toString(StringBuilder builder) {
            super.toString(builder);
            builder.append(',');
            builder.append("dataSim=").append(dataSim).append(',');
            builder.append("networkName=").append(networkName).append(',');
            builder.append("networkNameData=").append(networkNameData).append(',');
            builder.append("dataConnected=").append(dataConnected).append(',');
            builder.append("roaming=").append(roaming).append(',');
            builder.append("isDefault=").append(isDefault).append(',');
            builder.append("isEmergency=").append(isEmergency).append(',');
            builder.append("airplaneMode=").append(airplaneMode).append(',');
            builder.append("carrierNetworkChangeMode=").append(carrierNetworkChangeMode)
                    .append(',');
            builder.append("userSetup=").append(userSetup);
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o)
                    && Objects.equals(((MobileState) o).networkName, networkName)
                    && Objects.equals(((MobileState) o).networkNameData, networkNameData)
                    && ((MobileState) o).dataSim == dataSim
                    && ((MobileState) o).dataConnected == dataConnected
                    && ((MobileState) o).isEmergency == isEmergency
                    && ((MobileState) o).airplaneMode == airplaneMode
                    && ((MobileState) o).carrierNetworkChangeMode == carrierNetworkChangeMode
                    && ((MobileState) o).userSetup == userSetup
                    && ((MobileState) o).isDefault == isDefault
                    && ((MobileState) o).roaming == roaming;
        }
    }

    /* SPRD: FEATURE_ALWAYS_SHOW_RAT_ICON - bug691130. @{ */
    private int getRegNetworkType(ServiceState state) {
        int voiceNetworkType = state.getVoiceNetworkType();
        int dataNetworkType = state.getDataNetworkType();

        int retNetworkType =
                (dataNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                ? voiceNetworkType : dataNetworkType;
        return retNetworkType;
    }
    /* @} */

    /* SPRD: Add for bug 747639. @{ */
    private boolean isDualVolteBothRegistered() {
        return (ImsManagerEx.isDualVoLTEActive() || ImsManagerEx.isDualLteModem())
                && ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_ONE)
                && ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_TWO);
    }
    /* @} */

    /* SPRD: Dual volte signalStrength display for bug 666045. @{ */
    private void refreshDualVoLTEListener() {
        if (mDualVoLTEListener != null) {
            mPhone.listen(mDualVoLTEListener, PhoneStateListener.LISTEN_NONE);
        }
        int defaultDataSubId = SubscriptionManager.from(mContext)
                .getDefaultDataSubscriptionId();
        if (SubscriptionManager.isValidSubscriptionId(defaultDataSubId)) {
            mDualVoLTEListener = new MasterPhoneStateListener(defaultDataSubId,
                    mLooper);
            mPhone.listen(mDualVoLTEListener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
    }

    class MasterPhoneStateListener extends PhoneStateListener {
        public MasterPhoneStateListener(int subId, Looper looper) {
            super(subId, looper);
        }

        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            int defaultDataSubId = SubscriptionManager.from(mContext)
                    .getDefaultDataSubscriptionId();
            boolean dualVoLTEState = mIsVoLteBoard && ImsManagerEx.isDualVoLTEActive() &&
                    mDualVolteRegistered;
            if (DEBUG) {
                Log.d(mTag, "MasterPhone defaultDataSubId = " + defaultDataSubId +
                        " dualVoLTEState = " + dualVoLTEState);
            }
            if (SubscriptionManager.isValidSubscriptionId(defaultDataSubId) &&
                    mSubscriptionInfo.getSubscriptionId() != defaultDataSubId &&
                    dualVoLTEState && !ImsManagerEx.isDualLteModem()) {
                if (DEBUG) {
                    Log.d(mTag, "MasterPhone onSignalStrengthsChanged signalStrength=" +
                            signalStrength + ((signalStrength == null) ? "" :
                                    (" level=" + signalStrength.getLevel())));
                }
                mSignalStrength = signalStrength;
                if (mIsModemResetActive
                        && (signalStrength.getLevel() ==
                                SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN)) {
                    Log.i(mTag, "modem reset and signal strength is none or unknown");
                    mEnableRefreshSignalStrengths = false;
                    return;
                } else {
                    mEnableRefreshSignalStrengths = true;
                }
                updateTelephony();
            }
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ((TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED).equals(action)
                    || (TelephonyIntents.ACTION_SIM_STATE_CHANGED).equals(action)) {
                refreshDualVoLTEListener();
            }
        }
    };
    /* @} */
}
