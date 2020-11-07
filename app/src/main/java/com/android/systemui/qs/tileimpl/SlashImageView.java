/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
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

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.ImageView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.plugins.qs.QSTile.SlashState;
import com.android.systemui.qs.SlashDrawable;
import com.android.systemui.statusbar.phone.StatusBar;

public class SlashImageView extends ImageView {
    /*SPRD bug 814301&819675 :add control of whether refresh statusbar icons @{ */
    private static final boolean DEBUG_NOT_REFRESH = StatusBar.DEBUG_NOT_REFRESH;
    private static final String TAG_NOT_REFRESH = StatusBar.TAG_NOT_REFRESH;

    @VisibleForTesting
    protected SlashDrawable mSlash;
    private boolean mAnimationEnabled = true;

    public SlashImageView(Context context) {
        super(context);
    }

    protected SlashDrawable getSlash() {
        return mSlash;
    }

    protected void setSlash(SlashDrawable slash) {
        mSlash = slash;
    }

    protected void ensureSlashDrawable() {
        if (mSlash == null) {
            mSlash = new SlashDrawable(getDrawable());
            mSlash.setAnimationEnabled(mAnimationEnabled);
            super.setImageDrawable(mSlash);
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        if (drawable == null) {
            mSlash = null;
            super.setImageDrawable(null);
        } else if (mSlash == null) {
            setImageLevel(drawable.getLevel());
            super.setImageDrawable(drawable);
        } else {
            mSlash.setAnimationEnabled(mAnimationEnabled);
            mSlash.setDrawable(drawable);
        }
    }

    @Override
    public void invalidate() {
        if (!shouldRefresh && StatusBar.ALLOW_NOT_REFRESH) {
            if (DEBUG_NOT_REFRESH) Log.d(TAG_NOT_REFRESH, "StatusBarIconView no necessary to invalidate");
            return;
        }
        super.invalidate();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (StatusBar.ALLOW_NOT_REFRESH) {
            getContext().registerReceiver(mUpdateStatuaBarVisibleReceiver, new IntentFilter("statusbar visibility changed"));
        }
        shouldRefresh = StatusBar.getStatusBarVisibility();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (StatusBar.ALLOW_NOT_REFRESH)
            getContext().unregisterReceiver(mUpdateStatuaBarVisibleReceiver);
    }

    private boolean shouldRefresh = true;
    private BroadcastReceiver mUpdateStatuaBarVisibleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            boolean statusBarVisible = intent.getBooleanExtra("visible", true);
            if (shouldRefresh != statusBarVisible) {
                if (statusBarVisible) invalidate();
                shouldRefresh = statusBarVisible;
            }
            if (DEBUG_NOT_REFRESH) android.util.Log.d(TAG_NOT_REFRESH, "StatusBarIconView-onSystemUiVisibilityChange: statusBarVisible = " + statusBarVisible);
        }
    };
    /* @} SPRD bug 814301&819675 :add control of whether refresh statusbar icons*/

    protected void setImageViewDrawable(SlashDrawable slash) {
        super.setImageDrawable(slash);
    }

    public void setAnimationEnabled(boolean enabled) {
        mAnimationEnabled = enabled;
    }

    public boolean getAnimationEnabled() {
        return mAnimationEnabled;
    }

    private void setSlashState(@NonNull SlashState slashState) {
        ensureSlashDrawable();
        mSlash.setRotation(slashState.rotation);
        mSlash.setSlashed(slashState.isSlashed);
    }

    public void setState(@Nullable SlashState state, @Nullable Drawable drawable) {
        try {
            if (state != null) {
                setImageDrawable(drawable);
                setSlashState(state);
            } else {
                mSlash = null;
                setImageDrawable(drawable);
            }
        } catch (Exception e){

        }

    }
}
