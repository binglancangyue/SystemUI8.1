package com.sprd.keyguard;

import android.app.AddonManager;
import android.content.Context;
import android.content.res.Resources;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.keyguard.R;
import com.android.internal.telephony.TeleUtils;

public class KeyguardPluginsHelper {
    static KeyguardPluginsHelper mInstance;
    public static final String TAG = "KeyguardPluginsHelper";
    /* SPRD: modify by BUG 540847 @{ */
    protected String RAT_4G = "4G";
    protected String RAT_3G = "3G";
    protected String RAT_2G = "2G";
    /* @} */
    /* SPRD: modify by BUG 748782 @{ */
    private static final String UPDATE_OPERATOR_PLMN = "operator";
    private static final String UPDATE_OPERATOR_SPN = "spn";
    /* @} */

    public KeyguardPluginsHelper() {
    }

    public static KeyguardPluginsHelper getInstance() {
        if (mInstance != null)
            return mInstance;
        mInstance = (KeyguardPluginsHelper) AddonManager.getDefault()
                .getAddon(R.string.plugin_keyguard_operator, KeyguardPluginsHelper.class);
        return mInstance;
    }

    public boolean makeEmergencyInvisible() {
        return false;
    }

    public CharSequence parseOperatorName(Context context, ServiceState state, CharSequence operator) {
        StringBuilder relOperatorName = new StringBuilder();
        String separator = context.getResources().getString(
                com.android.internal.R.string.kg_text_message_separator);
        if (operator.toString().contains(separator)) {
            String[] operators = operator.toString().split(separator);
            // SPRD:fix for bug 748782
            operators = removeDup(operators);
            for (int i = 0; i < operators.length; i++) {
                relOperatorName.append(appendRatToNetworkName(context,
                        state, operators[i])).append(separator);
            }
            return relOperatorName.toString().subSequence(0, relOperatorName.length()-2);
        } else {
            relOperatorName.append(appendRatToNetworkName(context,
                    state, operator));
            return relOperatorName.toString();
        }
    }

    /* SPRD:fix for bug 748782 @{ */
    public String [] removeDup (String[] operators) {
        if (operators.length == 2) {
            String plmn = operators[0];
            String spn = operators[1];
            Log.d(TAG, "plmn before = " + plmn + " spn = " + spn);
            if (plmn != null && spn != null) {
                plmn = TeleUtils.updateOperator(plmn, UPDATE_OPERATOR_PLMN);
                spn = TeleUtils.updateOperator(spn, UPDATE_OPERATOR_SPN);
                Log.d(TAG, "plmn after= " + plmn + " spn = " + spn);
                if (plmn.equals(spn)) {
                    String plmns [] = new String[1];
                    plmns[0] = plmn;
                    return plmns;
                }
            }
        }
        return operators;
    }
    /* @} */

    public CharSequence appendRatToNetworkName(Context context, ServiceState state,
            CharSequence operator) {
        CharSequence operatorName = TeleUtils.updateOperator(operator.toString(),
                "operator");
        /* SPRD: modify by BUG 601753 @{ */
        String emergencyCall = Resources.getSystem()
                .getText(com.android.internal.R.string.emergency_calls_only).toString();
        String noService = Resources.getSystem()
                .getText(com.android.internal.R.string.lockscreen_carrier_default).toString();

        /* SPRD: modify by BUG 633103 & Bug645566 @{ */
        if (state != null && state.getOperatorAlphaShort() != null
                && (operatorName.toString().equals(emergencyCall)
                || operatorName.toString().equals(noService)) && hasService(state)) {
            operatorName = TeleUtils.updateOperator(state.getOperatorAlphaShort(),
                    "operator");
            Log.d(TAG,"refresh operator name in service : " + operatorName);
        }
        /* @} */
        if (context == null || state == null
            || operatorName.equals(emergencyCall)
            || operatorName.equals(noService) ) return operatorName;
        /* @} */

        boolean boolAppendRat = context.getResources().getBoolean(
                R.bool.config_show_rat_append_operator);

        if (!boolAppendRat) {
            return operatorName;
        }

        /* SPRD: add for BUG 536878 @{ */
        if (operatorName != null && operatorName.toString().matches(".*[2-4]G$")) {
            return operatorName;
        }
        /* @} */

        // SPRD: VoWifi feature
        if (operatorName != null && operatorName.toString().endsWith("WiFiCall")) {
            return operatorName;
        }

        if (state.getDataRegState() == ServiceState.STATE_IN_SERVICE
                || state.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            int voiceNetType = state.getVoiceNetworkType();
            int dataNetType = state.getDataNetworkType();
            int chosenNetType = ((dataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    ? voiceNetType : dataNetType);
            TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            int ratInt = tm.getNetworkClass(chosenNetType);
            String networktypeString = getNetworkTypeToString(context, ratInt, state);
            operatorName = new StringBuilder().append(operatorName).append(" ")
                    .append(networktypeString);
            return operatorName;
        }
        return operatorName;
    }

    protected String getNetworkTypeToString(Context context, int ratInt, ServiceState state) {
        String ratClassName = "";
        switch (ratInt) {
            case TelephonyManager.NETWORK_CLASS_2_G:
                boolean showRat2G = context.getResources().getBoolean(
                        R.bool.config_show_2g);
                Log.d(TAG, "showRat2G : " + showRat2G);
                ratClassName = showRat2G ? RAT_2G : "";
                break;
            case TelephonyManager.NETWORK_CLASS_3_G:
                Log.d(TAG, "showRat3G : " + show3G(state));
                ratClassName = show3G(state) ? RAT_3G : "";
                break;
            case TelephonyManager.NETWORK_CLASS_4_G:
                boolean showRat4g = context.getResources().getBoolean(
                        R.bool.config_show_4g);
                Log.d(TAG, "showRat4g : " + showRat4g);
                ratClassName = showRat4g ? RAT_4G : "";
                break;
        }
        return ratClassName;
    }

    /* SPRD: modify by BUG 522715 @{ */
    protected boolean show3G(ServiceState state) {
        return true;
    }
    /* @} */

    /* SPRD: modify by BUG 633103 @{ */
    private boolean hasService(ServiceState ss) {
        if (ss != null) {
            switch (ss.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return ss.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }
    /* @} */

}
