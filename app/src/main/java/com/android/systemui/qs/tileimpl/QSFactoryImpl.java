/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use mHost file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tileimpl;

import android.content.Context;
import android.os.SystemProperties;
import android.os.UserManager;
import android.telephony.TelephonyManagerEx;
import android.util.Log;
import android.os.IPowerManagerEx;
import android.os.PowerManagerEx;
import android.os.ServiceManager;
import android.view.ContextThemeWrapper;

import com.android.systemui.R;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.systemui.plugins.qs.*;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.BatterySaverTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.DataSaverTile;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.NfcTile;
import com.android.systemui.qs.tiles.NightDisplayTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.SuperBatteryTile;
import com.android.systemui.qs.tiles.UserTile;
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.qs.tiles.WorkModeTile;
import com.android.systemui.R;
import com.sprd.systemui.SystemUIPluginUtils;
import com.sprd.systemui.SystemUIAudioProfileUtils;
import com.android.systemui.statusbar.phone.StatusBar;


public class QSFactoryImpl implements QSFactory {

    private static final String TAG = "QSFactory";
    private final QSTileHost mHost;
    /* SPRD: 4G icon not in quicksettings panel after factory reset @{ */
    private static final String MODEM_TYPE = "ro.radio.modemtype";
    private static final String MODEM_TYPE_TL = "tl";
    private static final String MODEM_TYPE_LF = "lf";
    private static final String MODEM_TYPE_L = "l";
    /* @} */

    public QSFactoryImpl(QSTileHost host) {
        mHost = host;
    }

    public QSTile createTile(String tileSpec) {
        Log.w(TAG, "tile tileSpec: " + tileSpec);
        if (tileSpec.equals("wifi")) return new WifiTile(mHost);
        else if (tileSpec.equals("bt")) return new BluetoothTile(mHost);
        else if (tileSpec.equals("cell")) return new CellularTile(mHost);
        else if (tileSpec.equals("dnd")) return new DndTile(mHost);
        else if (tileSpec.equals("inversion")) return new ColorInversionTile(mHost);
        else if (tileSpec.equals("airplane")) return new AirplaneModeTile(mHost);
        else if (tileSpec.equals("work")) return new WorkModeTile(mHost);
        else if (tileSpec.equals("rotation")) return new RotationLockTile(mHost);
        else if (tileSpec.equals("flashlight")) return new FlashlightTile(mHost);
        else if (tileSpec.equals("location")) return new LocationTile(mHost);
        else if (tileSpec.equals("cast")) return new CastTile(mHost);
        else if (tileSpec.equals("hotspot")) return new HotspotTile(mHost);
        else if (tileSpec.equals("user")) return new UserTile(mHost);
         //else if (tileSpec.equals("battery")) return new BatterySaverTile(mHost);
        /*SPRD bug 780822:Super power feature*/
        //else if (tileSpec.equals("battery")) return new BatteryTile(this);
        else if (tileSpec.equals("battery")){
            if(StatusBar.SUPPORT_SUPER_POWER_SAVE){
                Log.w(TAG, "SuperBatteryTile " + tileSpec);
                return new SuperBatteryTile(mHost);
            }else{
                return new BatterySaverTile(mHost);
            }
        }
        else if (tileSpec.equals("saver")) return new DataSaverTile(mHost);
        else if (tileSpec.equals("night")) return new NightDisplayTile(mHost);
        else if (tileSpec.equals("nfc")) return new NfcTile(mHost);
        /* SPRD: add for Bug845301.@{ */
        else if (SystemUIPluginUtils.getInstance(mHost.getContext()) != null
                 && SystemUIPluginUtils.getInstance(mHost.getContext()).showExtraTile(tileSpec)) {
            return SystemUIPluginUtils.getInstance(mHost.getContext())
                    .createExtraTile(mHost, tileSpec);
        }
        /* @} */
        /*SPRD bug 692442:New feature for audioprofile{@*/
        else if (tileSpec.equals("audioprofile") && SystemUIAudioProfileUtils.getInstance().isSupportAudioProfileTile()){
            return SystemUIAudioProfileUtils.getInstance().createAudioProfileTile(mHost);
        }
        /*@}*/
        // Intent tiles.
        else if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(mHost, tileSpec);
        else if (tileSpec.startsWith(CustomTile.PREFIX)) return CustomTile.create(mHost, tileSpec);
        else {
            Log.w(TAG, "Bad tile spec: " + tileSpec);
            return null;
        }
    }

    @Override
    public QSTileView createTileView(QSTile tile, boolean collapsedView) {
        Context context = new ContextThemeWrapper(mHost.getContext(), R.style.qs_theme);
        QSIconView icon = tile.createTileView(context);
        if (collapsedView) {
            return new QSTileBaseView(context, icon, collapsedView);
        } else {
            return new com.android.systemui.qs.tileimpl.QSTileView(context, icon);
        }
    }

    /* SPRD: 4G icon not in quicksetting panel after factory reset@{ */
    private boolean isDeviceSupportLte() {
        /* SPRD: Bug792842 not support lte for pike2 @{
        String modeType = SystemProperties.get(MODEM_TYPE, "");
        Log.d(TAG, "getModemType: modemType=" + modeType);
        if (MODEM_TYPE_TL.equals(modeType) || MODEM_TYPE_LF.equals(modeType)
                || MODEM_TYPE_L.equals(modeType)) {
            return true;
        } else {
            return false;
        } */

        return TelephonyManagerEx.isDeviceSupportLte();
    }
    /* @}*/
    /* @} */
}
