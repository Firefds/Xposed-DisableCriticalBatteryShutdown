package com.zst.xposed.disablecriticalbatteryshutdown;

import static de.robv.android.xposed.XposedHelpers.findClass;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.XModuleResources;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XMod implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final int ID_NOTIFICATION = 123456789;
    private static final String PKG_SYSTEM = "android";
    private static final String PKG_BATTERY_SERVICE = "com.android.server.BatteryService";
    private static final String PKG_ACTIVITY_MANAGER_NATIVE = "android.app.ActivityManagerNative";

    private static XModuleResources mModRes;

    private static NotificationManager mNotifManager;
    private static Context mContext;
    private static int mNumber;

    @Override
    public void initZygote(StartupParam startupParam) {
        mModRes = XModuleResources.createInstance(startupParam.modulePath, null);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpp) {
        if (!lpp.packageName.equals(PKG_SYSTEM)) return;

        final Class<?> battService = findClass(PKG_BATTERY_SERVICE, lpp.classLoader);
        final Class<?> amn = findClass(PKG_ACTIVITY_MANAGER_NATIVE, lpp.classLoader);

        XposedBridge.hookAllConstructors(battService, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                mContext = (Context) param.args[0];
                mNotifManager = (NotificationManager) mContext
                        .getSystemService(Context.NOTIFICATION_SERVICE);
            }
        });

        XposedBridge.hookAllMethods(battService, "shutdownIfNoPowerLocked", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                final Object thiz = param.thisObject;
                final int battLevel = (Integer) XposedHelpers.callMethod(thiz, "getBatteryLevel");
                final boolean isPowered = (Boolean) XposedHelpers.callMethod(thiz, "isPowered", 7);
                final boolean isSystemReady = (Boolean) XposedHelpers.callStaticMethod(amn,
                        "isSystemReady");
                // shut down gracefully if our battery is critically low and we are not powered.
                // wait until the system has booted before attempting to display the shutdown dialog.
                if (battLevel == 0 && !isPowered && isSystemReady) {
                    notifyUser();
                    param.setResult(null);
                }
            }
        });
    }

    private void notifyUser() {
        mNumber++;
        Notification.Builder build = new Notification.Builder(mContext);
        build.setOngoing(true);
        build.setSmallIcon(android.R.drawable.ic_dialog_alert);
        build.setContentTitle(String.format(mModRes.getString(R.string.notification_title),
                mNumber));
        build.setContentText(mModRes.getString(R.string.notification_summary));

        if (Build.VERSION.SDK_INT <= 15) {
            mNotifManager.notify(ID_NOTIFICATION, build.getNotification());
        } else {
            mNotifManager.notify(ID_NOTIFICATION, build.build());
        }
    }
}
