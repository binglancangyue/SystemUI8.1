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

package com.android.systemui.statusbar.phone;

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.DrawableRes;
import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.DockedStackExistsListener;
import com.android.systemui.R;
import com.android.systemui.RecentsComponent;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.PluginManager;
import com.android.systemui.plugins.statusbar.phone.NavGesture;
import com.android.systemui.plugins.statusbar.phone.NavGesture.GestureHelper;
import com.android.systemui.stackdivider.Divider;
import com.android.systemui.statusbar.activity.CustomValue;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.ButtonDispatcher;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;
import com.android.systemui.statusbar.phone.NavigationBarGestureHelper;
import com.android.systemui.statusbar.phone.NavigationBarFrame;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonDrawable;
import com.sprd.systemui.SystemUIDynaNavigationBarUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NavigationBarView extends FrameLayout implements PluginListener<NavGesture> {
    final static boolean DEBUG = false;
    final static String TAG = "StatusBar/NavBarView";

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    final static boolean ALTERNATE_CAR_MODE_UI = false;

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    boolean mVertical;
    private int mCurrentRotation = -1;

    boolean mShowMenu;
    boolean mShowAccessibilityButton;
    boolean mLongClickableAccessibilityButton;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private KeyButtonDrawable mBackIcon, mBackLandIcon, mBackAltIcon, mBackAltLandIcon;
    private KeyButtonDrawable mBackCarModeIcon, mBackLandCarModeIcon;
    private KeyButtonDrawable mBackAltCarModeIcon, mBackAltLandCarModeIcon;
    private KeyButtonDrawable mHomeDefaultIcon, mHomeCarModeIcon;
    private KeyButtonDrawable mRecentIcon;
    //by lym start
    private KeyButtonDrawable mVoiceIcon;
    private KeyButtonDrawable mDRVIcon;
    private KeyButtonDrawable mDRVBackIcon;
    private KeyButtonDrawable mSettingsIcon;
    private List<ImageView> imageViews = new ArrayList<>();

    //end
    private KeyButtonDrawable mDockedIcon;
    private KeyButtonDrawable mImeIcon;
    private KeyButtonDrawable mMenuIcon;
    private KeyButtonDrawable mAccessibilityIcon;

    private GestureHelper mGestureHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private OnVerticalChangedListener mOnVerticalChangedListener;
    private boolean mLayoutTransitionsEnabled = true;
    private boolean mWakeAndUnlocking;
    private boolean mUseCarModeUi = false;
    private boolean mInCarMode = false;
    private boolean mDockedStackExists;

    private final SparseArray<ButtonDispatcher> mButtonDispatchers = new SparseArray<>();
    private Configuration mConfiguration;

    private NavigationBarInflaterView mNavigationInflaterView;
    private RecentsComponent mRecentsComponent;
    private Divider mDivider;

    /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
    private KeyButtonDrawable mHideIcon;
    private KeyButtonDrawable mPullDownIcon;
    private KeyButtonDrawable mPullUpIcon;
    private StatusBar mStatusBar = null;
    private boolean mSupportDynamicBar = false;
    /* @} */

    private ImageView home;
    private ImageView back;
    private ImageView voice;
    private ImageView dvr;
    private ImageView settings;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                                    View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = true;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = true;
                mStartDelay = transition.getStartDelay(transitionType);
                mDuration = transition.getDuration(transitionType);
                mInterpolator = transition.getInterpolator(transitionType);
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                                  View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = false;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = false;
            }
        }

        public void onBackAltCleared() {
            ButtonDispatcher backButton = getBackButton();

            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && backButton.getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(backButton, "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    private final OnClickListener mImeSwitcherClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            mContext.getSystemService(InputMethodManager.class)
                    .showInputMethodPicker(true /* showAuxiliarySubtypes */);
        }
    };

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = getCurrentView().getWidth();
                    final int vh = getCurrentView().getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                                "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                                how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
        mSupportDynamicBar = "0".equals(android.os.SystemProperties.get("qemu.hw.mainkeys"))
                && SystemUIDynaNavigationBarUtils.getInstance(mContext).isSupportDynaNaviBar();

        mDisplay = ((WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        mVertical = false;
        mShowMenu = false;

        mShowAccessibilityButton = false;
        mLongClickableAccessibilityButton = false;

        mConfiguration = new Configuration();
        mConfiguration.updateFrom(context.getResources().getConfiguration());

        mBarTransitions = new NavigationBarTransitions(this);

        mButtonDispatchers.put(R.id.back, new ButtonDispatcher(R.id.back));
        mButtonDispatchers.put(R.id.home, new ButtonDispatcher(R.id.home));
        mButtonDispatchers.put(R.id.recent_apps, new ButtonDispatcher(R.id.recent_apps));
        mButtonDispatchers.put(R.id.menu, new ButtonDispatcher(R.id.menu));
        mButtonDispatchers.put(R.id.ime_switcher, new ButtonDispatcher(R.id.ime_switcher));
        mButtonDispatchers.put(R.id.accessibility_button,
                new ButtonDispatcher(R.id.accessibility_button));
        //by lym start
        mButtonDispatchers.put(R.id.voice, new ButtonDispatcher(R.id.voice));
        mButtonDispatchers.put(R.id.dvr, new ButtonDispatcher(R.id.dvr));
        mButtonDispatchers.put(R.id.dvr_back, new ButtonDispatcher(R.id.dvr_back));
        mButtonDispatchers.put(R.id.settings, new ButtonDispatcher(R.id.settings));
        //end
        if (mSupportDynamicBar) {
            mStatusBar = SysUiServiceProvider.getComponent(getContext(), StatusBar.class);
            mButtonDispatchers.put(R.id.hide, new ButtonDispatcher(R.id.hide));
            mButtonDispatchers.put(R.id.pull, new ButtonDispatcher(R.id.pull));
        }
        /* UNISOC: Bug 921818 */
        updateIcons(context, Configuration.EMPTY, mConfiguration);
        /* @} */
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public LightBarTransitionsController getLightTransitionsController() {
        return mBarTransitions.getLightTransitionsController();
    }

    public void setComponents(RecentsComponent recentsComponent, Divider divider) {
        mRecentsComponent = recentsComponent;
        mDivider = divider;
        if (mGestureHelper instanceof NavigationBarGestureHelper) {
            ((NavigationBarGestureHelper) mGestureHelper).setComponents(
                    recentsComponent, divider, this);
        }
    }

    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mGestureHelper.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mGestureHelper.onInterceptTouchEvent(event);
    }

    public void abortCurrentGesture() {
        getHomeButton().abortCurrentGesture();
    }

    private H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View[] getAllViews() {
        return mRotatedViews;
    }

    public ButtonDispatcher getRecentsButton() {
        return mButtonDispatchers.get(R.id.recent_apps);
    }

    public ButtonDispatcher getMenuButton() {
        return mButtonDispatchers.get(R.id.menu);
    }

    public ButtonDispatcher getBackButton() {
        return mButtonDispatchers.get(R.id.back);
    }

    public ButtonDispatcher getHomeButton() {
        return mButtonDispatchers.get(R.id.home);
    }

    public ButtonDispatcher getImeSwitchButton() {
        return mButtonDispatchers.get(R.id.ime_switcher);
    }

    public ButtonDispatcher getAccessibilityButton() {
        return mButtonDispatchers.get(R.id.accessibility_button);
    }

    //by lym start
    public ButtonDispatcher getVoiceButton() {
        return mButtonDispatchers.get(R.id.voice);
    }

    public void showIndicatorBar(int index) {
//        if (CustomValue.SCREEN_3) {
//            return;
//        }
//        if (home == null) {
//            home = findViewById(R.id.iv_home_indicator_bar);
//            back = findViewById(R.id.iv_back_indicator_bar);
//            voice = findViewById(R.id.iv_voice_indicator_bar);
//            dvr = findViewById(R.id.iv_dvr_indicator_bar);
//            settings = findViewById(R.id.iv_settings_indicator_bar);
//        }
//        hideIndicatorBar();
//        switch (index) {
//            case 0:
//                home.setVisibility(View.VISIBLE);
//                break;
//            case 1:
//                back.setVisibility(View.VISIBLE);
//                break;
//            case 2:
//                voice.setVisibility(View.VISIBLE);
//                break;
//            case 3:
//                dvr.setVisibility(View.VISIBLE);
//                break;
//            default:
//                settings.setVisibility(View.VISIBLE);
//                break;
//        }
    }

    private void hideIndicatorBar() {
            home.setVisibility(View.GONE);
            back.setVisibility(View.GONE);
            voice.setVisibility(View.GONE);
            dvr.setVisibility(View.GONE);
            settings.setVisibility(View.GONE);

//        for (ImageView imageView : imageViews) {
//            if (imageView != null) {
//                imageView.setVisibility(View.GONE);
//            }
//        }
    }

    public ButtonDispatcher getDRVButton() {
        return mButtonDispatchers.get(R.id.dvr);
    }

    public ButtonDispatcher getDVRBackButton() {
        return mButtonDispatchers.get(R.id.dvr_back);
    }

    public ButtonDispatcher getSettingsButton() {
        return mButtonDispatchers.get(R.id.settings);
    }
    //end

    /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
    public ButtonDispatcher getHideSwitchButton() {
        return mButtonDispatchers.get(R.id.hide);
    }

    public ButtonDispatcher getPullSwitchButton() {
        return mButtonDispatchers.get(R.id.pull);
    }
    /* @} */

    public SparseArray<ButtonDispatcher> getButtonDispatchers() {
        return mButtonDispatchers;
    }

    private void updateCarModeIcons(Context ctx) {
        mBackCarModeIcon = getDrawable(ctx,
                R.drawable.ic_sysbar_back_carmode, R.drawable.ic_sysbar_back_carmode);
        mBackLandCarModeIcon = mBackCarModeIcon;
        mBackAltCarModeIcon = getDrawable(ctx,
                R.drawable.ic_sysbar_back_ime_carmode, R.drawable.ic_sysbar_back_ime_carmode);
        mBackAltLandCarModeIcon = mBackAltCarModeIcon;
        mHomeCarModeIcon = getDrawable(ctx,
                R.drawable.ic_sysbar_home_carmode, R.drawable.ic_sysbar_home_carmode);
    }

    private void updateIcons(Context ctx, Configuration oldConfig, Configuration newConfig) {
        if (oldConfig.orientation != newConfig.orientation
                || oldConfig.densityDpi != newConfig.densityDpi) {
            mDockedIcon = getDrawable(ctx,
                    R.drawable.ic_sysbar_docked, R.drawable.ic_sysbar_docked_dark);
        }
        if (oldConfig.densityDpi != newConfig.densityDpi
                || oldConfig.getLayoutDirection() != newConfig.getLayoutDirection()) {
            //by lym start
            int index = CustomValue.ICON_TYPE;
            mBackIcon = getDrawable(ctx, CustomValue.BACK_ICONS[index], CustomValue.BACK_ICONS[index]);
            mBackLandIcon = mBackIcon;
            mBackAltIcon = getDrawable(ctx,
                    CustomValue.BACK_ICONS[index], CustomValue.BACK_ICONS[index]);
            mBackAltLandIcon = mBackAltIcon;
            mHomeDefaultIcon = getDrawable(ctx,
                    CustomValue.HOME_ICONS[index], CustomValue.HOME_ICONS[index]);

            mRecentIcon = getDrawable(ctx,
                    R.drawable.ic_sysbar_recent, R.drawable.ic_sysbar_recent_dark);
            mMenuIcon = getDrawable(ctx, R.drawable.ic_sysbar_menu, R.drawable.ic_sysbar_menu_dark);
            mVoiceIcon = getDrawable(ctx,
                    CustomValue.VOICE_ICONS[index], CustomValue.VOICE_ICONS[index]);
            mDRVIcon = getDrawable(ctx,
                    CustomValue.FRONT_CAMERA_ICONS[index], CustomValue.FRONT_CAMERA_ICONS[index]);
            mDRVBackIcon = getDrawable(ctx,
                    CustomValue.BACK_CAMERA_ICONS[index], CustomValue.BACK_CAMERA_ICONS[index]);
            mSettingsIcon = getDrawable(ctx,
                    CustomValue.SETTINGS_ICONS[index], CustomValue.SETTINGS_ICONS[index]);
            //end

            /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
            if (mSupportDynamicBar) {
                mHideIcon = getDrawable(ctx, R.drawable.ic_sysbar_hide, R.drawable.ic_sysbar_hide_dark);
                mPullDownIcon = getDrawable(ctx, R.drawable.ic_sysbar_pull_down, R.drawable.ic_sysbar_pull_down_dark);
                mPullUpIcon = getDrawable(ctx, R.drawable.ic_sysbar_pull_up, R.drawable.ic_sysbar_pull_up_dark);
            }
            /* @} */

            mAccessibilityIcon = getDrawable(ctx, R.drawable.ic_sysbar_accessibility_button,
                    R.drawable.ic_sysbar_accessibility_button_dark);

            int dualToneDarkTheme = Utils.getThemeAttr(ctx, R.attr.darkIconTheme);
            int dualToneLightTheme = Utils.getThemeAttr(ctx, R.attr.lightIconTheme);
            Context darkContext = new ContextThemeWrapper(ctx, dualToneDarkTheme);
            Context lightContext = new ContextThemeWrapper(ctx, dualToneLightTheme);
            mImeIcon = getDrawable(darkContext, lightContext,
                    R.drawable.ic_ime_switcher_default, R.drawable.ic_ime_switcher_default);

            if (ALTERNATE_CAR_MODE_UI) {
                updateCarModeIcons(ctx);
            }
        }
        if (mSupportDynamicBar && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(TAG, "updateIcons:orientation=LANDSCAPE");
            int rotation = Surface.ROTATION_0;
            rotation = mDisplay.getRotation();
            Log.d(TAG, "rotation= " + rotation);
            if (rotation == Surface.ROTATION_90) {
                Log.d(TAG, "rotation=Surface.ROTATION_90 ");
                mHideIcon = getDrawable(ctx, R.drawable.ic_sysbar_hide_land, R.drawable.ic_sysbar_hide_land_dark);
            } else if (rotation == Surface.ROTATION_270) {
                Log.d(TAG, "rotation=Surface.ROTATION_270 ");
                mHideIcon = getDrawable(ctx, R.drawable.ic_sysbar_hide_land_270, R.drawable.ic_sysbar_hide_land_270_dark);
            }
            updataNavigationBar();
        } else {
            mHideIcon = getDrawable(ctx, R.drawable.ic_sysbar_hide, R.drawable.ic_sysbar_hide_dark);
        }
    }

    private KeyButtonDrawable getDrawable(Context ctx, @DrawableRes int lightIcon,
                                          @DrawableRes int darkIcon) {
        return getDrawable(ctx, ctx, lightIcon, darkIcon);
    }

    private KeyButtonDrawable getDrawable(Context darkContext, Context lightContext,
                                          @DrawableRes int lightIcon, @DrawableRes int darkIcon) {
        return KeyButtonDrawable.create(lightContext.getDrawable(lightIcon),
                darkContext.getDrawable(darkIcon));
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        // Reload all the icons
        updateIcons(getContext(), Configuration.EMPTY, mConfiguration);

        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn() {
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    private KeyButtonDrawable getBackIconWithAlt(boolean carMode, boolean landscape) {
        return landscape
                ? carMode ? mBackAltLandCarModeIcon : mBackAltLandIcon
                : carMode ? mBackAltCarModeIcon : mBackAltIcon;
    }

    private KeyButtonDrawable getBackIcon(boolean carMode, boolean landscape) {
        return landscape
                ? carMode ? mBackLandCarModeIcon : mBackLandIcon
                : carMode ? mBackCarModeIcon : mBackIcon;
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !backAlt) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(getContext(),
                    "Navigation icon hints = " + hints,
                    500).show();
        }

        mNavigationIconHints = hints;

        // We have to replace or restore the back and home button icons when exiting or entering
        // carmode, respectively. Recents are not available in CarMode in nav bar so change
        // to recent icon is not required.
        KeyButtonDrawable backIcon = (backAlt)
                ? getBackIconWithAlt(mUseCarModeUi, mVertical)
                : getBackIcon(mUseCarModeUi, mVertical);

        getBackButton().setImageDrawable(backIcon);

        updateRecentsIcon();

        if (mUseCarModeUi) {
            getHomeButton().setImageDrawable(mHomeCarModeIcon);
        } else {
            getHomeButton().setImageDrawable(mHomeDefaultIcon);
        }

        // The Accessibility button always overrides the appearance of the IME switcher
        final boolean showImeButton =
                !mShowAccessibilityButton && ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN)
                        != 0);
        getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);
        getImeSwitchButton().setImageDrawable(mImeIcon);

        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);
        getMenuButton().setImageDrawable(mMenuIcon);

        setAccessibilityButtonState(mShowAccessibilityButton, mLongClickableAccessibilityButton);
        getAccessibilityButton().setImageDrawable(mAccessibilityIcon);

        setDisabledFlags(mDisabledFlags, true);
        //by lym start
        getVoiceButton().setImageDrawable(mVoiceIcon);
        getDRVButton().setImageDrawable(mDRVIcon);
        getDVRBackButton().setImageDrawable(mDRVBackIcon);
        getSettingsButton().setImageDrawable(mSettingsIcon);
        //end
        mBarTransitions.reapplyDarkIntensity();
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);

        // Always disable recents when alternate car mode UI is active.
        boolean disableRecent = mUseCarModeUi
                || ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);

        ViewGroup navButtons = (ViewGroup) getCurrentView().findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
            }
        }
        if (inLockTask() && disableRecent && !disableHome) {
            // Don't hide recents when in lock task, it is used for exiting.
            // Unless home is hidden, then in DPM locked mode and no exit available.
            disableRecent = false;
        }
        /*SPRD bug 721324:Disable hide and pull button while back\home\recent is invisible.*/
        Log.d(TAG, "setDisabledFlags disableBack=" + disableBack + ",disableHome=" + disableHome + ",disableRecent=" + disableRecent);
        getBackButton().setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        getHomeButton().setVisibility(disableHome ? View.INVISIBLE : View.VISIBLE);
        getRecentsButton().setVisibility(disableRecent ? View.INVISIBLE : View.VISIBLE);
        //by lym start
        getDRVButton().setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        getDVRBackButton().setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        getVoiceButton().setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        getSettingsButton().setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        //end
        /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
        if (mSupportDynamicBar) {
            updataNavigationBar();
        }
        /* @} */
        /*@}*/
    }

    private boolean inLockTask() {
        try {
            return ActivityManager.getService().isInLockTaskMode();
        } catch (RemoteException e) {
            return false;
        }
    }

    public void setLayoutTransitionsEnabled(boolean enabled) {
        mLayoutTransitionsEnabled = enabled;
        updateLayoutTransitionsEnabled();
    }

    public void setWakeAndUnlocking(boolean wakeAndUnlocking) {
        setUseFadingAnimations(wakeAndUnlocking);
        mWakeAndUnlocking = wakeAndUnlocking;
        updateLayoutTransitionsEnabled();
    }

    private void updateLayoutTransitionsEnabled() {
        boolean enabled = !mWakeAndUnlocking && mLayoutTransitionsEnabled;
        ViewGroup navButtons = (ViewGroup) getCurrentView().findViewById(R.id.nav_buttons);
        LayoutTransition lt = navButtons.getLayoutTransition();
        if (lt != null) {
            if (enabled) {
                lt.enableTransitionType(LayoutTransition.APPEARING);
                lt.enableTransitionType(LayoutTransition.DISAPPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            } else {
                lt.disableTransitionType(LayoutTransition.APPEARING);
                lt.disableTransitionType(LayoutTransition.DISAPPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            }
        }
    }

    private void setUseFadingAnimations(boolean useFadingAnimations) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) ((ViewGroup) getParent())
                .getLayoutParams();
        if (lp != null) {
            boolean old = lp.windowAnimations != 0;
            if (!old && useFadingAnimations) {
                lp.windowAnimations = R.style.Animation_NavigationBarFadeIn;
            } else if (old && !useFadingAnimations) {
                lp.windowAnimations = 0;
            } else {
                return;
            }
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout((View) getParent(), lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        // Only show Menu if IME switcher and Accessibility button not shown.
        final boolean shouldShow = mShowMenu && !mShowAccessibilityButton &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);

        getMenuButton().setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
    }

    public void setAccessibilityButtonState(final boolean visible, final boolean longClickable) {
        mShowAccessibilityButton = visible;
        mLongClickableAccessibilityButton = longClickable;
        if (visible) {
            // Accessibility button overrides Menu and IME switcher buttons.
            setMenuVisibility(false, true);
            getImeSwitchButton().setVisibility(View.INVISIBLE);
        }

        getAccessibilityButton().setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        getAccessibilityButton().setLongClickable(longClickable);
    }

    @Override
    public void onFinishInflate() {
        mNavigationInflaterView = (NavigationBarInflaterView) findViewById(
                R.id.navigation_inflater);
        mNavigationInflaterView.setButtonDispatchers(mButtonDispatchers);

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
        if (mSupportDynamicBar) {
            getHideSwitchButton().setOnClickListener(mHideBarClickListener);
            getPullSwitchButton().setOnClickListener(mNotiBarClickListener);
        }
        mNavigationInflaterView.setIsSupportDynavBar(mSupportDynamicBar);
        /* @} */

        DockedStackExistsListener.register(mDockedListener);
        updateRotatedViews();
    }

    void updateRotatedViews() {
        mRotatedViews[Surface.ROTATION_0] =
                mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);
        mRotatedViews[Surface.ROTATION_270] =
                mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

        updateCurrentView();
    }

    public boolean needsReorient(int rotation) {
        return mCurrentRotation != rotation;
    }

    private void updateCurrentView() {
        final int rot = mDisplay.getRotation();
        for (int i = 0; i < 4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);
        mNavigationInflaterView.setAlternativeOrder(rot == Surface.ROTATION_90);
        for (int i = 0; i < mButtonDispatchers.size(); i++) {
            mButtonDispatchers.valueAt(i).setCurrentView(mCurrentView);
        }
        updateLayoutTransitionsEnabled();
        mCurrentRotation = rot;
    }

    private void updateRecentsIcon() {
        getRecentsButton().setImageDrawable(mDockedStackExists ? mDockedIcon : mRecentIcon);
        mBarTransitions.reapplyDarkIntensity();
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void reorient() {
        updateCurrentView();

        /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
        if (mSupportDynamicBar) {
            getHideSwitchButton().setOnClickListener(mHideBarClickListener);
            getPullSwitchButton().setOnClickListener(mNotiBarClickListener);
        }
        /* @} */

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);

        ((NavigationBarFrame) getRootView()).setDeadZone(mDeadZone);
        mDeadZone.setDisplayRotation(mCurrentRotation);

        // force the low profile & disabled states into compliance
        mBarTransitions.init();
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mCurrentRotation);
        }

        updateTaskSwitchHelper();
        setNavigationIconHints(mNavigationIconHints, true);

        getHomeButton().setVertical(mVertical);
    }

    private void updateTaskSwitchHelper() {
        if (mGestureHelper == null) return;
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        mGestureHelper.setBarState(mVertical, isRtl);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(newVertical);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean uiCarModeChanged = updateCarMode(newConfig);
        updateTaskSwitchHelper();
        Log.d(TAG, "orientation change");
        updateIcons(getContext(), mConfiguration, newConfig);
        updateRecentsIcon();
        if (uiCarModeChanged || mConfiguration.densityDpi != newConfig.densityDpi
                || mConfiguration.getLayoutDirection() != newConfig.getLayoutDirection()) {
            // If car mode or density changes, we need to reset the icons.
            setNavigationIconHints(mNavigationIconHints, true);
        }
        mConfiguration.updateFrom(newConfig);
    }

    /**
     * If the configuration changed, update the carmode and return that it was updated.
     */
    private boolean updateCarMode(Configuration newConfig) {
        boolean uiCarModeChanged = false;
        if (newConfig != null) {
            int uiMode = newConfig.uiMode & Configuration.UI_MODE_TYPE_MASK;
            final boolean isCarMode = (uiMode == Configuration.UI_MODE_TYPE_CAR);

            if (isCarMode != mInCarMode) {
                mInCarMode = isCarMode;
                if (ALTERNATE_CAR_MODE_UI) {
                    mUseCarModeUi = isCarMode;
                    uiCarModeChanged = true;
                } else {
                    // Don't use car mode behavior if ALTERNATE_CAR_MODE_UI not set.
                    mUseCarModeUi = false;
                }
            }
        }
        return uiCarModeChanged;
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getContext().getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reorient();
        onPluginDisconnected(null); // Create default gesture helper
        Dependency.get(PluginManager.class).addPluginListener(this,
                NavGesture.class, false /* Only one */);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Dependency.get(PluginManager.class).removePluginListener(this);
        if (mGestureHelper != null) {
            mGestureHelper.destroy();
        }
    }

    @Override
    public void onPluginConnected(NavGesture plugin, Context context) {
        mGestureHelper = plugin.getGestureHelper();
        updateTaskSwitchHelper();
    }

    @Override
    public void onPluginDisconnected(NavGesture plugin) {
        NavigationBarGestureHelper defaultHelper = new NavigationBarGestureHelper(getContext());
        defaultHelper.setComponents(mRecentsComponent, mDivider, this);
        if (mGestureHelper != null) {
            mGestureHelper.destroy();
        }
        mGestureHelper = defaultHelper;
        updateTaskSwitchHelper();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + StatusBar.viewInfo(this)
                + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                getResourceName(getCurrentView().getId()),
                getCurrentView().getWidth(), getCurrentView().getHeight(),
                visibilityToString(getCurrentView().getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                mDisabledFlags,
                mVertical ? "true" : "false",
                mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());
        dumpButton(pw, "a11y", getAccessibilityButton());
        //by lym start
        dumpButton(pw, "voice", getVoiceButton());
        dumpButton(pw, "dvr", getDRVButton());
        dumpButton(pw, "dvrBack", getDVRBackButton());
        dumpButton(pw, "settings", getSettingsButton());
        //end
        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, ButtonDispatcher button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
            );
        }
        pw.println();
    }

    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }

    private final Consumer<Boolean> mDockedListener = exists -> mHandler.post(() -> {
        mDockedStackExists = exists;
        updateRecentsIcon();
    });

    /* SPRD: Bug 692453 new feature of dynamic navigationbar @{ */
    private final OnClickListener mNotiBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            onPullButtonClick();
        }
    };

    private final OnClickListener mHideBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent("com.action.hide_navigationbar");
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            mContext.sendBroadcast(intent);
            android.util.Log.d(TAG, "com.action.hide_navigationbar sendBroadcast");
        }
    };

    public void setBar(StatusBar statusbar) {
        mStatusBar = statusbar;
    }

    public void updataNavigationBar() {
        /*SPRD bug 721324:Disable hide and pull button while back\home\recent is invisible.*/
        int lastNavLayoutSettings = Settings.System.getInt(mContext.getContentResolver(), "navigationbar_config", 0);
        boolean otherVisible = getRecentsButton().getVisibility() == View.VISIBLE
                //|| getBackButton().getVisibility() == View.VISIBLE
                || getHomeButton().getVisibility() == View.VISIBLE;
        Log.d(TAG, "updataNavigationBar otherVisible=" + otherVisible);
        if ((lastNavLayoutSettings & 0x10) != 0 && otherVisible) {
            getHideSwitchButton().setVisibility(View.VISIBLE);
            getHideSwitchButton().setImageDrawable(mHideIcon);
        } else {
            getHideSwitchButton().setVisibility(View.INVISIBLE);
        }
        updateSwitchButton(lastNavLayoutSettings);
        mBarTransitions.reapplyDarkIntensity();
    }

    public void updateSwitchButton(int lastNavLayoutSettings) {
        /*SPRD bug 721324:Disable hide and pull button while back\home\recent is invisible.*/
        boolean otherVisible = getRecentsButton().getVisibility() == View.VISIBLE
                //|| getBackButton().getVisibility() == View.VISIBLE
                || getHomeButton().getVisibility() == View.VISIBLE;
        Log.d(TAG, "updateSwitchButton otherVisible=" + otherVisible);
        if (((lastNavLayoutSettings & 0x0F) == NavigationBarInflaterView.NAVIGATION_RIGHT_NOTI
                || (lastNavLayoutSettings & 0x0F) == NavigationBarInflaterView.NAVIGATION_LEFT_NOTI) && otherVisible) {
            getPullSwitchButton().setVisibility(View.VISIBLE);
            if (mStatusBar != null && !mStatusBar.isPanelFullyCollapsed()) {
                getPullSwitchButton().setImageDrawable(mPullUpIcon);
            } else {
                getPullSwitchButton().setImageDrawable(mPullDownIcon);
            }
        } else {
            getPullSwitchButton().setVisibility(View.INVISIBLE);
        }
    }

    public void setPanelExpanded(boolean expaned) {
        if (mSupportDynamicBar) {
            if (expaned) {
                getPullSwitchButton().setImageDrawable(mPullUpIcon);
            } else {
                getPullSwitchButton().setImageDrawable(mPullDownIcon);
            }
            Log.d(TAG, "reapplyDarkIntensity after panel expand or collape");
            mBarTransitions.reapplyDarkIntensity();
        }
    }

    public void onPullButtonClick() {
        // TODO: this code piece can be deleted
        // it only to display carrier info. when click pull down/up notification button
        if (mStatusBar != null) {
            if (!mStatusBar.isPanelFullyCollapsed()) {
                mStatusBar.animateCollapsePanels();
            } else {
                mStatusBar.animateExpandSettingsPanel(null);
            }
        }
    }

    public boolean isSupportDynamicBar() {
        return mSupportDynamicBar;
    }
    /* @} */
}
