/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.systemui.recents.sprd;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.Messenger;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.MutableBoolean;
import android.graphics.Rect;

import com.android.systemui.recents.RecentsImpl;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.events.EventBus;
import com.android.systemui.recents.events.activity.RecentsActivityStartingEvent;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.recents.events.EventBus;
import com.android.systemui.recents.events.activity.DockedTopTaskEvent;
import com.android.systemui.statusbar.phone.NavigationBarGestureHelper;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.stackdivider.DividerView;

public class RecentsSprdImpl extends RecentsImpl {

    final static String TAG = "RecentsSprdImpl";
    public final static String PROXY_RECENTS_PACKAGE = "com.sprd.recents";
    public final static String ACTION_TOGGLE_RECENTS = "com.sprd.recents.ACTION_TOGGLE";
    public final static String ACTION_SHOW_RECENTS = "com.sprd.recents.ACTION_SHOW";
    public final static String ACTION_HIDE_RECENTS = "com.sprd.recents.ACTION_HIDE";

    public final static int SPRD_TOGGLE_RECENTS = 1;
    public final static int SPRD_SHOW_RECENTS = 2;
    public final static int SPRD_HIDE_RECENTS = 3;

    private int pendingAction = 0;
    private Messenger mService = null;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            if (pendingAction != 0) {
                Message msg = Message.obtain(null, pendingAction, 0, 0);
                try {
                    mService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                pendingAction = 0;
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    public RecentsSprdImpl(Context context) {
        super(context);
        bindSprdRecentsService();
    }

    @Override
    public void onBootCompleted() {
        // Do nothing
    }

    @Override
    public void onConfigurationChanged() {
        // Do nothing
    }

    @Override
    public void preloadRecents() {
        // Do nothing
    }

    @Override
    public void cancelPreloadingRecents() {
        // Do nothing
    }

    private void bindSprdRecentsService() {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.sprd.recents",
                    "com.sprd.recents.RecentsService"));
        mContext.bindServiceAsUser(i,  mConnection, Context.BIND_AUTO_CREATE, UserHandle.CURRENT);
    }

    private void proxyAction(int action) {
        if (mService == null) {
            pendingAction = action;
            bindSprdRecentsService();
        } else {
            Message msg = Message.obtain(null, action, 0, 0);
            try {
                mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void proxyAction(int action, boolean triggeredFromHomeKey) {
        if (mService == null) {
            bindSprdRecentsService();
        } else {
            Message msg = Message.obtain(null, action, 0, 0);
            msg.arg1 = triggeredFromHomeKey ? 1 : 0;
            try {
                mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void showRecents(boolean triggeredFromAltTab, boolean draggingInRecents,
            boolean animate, boolean launchedWhileDockingTask, boolean fromHome,
            int growTarget) {
        android.util.Log.d(TAG, "showRecents u:" + Recents.getSystemServices().getCurrentUser());
        /*SPRD bug 695538:Split screen feature*/
        try {
            android.util.Log.d(TAG, "showRecents hasDockedTask:" + Recents.getSystemServices().hasDockedTask());
            if(Recents.getSystemServices().hasDockedTask()){
                startSprdRecentActivity();
                EventBus.getDefault().send(new RecentsActivityStartingEvent());
                return;
            }
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch startSprdRecentActivity:", e);
        }
        /*@}*/
        proxyAction(SPRD_SHOW_RECENTS);
        EventBus.getDefault().send(new RecentsActivityStartingEvent());
    }

    @Override
    public void hideRecents(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        android.util.Log.d(TAG, "hideRecents u:" + Recents.getSystemServices().getCurrentUser());
        proxyAction(SPRD_HIDE_RECENTS, triggeredFromHomeKey);
    }

    @Override
    public void toggleRecents(int growTarget) {
        android.util.Log.d(TAG, "toggleRecents u:" + Recents.getSystemServices().getCurrentUser());
        /*SPRD bug 695538:Split screen feature*/
        try {
            android.util.Log.d(TAG, "toggleRecents hasDockedTask:" + Recents.getSystemServices().hasDockedTask());
            if(Recents.getSystemServices().hasDockedTask()){
                startSprdRecentActivity();
                return;
            }
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch startSprdRecentActivity:", e);
        }
        /*@}*/
        proxyAction(SPRD_TOGGLE_RECENTS);
    }

    @Override
    public void dockTopTask(int topTaskId, int dragMode,
            int stackCreateMode, Rect initialBounds) {
        SystemServicesProxy ssp = Recents.getSystemServices();

        // Make sure we inform DividerView before we actually start the activity so we can change
        // the resize mode already.
        if (ssp.moveTaskToDockedStack(topTaskId, stackCreateMode, initialBounds)) {
                EventBus.getDefault().send(new DockedTopTaskEvent(dragMode, initialBounds));
                showRecents(
                        false /* triggeredFromAltTab */,
                        dragMode == NavigationBarGestureHelper.DRAG_MODE_RECENTS,
                        false /* animate */,
                        true /* launchedWhileDockingTask*/,
                        false /* fromHome */,
                        DividerView.INVALID_RECENTS_GROW_TARGET);
        }
    }

    /*SPRD bug 695538:Split screen feature*/
    public void startSprdRecentActivity(){
        Intent intent = new Intent();
        intent.setClassName("com.android.systemui",
                "com.sprd.recents.stackdivider.SprdRecentsActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        ActivityOptions options = ActivityOptions.makeBasic();
        //options.setLaunchStackId(FULLSCREEN_WORKSPACE_STACK_ID);
        Recents.getSystemServices().startActivityAsUserAsync(intent, options);
    }
    /*@}*/
}
