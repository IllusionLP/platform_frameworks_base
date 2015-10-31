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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.NotificationColorUtil;
import com.android.internal.util.darkkat.ColorHelper;
import com.android.internal.util.darkkat.DeviceUtils;
import com.android.internal.util.darkkat.StatusBarColorHelper;
import com.android.keyguard.CarrierText;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.NetworkTraffic;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Controls everything regarding the icons in the status bar and on Keyguard, including, but not
 * limited to: notification icons, signal cluster, additional status icons, and clock in the status
 * bar.
 */
public class StatusBarIconController implements Tunable {

    public static final long DEFAULT_TINT_ANIMATION_DURATION = 120;

    public static final String ICON_BLACKLIST = "icon_blacklist";

    private Context mContext;
    private PhoneStatusBar mPhoneStatusBar;
    private Interpolator mLinearOutSlowIn;
    private Interpolator mFastOutSlowIn;
    private DemoStatusIcons mDemoStatusIcons;
    private NotificationColorUtil mNotificationColorUtil;

    private LinearLayout mSystemIconArea;
    private LinearLayout mStatusIcons;
    private SignalClusterView mSignalCluster;
    private LinearLayout mStatusIconsKeyguard;
    private CarrierText mStatusBarCarrierLabel;
    private IconMerger mNotificationIcons;
    private View mNotificationIconArea;
    private ImageView mMoreIcon;
    private BatteryMeterView mBatteryMeterView;
    private Clock mClockDefault;
    private Clock mClockCentered;
    private LinearLayout mCenterClockLayout;
    private NetworkTraffic mNetworkTraffic;

    private int mIconSize;
    private int mIconHPadding;

    private int mIconTint = Color.WHITE;
    private int mCarrierLabelColor;
    private int mCarrierLabelColorOld;
    private int mCarrierLabelColorTint;
//    private int mClockColor;
    private int mBatteryFrameColorOld;
    private int mBatteryColorOld;
//    private int mBatteryTint;
    private int mBatteryTextColorOld;
//    private int mBatteryTextTint;
//    private int mNetworkTrafficTextColor;
//    private int mNetworkTrafficIconColor;
    private int mNetworkSignalColor;
    private int mNetworkSignalColorOld;
    private int mNetworkSignalColorTint;
    private int mNoSimColor;
    private int mNoSimColorOld;
    private int mNoSimColorTint;
    private int mAirplaneModeColor;
    private int mAirplaneModeColorOld;
    private int mAirplaneModeColorTint;
    private float mDarkIntensity;

    private boolean mTransitionPending;
    private boolean mTintChangePending;
    private float mPendingDarkIntensity;
    private ValueAnimator mTintAnimator;

    private int mDarkModeIconColorSingleTone;
    private int mLightModeIconColorSingleTone;

    private static final int CLOCK_STYLE_DEFAULT  = 0;
    private static final int CLOCK_STYLE_CENTERED = 1;
    private static final int CLOCK_STYLE_HIDDEN   = 2;
    private int mClockStyle;

    private static final int CARRIER_LABEL_COLOR  = 0;
    private static final int BATTERY_COLOR        = 1;
    private static final int BATTERY_TEXT_COLOR   = 2;
    private static final int NETWORK_SIGNAL_COLOR = 3;
    private static final int NO_SIM_COLOR         = 4;
    private static final int AIRPLANE_MODE_COLOR  = 5;
    private int mColorToChange;

    private final Handler mHandler;
    private boolean mTransitionDeferring;
    private long mTransitionDeferringStartTime;
    private long mTransitionDeferringDuration;

    private Animator mColorTransitionAnimator;

    private final ArraySet<String> mIconBlacklist = new ArraySet<>();

    private final Runnable mTransitionDeferringDoneRunnable = new Runnable() {
        @Override
        public void run() {
            mTransitionDeferring = false;
        }
    };

    public StatusBarIconController(Context context, View statusBar, View keyguardStatusBar,
            PhoneStatusBar phoneStatusBar) {
        mContext = context;
        mPhoneStatusBar = phoneStatusBar;
        mNotificationColorUtil = NotificationColorUtil.getInstance(context);
        mSystemIconArea = (LinearLayout) statusBar.findViewById(R.id.system_icon_area);
        mStatusIcons = (LinearLayout) statusBar.findViewById(R.id.statusIcons);
        mSignalCluster = (SignalClusterView) statusBar.findViewById(R.id.signal_cluster);
        mNotificationIconArea = statusBar.findViewById(R.id.notification_icon_area_inner);
        mStatusBarCarrierLabel = (CarrierText) statusBar.findViewById(R.id.status_bar_carrier_text);
        mNotificationIcons = (IconMerger) statusBar.findViewById(R.id.notificationIcons);
        mMoreIcon = (ImageView) statusBar.findViewById(R.id.moreIcon);
        mNotificationIcons.setOverflowIndicator(mMoreIcon);
        mStatusIconsKeyguard = (LinearLayout) keyguardStatusBar.findViewById(R.id.statusIcons);
        mBatteryMeterView = (BatteryMeterView) statusBar.findViewById(R.id.battery);
        mClockDefault = (Clock) statusBar.findViewById(R.id.clock);
        mClockCentered = (Clock) statusBar.findViewById(R.id.center_clock);
        mCenterClockLayout = (LinearLayout) statusBar.findViewById(R.id.center_clock_layout);
        mNetworkTraffic = (NetworkTraffic) statusBar.findViewById(R.id.network_traffic_layout);
        mLinearOutSlowIn = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.linear_out_slow_in);
        mFastOutSlowIn = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.fast_out_slow_in);
        mDarkModeIconColorSingleTone = context.getColor(R.color.dark_mode_icon_color_single_tone);
        mLightModeIconColorSingleTone = context.getColor(R.color.light_mode_icon_color_single_tone);
        mHandler = new Handler();
        updateResources();
        TunerService.get(mContext).addTunable(this, ICON_BLACKLIST);

        setUpCustomColors();
    }

    private void setUpCustomColors() {
        mCarrierLabelColor = StatusBarColorHelper.getCarrierLabelColor(mContext);
        mCarrierLabelColorOld = mCarrierLabelColor;
        mCarrierLabelColorTint = mCarrierLabelColor;
        mBatteryFrameColorOld = StatusBarColorHelper.getBatteryFrameColor(mContext);
        mBatteryColorOld = StatusBarColorHelper.getBatteryColor(mContext);
        mBatteryTextColorOld = StatusBarColorHelper.getBatteryTextColor(mContext);
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mNetworkSignalColorOld = mNetworkSignalColor;
        mNetworkSignalColorTint = mNetworkSignalColor;
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mNoSimColorOld = mNoSimColor;
        mNoSimColorTint = mNoSimColor;
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        mAirplaneModeColorOld = mAirplaneModeColor;
        mAirplaneModeColorTint = mAirplaneModeColor;

        mColorTransitionAnimator = createColorTransitionAnimator(0, 1);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!ICON_BLACKLIST.equals(key)) {
            return;
        }
        mIconBlacklist.clear();
        mIconBlacklist.addAll(getIconBlacklist(newValue));
        ArrayList<StatusBarIconView> views = new ArrayList<StatusBarIconView>();
        // Get all the current views.
        for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
            views.add((StatusBarIconView) mStatusIcons.getChildAt(i));
        }
        // Remove all the icons.
        for (int i = views.size() - 1; i >= 0; i--) {
            removeSystemIcon(views.get(i).getSlot(), i, i);
        }
        // Add them all back
        for (int i = 0; i < views.size(); i++) {
            addSystemIcon(views.get(i).getSlot(), i, i, views.get(i).getStatusBarIcon());
        }
    };

    public void updateResources() {
        mIconSize = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_size);
        mIconHPadding = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_padding);
        FontSizeUtils.updateFontSize(mClockDefault, R.dimen.status_bar_clock_size);
        FontSizeUtils.updateFontSize(mClockCentered, R.dimen.status_bar_clock_size);
    }

    public void addSystemIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
        boolean blocked = mIconBlacklist.contains(slot);
        StatusBarIconView view = new StatusBarIconView(mContext, slot, null, blocked);
        view.set(icon);
        mStatusIcons.addView(view, viewIndex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize));
        view = new StatusBarIconView(mContext, slot, null, blocked);
        view.set(icon);
        mStatusIconsKeyguard.addView(view, viewIndex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize));
        applyIconTint();
    }

    public void updateSystemIcon(String slot, int index, int viewIndex,
            StatusBarIcon old, StatusBarIcon icon) {
        StatusBarIconView view = (StatusBarIconView) mStatusIcons.getChildAt(viewIndex);
        view.set(icon);
        view = (StatusBarIconView) mStatusIconsKeyguard.getChildAt(viewIndex);
        view.set(icon);
        applyIconTint();
    }

    public void removeSystemIcon(String slot, int index, int viewIndex) {
        mStatusIcons.removeViewAt(viewIndex);
        mStatusIconsKeyguard.removeViewAt(viewIndex);
    }

    public void updateNotificationIcons(NotificationData notificationData) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                mIconSize + 2*mIconHPadding, mPhoneStatusBar.getStatusBarHeight());

        ArrayList<NotificationData.Entry> activeNotifications =
                notificationData.getActiveNotifications();
        final int N = activeNotifications.size();
        ArrayList<StatusBarIconView> toShow = new ArrayList<>(N);

        // Filter out ambient notifications and notification children.
        for (int i = 0; i < N; i++) {
            NotificationData.Entry ent = activeNotifications.get(i);
            if (notificationData.isAmbient(ent.key)
                    && !NotificationData.showNotificationEvenIfUnprovisioned(ent.notification)) {
                continue;
            }
            if (!PhoneStatusBar.isTopLevelChild(ent)) {
                continue;
            }
            toShow.add(ent.icon);
        }

        ArrayList<View> toRemove = new ArrayList<>();
        for (int i=0; i<mNotificationIcons.getChildCount(); i++) {
            View child = mNotificationIcons.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        final int toRemoveCount = toRemove.size();
        for (int i = 0; i < toRemoveCount; i++) {
            mNotificationIcons.removeView(toRemove.get(i));
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mNotificationIcons.addView(v, i, params);
            }
        }

        // Resort notification icons
        final int childCount = mNotificationIcons.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View actual = mNotificationIcons.getChildAt(i);
            StatusBarIconView expected = toShow.get(i);
            if (actual == expected) {
                continue;
            }
            mNotificationIcons.removeView(expected);
            mNotificationIcons.addView(expected, i);
        }

        applyNotificationIconsTint();
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate);
        if (mClockStyle == CLOCK_STYLE_CENTERED) {
            animateHide(mCenterClockLayout, animate);
        }
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
        if (mClockStyle == CLOCK_STYLE_CENTERED) {
            animateShow(mCenterClockLayout, animate);
        }
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconArea, animate);
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconArea, animate);
    }

    public void setClockVisibility(boolean visible) {
        if (mClockStyle == CLOCK_STYLE_DEFAULT) {
            mClockDefault.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (mClockStyle == CLOCK_STYLE_CENTERED) {
            mClockCentered.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void dump(PrintWriter pw) {
        int N = mStatusIcons.getChildCount();
        pw.println("  system icons: " + N);
        for (int i=0; i<N; i++) {
            StatusBarIconView ic = (StatusBarIconView) mStatusIcons.getChildAt(i);
            pw.println("    [" + i + "] icon=" + ic);
        }
    }

    public void dispatchDemoCommand(String command, Bundle args) {
        if (mDemoStatusIcons == null) {
            mDemoStatusIcons = new DemoStatusIcons(mStatusIcons, mIconSize);
        }
        mDemoStatusIcons.dispatchDemoCommand(command, args);
    }

    public void dispatchClockDemoCommand(String command, Bundle args) {
        if (mClockStyle == CLOCK_STYLE_DEFAULT) {
            mClockDefault.dispatchDemoCommand(command, args);
        }
        if (mClockStyle == CLOCK_STYLE_CENTERED) {
            mClockCentered.dispatchDemoCommand(command, args);
        }
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(View.INVISIBLE);
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(PhoneStatusBar.ALPHA_OUT)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setVisibility(View.INVISIBLE);
                    }
                });
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(PhoneStatusBar.ALPHA_IN)
                .setStartDelay(50)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mPhoneStatusBar.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mPhoneStatusBar.getKeyguardFadingAwayDuration())
                    .setInterpolator(mLinearOutSlowIn)
                    .setStartDelay(mPhoneStatusBar.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    public void setIconsDark(boolean dark, boolean animate) {
        if (!animate) {
            setIconTintInternal(dark ? 1.0f : 0.0f);
        } else if (mTransitionPending) {
            deferIconTintChange(dark ? 1.0f : 0.0f);
        } else if (mTransitionDeferring) {
            animateIconTint(dark ? 1.0f : 0.0f,
                    Math.max(0, mTransitionDeferringStartTime - SystemClock.uptimeMillis()),
                    mTransitionDeferringDuration);
        } else {
            animateIconTint(dark ? 1.0f : 0.0f, 0 /* delay */, DEFAULT_TINT_ANIMATION_DURATION);
        }
    }

    private void animateIconTint(float targetDarkIntensity, long delay,
            long duration) {
        if (mTintAnimator != null) {
            mTintAnimator.cancel();
        }
        if (mDarkIntensity == targetDarkIntensity) {
            return;
        }
        mTintAnimator = ValueAnimator.ofFloat(mDarkIntensity, targetDarkIntensity);
        mTintAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setIconTintInternal((Float) animation.getAnimatedValue());
            }
        });
        mTintAnimator.setDuration(duration);
        mTintAnimator.setStartDelay(delay);
        mTintAnimator.setInterpolator(mFastOutSlowIn);
        mTintAnimator.start();
    }

    private void setIconTintInternal(float darkIntensity) {
        mDarkIntensity = darkIntensity;
        mIconTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mLightModeIconColorSingleTone, mDarkModeIconColorSingleTone);
        if (DeviceUtils.deviceSupportsMobileData(mContext)) {
            mCarrierLabelColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                    mCarrierLabelColor, StatusBarColorHelper.getCarrierLabelColorDarkMode(mContext));
        }
//        mClockColor = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
//                mClockDefault.getColor(), mClockDefault.getColorDarkMode());
//        mBatteryTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
//                StatusBarColorHelper.getBatteryColor(mContext), StatusBarColorHelper.getBatteryColorDarkMode(mContext));
//        mBatteryTextTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
//                StatusBarColorHelper.getBatteryTextColor(mContext), StatusBarColorHelper.getBatteryTextColorDarkMode(mContext));
//        mNetworkTrafficTextColor = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
//                mNetworkTraffic.getTextColor(), mNetworkTraffic.getTextColorDarkMode());
//        mNetworkTrafficIconColor = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
//                mNetworkTraffic.getIconColor(), mNetworkTraffic.getIconColorDarkMode());
        mNetworkSignalColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mNetworkSignalColor, StatusBarColorHelper.getNetworkSignalColorDark(mContext));
        mNoSimColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mNoSimColor, StatusBarColorHelper.getNoSimColorDark(mContext));
        mAirplaneModeColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mAirplaneModeColor, StatusBarColorHelper.getAirplaneModeColorDark(mContext));

        applyIconTint();
    }

    private void deferIconTintChange(float darkIntensity) {
        if (mTintChangePending && darkIntensity == mPendingDarkIntensity) {
            return;
        }
        mTintChangePending = true;
        mPendingDarkIntensity = darkIntensity;
    }

    private void applyIconTint() {
        for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mStatusIcons.getChildAt(i);
            v.setImageTintList(ColorStateList.valueOf(mIconTint));
        }
        mSignalCluster.setIconTint(
                mNetworkSignalColorTint, mNoSimColorTint, mAirplaneModeColorTint, mDarkIntensity);
        if (DeviceUtils.deviceSupportsMobileData(mContext)) {
            mStatusBarCarrierLabel.setTextColor(mCarrierLabelColorTint);
        }
        mMoreIcon.setImageTintList(ColorStateList.valueOf(mIconTint));
//        if (showBattery()) {
//            mBatteryMeterView.setBatteryColor(mBatteryTint);
//            mBatteryColorOld = mBatteryTint;
//            if (showBatteryText()) {
//                mBatteryMeterView.setBatteryTextColor(mBatteryTextTint);
//                mBatteryTextColorOld = mBatteryTextTint;
//            }
//        }
//        mClockDefault.setTextColor(mClockColor);
//        mClockCentered.setTextColor(mClockColor);
//        mNetworkTraffic.setTextColor(mNetworkTrafficTextColor);
//        mNetworkTraffic.setIconColor(mNetworkTrafficIconColor);
        applyNotificationIconsTint();
    }

    private void applyNotificationIconsTint() {
        for (int i = 0; i < mNotificationIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mNotificationIcons.getChildAt(i);
            boolean isPreL = Boolean.TRUE.equals(v.getTag(R.id.icon_is_pre_L));
            boolean colorize = !isPreL || isGrayscale(v);
            if (colorize) {
                v.setImageTintList(ColorStateList.valueOf(mIconTint));
            }
        }
    }

    private boolean isGrayscale(StatusBarIconView v) {
        Object isGrayscale = v.getTag(R.id.icon_is_grayscale);
        if (isGrayscale != null) {
            return Boolean.TRUE.equals(isGrayscale);
        }
        boolean grayscale = mNotificationColorUtil.isGrayscaleIcon(v.getDrawable());
        v.setTag(R.id.icon_is_grayscale, grayscale);
        return grayscale;
    }

    public void appTransitionPending() {
        mTransitionPending = true;
    }

    public void appTransitionCancelled() {
        if (mTransitionPending && mTintChangePending) {
            mTintChangePending = false;
            animateIconTint(mPendingDarkIntensity, 0 /* delay */, DEFAULT_TINT_ANIMATION_DURATION);
        }
        mTransitionPending = false;
    }

    public void appTransitionStarting(long startTime, long duration) {
        if (mTransitionPending && mTintChangePending) {
            mTintChangePending = false;
            animateIconTint(mPendingDarkIntensity,
                    Math.max(0, startTime - SystemClock.uptimeMillis()),
                    duration);

        } else if (mTransitionPending) {

            // If we don't have a pending tint change yet, the change might come in the future until
            // startTime is reached.
            mTransitionDeferring = true;
            mTransitionDeferringStartTime = startTime;
            mTransitionDeferringDuration = duration;
            mHandler.removeCallbacks(mTransitionDeferringDoneRunnable);
            mHandler.postAtTime(mTransitionDeferringDoneRunnable, startTime);
        }
        mTransitionPending = false;
    }

    public static ArraySet<String> getIconBlacklist(String blackListStr) {
        ArraySet<String> ret = new ArraySet<String>();
        if (blackListStr != null) {
            String[] blacklist = blackListStr.split(",");
            for (String slot : blacklist) {
                if (!TextUtils.isEmpty(slot)) {
                    ret.add(slot);
                }
            }
        }
        return ret;
    }

    private ValueAnimator createColorTransitionAnimator(float start, float end) {
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);

        animator.setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float position = animation.getAnimatedFraction();
                int blendedFrame;
                int blended;
                if (mColorToChange == CARRIER_LABEL_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mCarrierLabelColorOld, mCarrierLabelColor, position);
                    mStatusBarCarrierLabel.setTextColor(blended);
                } else if (mColorToChange == BATTERY_COLOR) {
                    blendedFrame = ColorHelper.getBlendColor(
                            mBatteryFrameColorOld, StatusBarColorHelper.getBatteryFrameColor(mContext), position);
                    blended = ColorHelper.getBlendColor(
                            mBatteryColorOld, StatusBarColorHelper.getBatteryColor(mContext), position);
                    mBatteryMeterView.setBatteryFrameColor(blendedFrame);
                    mBatteryMeterView.setBatteryColor(blended);
                } else if (mColorToChange == BATTERY_TEXT_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mBatteryTextColorOld, StatusBarColorHelper.getBatteryTextColor(mContext), position);
                    mBatteryMeterView.setBatteryTextColor(blended);
                } else if (mColorToChange == NETWORK_SIGNAL_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mNetworkSignalColorOld, mNetworkSignalColor, position);
                    mSignalCluster.applyNetworkSignalTint(blended);
                } else if (mColorToChange == NO_SIM_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mNoSimColorOld, mNoSimColor, position);
                    mSignalCluster.applyNoSimTint(blended);
                } else if (mColorToChange == AIRPLANE_MODE_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mAirplaneModeColorOld, mAirplaneModeColor, position);
                    mSignalCluster.applyAirplaneModeTint(blended);
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mColorToChange == CARRIER_LABEL_COLOR) {
                    mCarrierLabelColorOld = mCarrierLabelColor;
                    mCarrierLabelColorTint = mCarrierLabelColor;
                } else if (mColorToChange == BATTERY_COLOR) {
                    mBatteryFrameColorOld = StatusBarColorHelper.getBatteryFrameColor(mContext);
                    mBatteryColorOld = StatusBarColorHelper.getBatteryColor(mContext);
                } else if (mColorToChange == BATTERY_TEXT_COLOR) {
                    mBatteryTextColorOld = StatusBarColorHelper.getBatteryTextColor(mContext);
                } else if (mColorToChange == NETWORK_SIGNAL_COLOR) {
                    mNetworkSignalColorOld = mNetworkSignalColor;
                    mNetworkSignalColorTint = mNetworkSignalColor;
                } else if (mColorToChange == NO_SIM_COLOR) {
                    mNoSimColorOld = mNoSimColor;
                    mNoSimColorTint = mNoSimColor;
                } else if (mColorToChange == AIRPLANE_MODE_COLOR) {
                    mAirplaneModeColorOld = mAirplaneModeColor;
                    mAirplaneModeColorTint = mAirplaneModeColor;
                }
            }
        });
        return animator;
    }

    public int getCurrentVisibleNotificationIcons() {
        return mNotificationIcons.getChildCount();
    }

    public void updateCarrierLabelColor(boolean animate) {
        mCarrierLabelColor = StatusBarColorHelper.getCarrierLabelColor(mContext);
        if (animate) {
            mColorToChange = CARRIER_LABEL_COLOR;
            mColorTransitionAnimator.start();
        } else {
            mStatusBarCarrierLabel.setTextColor(mCarrierLabelColor);
            mCarrierLabelColorOld = mCarrierLabelColor;
            mCarrierLabelColorTint = mCarrierLabelColor;
        }
    }

    public void setClockStyle(int clockStyle) {
        mClockStyle = clockStyle;

        switch (mClockStyle) {
            case CLOCK_STYLE_DEFAULT:
                mClockCentered.setVisibility(View.GONE);
                mCenterClockLayout.setVisibility(View.GONE);
                mClockDefault.setVisibility(View.VISIBLE);
                break;
            case CLOCK_STYLE_CENTERED:
                mClockDefault.setVisibility(View.GONE);
                mCenterClockLayout.setVisibility(View.VISIBLE);
                mClockCentered.setVisibility(View.VISIBLE);
                break;
            case CLOCK_STYLE_HIDDEN:
                mClockDefault.setVisibility(View.GONE);
                mCenterClockLayout.setVisibility(View.GONE);
                mClockCentered.setVisibility(View.GONE);
                break;
        }
        mNotificationIcons.setCenteredClock(mClockStyle == CLOCK_STYLE_CENTERED);
    }

    public void updateClockSettings() {
        mClockDefault.updateSettings();
        mClockCentered.updateSettings();
    }

    public void updateClockColor(boolean animate) {
        mClockDefault.updateClockColor(animate);
        mClockCentered.updateClockColor(animate);
    }

    public void updateBatterySettings() {
        updateBatteryVisibility();
        updateBatteryTextVisibility();
        updateCutOutBatteryText();
        updateBatteryColor(false);
        updateBatteryTextColor(false);
    }

    public void updateBatteryVisibility() {
        mBatteryMeterView.setVisibility(showBattery() ? View.VISIBLE : View.GONE);
    }

    public void updateBatteryTextVisibility() {
        mBatteryMeterView.setTextVisibility(showBatteryText() ? true : false);
    }

    public void updateCutOutBatteryText() {
        mBatteryMeterView.setCutOutBatteryText(cutOutBatteryText() ? true : false);
    }

    public void updateBatteryColor(boolean animate) {
        if (animate) {
            mColorToChange = BATTERY_COLOR;
            mColorTransitionAnimator.start();
        } else {
            mBatteryMeterView.setBatteryFrameColor(StatusBarColorHelper.getBatteryFrameColor(mContext));
            mBatteryMeterView.setBatteryColor(StatusBarColorHelper.getBatteryColor(mContext));
            mBatteryFrameColorOld = StatusBarColorHelper.getBatteryFrameColor(mContext);
            mBatteryColorOld = StatusBarColorHelper.getBatteryColor(mContext);
        }
    }

    public void updateBatteryTextColor(boolean animate) {
        if (animate) {
            mColorToChange = BATTERY_TEXT_COLOR;
            mColorTransitionAnimator.start();
        } else {
            mBatteryMeterView.setBatteryTextColor(StatusBarColorHelper.getBatteryTextColor(mContext));
            mBatteryTextColorOld = StatusBarColorHelper.getBatteryTextColor(mContext);
        }
    }

    private boolean showBattery() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_STATUS_SHOW_BATTERY, 1,
                UserHandle.USER_CURRENT) == 1;
    }

    private boolean showBatteryText() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_STATUS_SHOW_TEXT, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    private boolean cutOutBatteryText() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_STATUS_CUT_OUT_TEXT, 1,
                UserHandle.USER_CURRENT) == 1;
    }

    public void updateNetworkIconColors() {
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        mNetworkSignalColorOld = mNetworkSignalColor;
        mNoSimColorOld = mNoSimColor;
        mAirplaneModeColorOld = mAirplaneModeColor;
        mNetworkSignalColorTint = mNetworkSignalColor;
        mNoSimColorTint = mNoSimColor;
        mAirplaneModeColorTint = mAirplaneModeColor;

        mSignalCluster.setIgnoreSystemUITuner(true);
        mSignalCluster.setIconTint(mNetworkSignalColor, mNoSimColor, mAirplaneModeColor, mDarkIntensity);
    }

    public void updateNetworkSignalColor() {
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mColorToChange = NETWORK_SIGNAL_COLOR;
        mColorTransitionAnimator.start();
    }

    public void updateNoSimColor() {
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mColorToChange = NO_SIM_COLOR;
        mColorTransitionAnimator.start();
    }

    public void updateAirplaneModeColor() {
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        mColorToChange = AIRPLANE_MODE_COLOR;
        mColorTransitionAnimator.start();
    }
}
