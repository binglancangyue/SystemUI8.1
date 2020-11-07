/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui;

import android.app.Service;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.text.format.Formatter;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.R;
import com.android.systemui.recents.Recents;
import com.android.systemui.RecentsComponent;
import com.android.systemui.recents.events.EventBus;
import com.android.systemui.recents.events.component.ScreenPinningRequestEvent;
import com.android.systemui.recents.events.ui.RecentsDrawnEvent;
import com.android.systemui.recents.events.component.RecentsVisibilityChangedEvent;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class SystemUIService extends Service {

    /* SPRD: new feature of sprd recents @{ */
    final private Handler mHandler = new Handler();
    final private Messenger mMessenger = new Messenger(new IncomingHandler());
    /* @} */
    final public static String REMOVE_ALL_RECENT_TASKS = "com.android.systemui.action_remove_all_recent_tasks";// SPRD: new feature of showing memory tip

    @Override
    public void onCreate() {
        /* SPRD: fix bug 793189 @{*/
        if(isCurrentEncrypt()) {
            return;
        }
        /*@}*/
        super.onCreate();
        ((SystemUIApplication) getApplication()).startServicesIfNeeded();

        // For debugging RescueParty
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("debug.crash_sysui", false)) {
            throw new RuntimeException();
        }
        /* SPRD: new feature of showing memory tip @{ */
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(REMOVE_ALL_RECENT_TASKS);
        this.registerReceiver(recentTaskReceiver, intentFilter);
        /* @} */
    }

    /*
     * fix for bug 793189
     */
    private boolean isCurrentEncrypt() {
        int progress = SystemProperties.getInt("vold.encrypt_progress", -1);
        return progress > 0 && progress < 100;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();// SPRD: new feature of sprd recents
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        SystemUI[] services = ((SystemUIApplication) getApplication()).getServices();
        if (args == null || args.length == 0) {
            for (SystemUI ui: services) {
                pw.println("dumping service: " + ui.getClass().getName());
                ui.dump(fd, pw, args);
            }
        } else {
            String svc = args[0];
            for (SystemUI ui: services) {
                String name = ui.getClass().getName();
                if (name.endsWith(svc)) {
                    ui.dump(fd, pw, args);
                }
            }
        }
    }

    /* SPRD: new feature of sprd recents @{ */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            RecentsComponent recents = ((SystemUIApplication) getApplication()).getComponent(Recents.class);
            switch (msg.what) {
                case Recents.SPRD_SHOW_SCREEN_PIN_REQUEST:
                    EventBus.getDefault().send(new ScreenPinningRequestEvent(SystemUIService.this, msg.arg1));
                    break;
                case Recents.SPRD_LAUNCH_SPLIT_APP_PANEL:
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            recents.dockTopTask(-1, ActivityManager.DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT, null, -1);
                        }
                    }, 350);
                    break;
                case Recents.SPRD_RECENTS_DRAWN:
                    EventBus.getDefault().send(new RecentsDrawnEvent());
                    break;
                case Recents.SPRD_RECENTS_VISBILITY_CHANGED:
                    EventBus.getDefault().send(new RecentsVisibilityChangedEvent(SystemUIService.this, msg.arg1 == 0 ? false : true));
                    break;
                default:
                    break;
            }
        }
    }
    /* @} */

    /* SPRD: new feature of showing memory tip @{ */
    private final BroadcastReceiver recentTaskReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final double availBefore = intent.getDoubleExtra("availBefore", 0);
            final double availAfter = Recents.getSystemServices().getMemoryInfo().availMem;
            final long size = availAfter > availBefore ? (long) (availAfter - availBefore) : 0;
            String release = Formatter.formatFileSize(context, size);
            String avail = Formatter.formatFileSize(context, (long) availAfter);
            Toast toast = Toast.makeText(context,
                context.getString(R.string.recent_app_clean_finished_toast, release, avail), Toast.LENGTH_SHORT);
            toast.getWindowParams().privateFlags |= LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            toast.show();
        }
    };
    /* @} */
}

