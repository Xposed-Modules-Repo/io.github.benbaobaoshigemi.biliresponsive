package io.github.benbaobaoshigemi.biliresponsive

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        const val TAG = "BiliResponsive"
        const val TARGET_PACKAGE = "tv.danmaku.bili"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        Log.i(TAG, "Hooking into $TARGET_PACKAGE, processName=${lpparam.processName}")
        try {
            BiliScreenHook.init(lpparam)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize BiliScreenHook", t)
        }
    }
}
