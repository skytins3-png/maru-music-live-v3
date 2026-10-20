package com.maru.radio;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/** Installed metadata, so another build cannot accidentally show a stale label. */
public final class RadioBuildInfo {
    private RadioBuildInfo() {}

    public static String describe(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return "버전 " + info.versionName + " · " + context.getPackageName();
        } catch (PackageManager.NameNotFoundException error) {
            return "버전 확인 불가 · " + context.getPackageName();
        }
    }
}
