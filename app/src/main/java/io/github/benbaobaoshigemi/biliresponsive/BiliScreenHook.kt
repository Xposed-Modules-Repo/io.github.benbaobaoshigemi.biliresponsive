package io.github.benbaobaoshigemi.biliresponsive

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object BiliScreenHook {
    private const val TAG = "BiliResponsive"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("Initializing BiliResponsive hooks in ${lpparam.processName}")

        // 1. 监听 Application 启动，注册动态广播与读取配置
        hookApplicationLifecycle()

        // 2. 核心底层劫持：kntr.common.screen.adjust.KScreenAdjustUtilsKt
        hookKScreenAdjustUtils(lpparam)

        // 3. 业务层兜底劫持：关注页 ExhibitionFragment & 首页流 PegasusDDConfigKt
        hookBusinessComponents(lpparam)

        // 4. 方向锁定防护：防止 Bilibili 在识别为手机排版时误锁 SCREEN_ORIENTATION_PORTRAIT
        hookOrientationLock(lpparam)
    }

    private fun hookApplicationLifecycle() {
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        ConfigManager.updateInTarget(app)

                        // 注册广播监听，实现实时热配置生效
                        val filter = IntentFilter(ConfigManager.ACTION_CONFIG_CHANGED)
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                ConfigManager.updateInTarget(context)
                                log("Config refreshed via broadcast in Bilibili")
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            app.registerReceiver(receiver, filter)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            log("Failed to hook Application.onCreate: ${t.message}")
        }
    }

    private fun isPortraitWindow(windowSizeClass: Any?): Boolean {
        if (windowSizeClass == null) return true
        return try {
            val minWidth = XposedHelpers.callMethod(windowSizeClass, "getMinWidthDp") as? Int ?: 0
            val minHeight = XposedHelpers.callMethod(windowSizeClass, "getMinHeightDp") as? Int ?: 0
            minWidth < minHeight
        } catch (t: Throwable) {
            true
        }
    }

    private fun hookKScreenAdjustUtils(lpparam: XC_LoadPackage.LoadPackageParam) {
        val className = "kntr.common.screen.adjust.KScreenAdjustUtilsKt"
        try {
            val wscClass = XposedHelpers.findClass("androidx.window.core.layout.WindowSizeClass", lpparam.classLoader)

            // isLargePortrait: 竖屏下绝不判定为 LargePortrait，彻底终结竖屏双栏与畸变流
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "isLargePortrait",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        param.result = false
                    }
                }
            )

            // isLarge: 竖屏恒为 false，横屏放行原生逻辑
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "isLarge",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = false
                        }
                    }
                }
            )

            // isMedium: 竖屏恒为 false，横屏放行
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "isMedium",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = false
                        }
                    }
                }
            )

            // isNormal: 竖屏恒为 true（优雅单栏、双列标准手机视图），横屏放行
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "isNormal",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = true
                        }
                    }
                }
            )

            // widthBreakPointNormal: 竖屏恒为 true
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "widthBreakPointNormal",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = true
                        }
                    }
                }
            )

            // widthBreakPointMedium: 竖屏恒为 false
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "widthBreakPointMedium",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = false
                        }
                    }
                }
            )

            // widthBreakPointLarge: 竖屏恒为 false
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "widthBreakPointLarge",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = false
                        }
                    }
                }
            )

            // getRawWindowSizeType: 竖屏返回 1 (NORMAL)
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "getRawWindowSizeType",
                wscClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = 1
                        }
                    }
                }
            )

            log("Successfully hooked KScreenAdjustUtilsKt methods")
        } catch (t: Throwable) {
            log("Error hooking KScreenAdjustUtilsKt: ${t.message}")
        }
    }

    private fun hookBusinessComponents(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. 关注页 ExhibitionFragment.bg()：竖屏下强制返回 false（禁用 WideMediatorFragment 双栏中介容器）
        try {
            XposedHelpers.findAndHookMethod(
                "com.bilibili.bplus.following.home.ui.exhibition.ExhibitionFragment",
                lpparam.classLoader,
                "bg",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled || !ConfigManager.isFollowingOptimizeEnabled) return
                        val fragment = param.thisObject
                        val context = XposedHelpers.callMethod(fragment, "getContext") as? Context ?: return
                        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                            param.result = false
                        }
                    }
                }
            )
            log("Successfully hooked ExhibitionFragment.bg()")
        } catch (t: Throwable) {
            log("ExhibitionFragment.bg hook note: ${t.message}")
        }

        // 1.1 核心修复：关注页 ExhibitionFragment 在横竖屏旋转时重置 ViewPager 偏移，根治 1260px 黑屏死区
        try {
            val exhClass = "com.bilibili.bplus.following.home.ui.exhibition.ExhibitionFragment"

            val resetHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isGlobalEnabled || !ConfigManager.isFollowingOptimizeEnabled) return
                    resetExhibitionViewPager(param.thisObject)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isGlobalEnabled || !ConfigManager.isFollowingOptimizeEnabled) return
                    clampExhibitionViewPagerScroll(param.thisObject)
                }
            }

            // 旋转时配置变更
            XposedHelpers.findAndHookMethod(
                exhClass,
                lpparam.classLoader,
                "onConfigurationChanged",
                Configuration::class.java,
                resetHook
            )

            // Tab 数据源重建与刷新逻辑
            XposedHelpers.findAndHookMethod(
                exhClass,
                lpparam.classLoader,
                "Yf",
                List::class.java,
                resetHook
            )

            // 数据集装载
            XposedHelpers.findAndHookMethod(
                exhClass,
                lpparam.classLoader,
                "fg",
                List::class.java,
                List::class.java,
                resetHook
            )

            log("Successfully hooked ExhibitionFragment rotation reset hooks")
        } catch (t: Throwable) {
            log("ExhibitionFragment reset hooks note: ${t.message}")
        }

        // 2. 首页推荐流 PegasusDDConfigKt.getRecyclerSpanCount()：竖屏下强制返回 2 列，横屏保持 4 列
        try {
            val wscClass = XposedHelpers.findClass("androidx.window.core.layout.WindowSizeClass", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                "com.bilibili.pegasus.PegasusDDConfigKt",
                lpparam.classLoader,
                "getRecyclerSpanCount",
                wscClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled || !ConfigManager.isFeedOptimizeEnabled) return
                        val wsc = param.args[0]
                        if (isPortraitWindow(wsc)) {
                            param.result = 2
                        }
                    }
                }
            )
            log("Successfully hooked PegasusDDConfigKt.getRecyclerSpanCount()")
        } catch (t: Throwable) {
            log("PegasusDDConfigKt hook note: ${t.message}")
        }
    }

    private fun resetExhibitionViewPager(fragment: Any?) {
        if (fragment == null) return
        try {
            val vp = XposedHelpers.getObjectField(fragment, "I") as? android.view.ViewGroup ?: return
            XposedHelpers.callMethod(vp, "setCurrentItem", 0, false)
            vp.scrollTo(0, 0)
        } catch (_: Throwable) {}
    }

    private fun clampExhibitionViewPagerScroll(fragment: Any?) {
        if (fragment == null) return
        try {
            val vp = XposedHelpers.getObjectField(fragment, "I") as? android.view.ViewGroup ?: return
            if (vp.scrollX < 0) {
                vp.scrollTo(0, 0)
            }
        } catch (_: Throwable) {}
    }

    private fun hookOrientationLock(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. 拦截 ScreenAdjustUtilsKt.correctOrientation: 禁止将 Activity 锁定为竖屏
        try {
            val appCompatClass = XposedHelpers.findClass("androidx.appcompat.app.AppCompatActivity", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                "com.bilibili.app.screen.adjust.utils.ScreenAdjustUtilsKt",
                lpparam.classLoader,
                "correctOrientation",
                appCompatClass,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        if (!ConfigManager.isGlobalEnabled || !ConfigManager.isAllowRotationEnabled) {
                            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                        val activity = param.args[0] as? Activity ?: return null
                        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            log("Prevented correctOrientation from locking ${activity.javaClass.simpleName} to PORTRAIT")
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                        return null
                    }
                }
            )
            log("Successfully hooked ScreenAdjustUtilsKt.correctOrientation")
        } catch (t: Throwable) {
            log("ScreenAdjustUtilsKt.correctOrientation hook note: ${t.message}")
        }

        // 2. 拦截 MainActivityV2.setRequestedOrientation: 如果传入 PORTRAIT (1)，替换为 UNSPECIFIED (-1)
        try {
            XposedHelpers.findAndHookMethod(
                "tv.danmaku.bili.MainActivityV2",
                lpparam.classLoader,
                "setRequestedOrientation",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isGlobalEnabled || !ConfigManager.isAllowRotationEnabled) return
                        val orientation = param.args[0] as Int
                        if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            log("Overriding MainActivityV2 requestedOrientation from PORTRAIT (1) to UNSPECIFIED (-1)")
                            param.args[0] = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                }
            )
            log("Successfully hooked MainActivityV2.setRequestedOrientation")
        } catch (t: Throwable) {
            log("MainActivityV2.setRequestedOrientation hook note: ${t.message}")
        }
    }

    private fun log(msg: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.i(TAG, msg, tr)
        } else {
            Log.i(TAG, msg)
        }
        try {
            val text = if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg
            XposedBridge.log("[$TAG] $text")
        } catch (_: Throwable) {}
    }
}
