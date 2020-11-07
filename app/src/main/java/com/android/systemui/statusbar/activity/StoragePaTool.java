package com.android.systemui.statusbar.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.storage.StorageManager;
import android.util.Log;

import com.android.systemui.SystemUIApplication;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class StoragePaTool {
    /**
     * 通过反射调用获取内置存储和外置sd卡根路径(通用)
     *
     * @param mContext     上下文
     * @param isRemoveAble 是否可移除，false返回内部存储，true返回外置sd卡
     * @return
     */
    public static String getStoragePath(boolean isRemoveAble) {
        StorageManager mStorageManager =
                (StorageManager) SystemUIApplication.getInstance().getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(mStorageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (isRemoveAble == removable) {
                    return path;

                }
            }
        } catch (ClassNotFoundException
                | InvocationTargetException
                | NoSuchMethodException
                | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getDVRPath() {
        try {
            Context otherContext;
            otherContext = SystemUIApplication.getInstance().createPackageContext(
                    "com.bx.carDVR", Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences preferences = otherContext.getSharedPreferences("DVR",
                    Context.MODE_PRIVATE);
            String mDVRPath = preferences.getString("SD_PATH", null);
            Log.d("storagePath", "getDVRPath: " + mDVRPath);
            return mDVRPath;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("storagePath", "onCreate:error " + e.getMessage());
            return null;
        }
    }
}
