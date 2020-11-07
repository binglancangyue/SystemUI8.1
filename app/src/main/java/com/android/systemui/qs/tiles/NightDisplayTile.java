/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.ContentResolver;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.widget.Switch;

import com.android.internal.app.NightDisplayController;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import android.graphics.drawable.Drawable;

public class NightDisplayTile extends QSTileImpl<BooleanState>
        implements NightDisplayController.Callback {

    private NightDisplayController mController;
    private boolean mIsListening;
    private static final String SPRD_DISPLAY_COLOR_TEMPERATURE_MODE = "sprd_display_color_temperature_mode";
    private static final String SPRD_DISPLAY_COLOR_TEMPERATURE_MODE_BACKUP = "sprd_display_color_temperature_mode_backup";

    public NightDisplayTile(QSHost host) {
        super(host);
        mController = new NightDisplayController(mContext, ActivityManager.getCurrentUser());
    }

    @Override
    public boolean isAvailable() {
        return NightDisplayController.isAvailable(mContext);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        final boolean activated = !mState.value;
        if (activated) {
            setDefaultDisplayColorTemperature();
            mController.setActivated(activated);
        } else {
            mController.setActivated(activated);
            setBackUpDisplayColorTemperature();
        }

    }

    public void setDefaultDisplayColorTemperature() {
        final ContentResolver cr = mContext.getContentResolver();
        int mDisplayColorTemperature = Settings.System.getIntForUser(cr,
            SPRD_DISPLAY_COLOR_TEMPERATURE_MODE, 0, ActivityManager.getCurrentUser());
        Settings.System.putIntForUser(cr, SPRD_DISPLAY_COLOR_TEMPERATURE_MODE_BACKUP,
            mDisplayColorTemperature, ActivityManager.getCurrentUser());
        Settings.System.putIntForUser(cr, SPRD_DISPLAY_COLOR_TEMPERATURE_MODE, 0, ActivityManager.getCurrentUser());
    }

    public void setBackUpDisplayColorTemperature() {
        final ContentResolver cr = mContext.getContentResolver();
        int mBackUpColorTemperature = Settings.System.getIntForUser(cr, SPRD_DISPLAY_COLOR_TEMPERATURE_MODE_BACKUP, 0, ActivityManager.getCurrentUser());
        Settings.System.putIntForUser(cr, SPRD_DISPLAY_COLOR_TEMPERATURE_MODE, mBackUpColorTemperature, ActivityManager.getCurrentUser());
    }
    @Override
    protected void handleUserSwitch(int newUserId) {
        // Stop listening to the old controller.
        if (mIsListening) {
            mController.setListener(null);
        }

        // Make a new controller for the new user.
        mController = new NightDisplayController(mContext, newUserId);
        if (mIsListening) {
            mController.setListener(this);
        }

        super.handleUserSwitch(newUserId);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean isActivated = mController.isActivated();

        state.value = isActivated;
        state.label = state.contentDescription =
                mContext.getString(R.string.quick_settings_night_display_label);
        //state.icon = ResourceIcon.get(R.drawable.ic_qs_night_display_on);
        state.icon = ResourceIcon.get(state.value ? R.drawable.ic_qs_nightmode_enabled : R.drawable.ic_qs_nightmode_disabled);
        state.expandedAccessibilityClassName = Switch.class.getName();
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_NIGHT_DISPLAY;
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS);
    }

    @Override
    protected void handleSetListening(boolean listening) {
        mIsListening = listening;
        if (listening) {
            mController.setListener(this);
            refreshState();
        } else {
            mController.setListener(null);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_night_display_label);
    }

    @Override
    public void onActivated(boolean activated) {
        refreshState();
    }
}
