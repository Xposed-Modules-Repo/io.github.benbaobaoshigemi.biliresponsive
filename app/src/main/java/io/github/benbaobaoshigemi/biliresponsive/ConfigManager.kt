package io.github.benbaobaoshigemi.biliresponsive

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

object ConfigManager {
    private const val TAG = "BiliResponsive-Config"

    const val PREF_NAME = "bili_responsive_prefs"
    const val KEY_GLOBAL_ENABLED = "bili_resp_global_enabled"
    const val KEY_FOLLOWING_OPTIMIZE = "bili_resp_following_optimize"
    const val KEY_FEED_OPTIMIZE = "bili_resp_feed_optimize"
    const val KEY_ALLOW_ROTATION = "bili_resp_allow_rotation"

    const val ACTION_CONFIG_CHANGED = "io.github.benbaobaoshigemi.biliresponsive.CONFIG_CHANGED"

    @Volatile
    var isGlobalEnabled: Boolean = true
        private set

    @Volatile
    var isFollowingOptimizeEnabled: Boolean = true
        private set

    @Volatile
    var isFeedOptimizeEnabled: Boolean = true
        private set

    @Volatile
    var isAllowRotationEnabled: Boolean = true
        private set

    /**
     * 在 tv.danmaku.bili 目标进程中刷新配置
     */
    fun updateInTarget(context: Context) {
        try {
            val cr = context.contentResolver
            isGlobalEnabled = Settings.System.getInt(cr, KEY_GLOBAL_ENABLED, 1) == 1
            isFollowingOptimizeEnabled = Settings.System.getInt(cr, KEY_FOLLOWING_OPTIMIZE, 1) == 1
            isFeedOptimizeEnabled = Settings.System.getInt(cr, KEY_FEED_OPTIMIZE, 1) == 1
            isAllowRotationEnabled = Settings.System.getInt(cr, KEY_ALLOW_ROTATION, 1) == 1
            Log.i(TAG, "Config loaded in target: global=$isGlobalEnabled, following=$isFollowingOptimizeEnabled, feed=$isFeedOptimizeEnabled, rotation=$isAllowRotationEnabled")
        } catch (t: Throwable) {
            Log.w(TAG, "Error updating config in target: ${t.message}")
        }
    }

    /**
     * App 端读取配置（本地持久化 SharedPreferences 优先）
     */
    fun getSetting(context: Context, key: String, default: Boolean = true): Boolean {
        return try {
            val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            if (sp.contains(key)) {
                sp.getBoolean(key, default)
            } else {
                val sysVal = Settings.System.getInt(context.contentResolver, key, if (default) 1 else 0) == 1
                sp.edit().putBoolean(key, sysVal).apply()
                sysVal
            }
        } catch (_: Throwable) {
            default
        }
    }

    /**
     * App 端保存配置：本地保存 + 异步同步系统设置 + 广播
     */
    fun setSetting(context: Context, key: String, value: Boolean) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(key, value).apply()

        // 发送广播
        val intent = Intent(ACTION_CONFIG_CHANGED).apply {
            putExtra("key", key)
            putExtra("value", value)
        }
        context.sendBroadcast(intent)

        // 异步同步写入 Settings.System
        Thread({
            val intVal = if (value) 1 else 0
            var directSuccess = false
            try {
                directSuccess = Settings.System.putInt(context.contentResolver, key, intVal)
            } catch (_: Throwable) {}
            if (!directSuccess) {
                runRootCmd("settings put system $key $intVal")
            }
        }, "BiliConfigSync-$key").start()
    }

    /**
     * 同步所有配置到系统
     */
    fun syncAllSettings(context: Context) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val global = sp.getBoolean(KEY_GLOBAL_ENABLED, true)
        val following = sp.getBoolean(KEY_FOLLOWING_OPTIMIZE, true)
        val feed = sp.getBoolean(KEY_FEED_OPTIMIZE, true)
        val rotation = sp.getBoolean(KEY_ALLOW_ROTATION, true)

        listOf(
            KEY_GLOBAL_ENABLED to global,
            KEY_FOLLOWING_OPTIMIZE to following,
            KEY_FEED_OPTIMIZE to feed,
            KEY_ALLOW_ROTATION to rotation
        ).forEach { (k, v) ->
            setSetting(context, k, v)
        }
    }

    /**
     * 重启 Bilibili 作用域进程
     */
    fun restartBiliProcess(): Boolean {
        val cmds = "am force-stop tv.danmaku.bili && monkey -p tv.danmaku.bili -c android.intent.category.LAUNCHER 1"
        return runRootCmd(cmds)
    }

    fun runRootCmd(cmd: String): Boolean {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "su")
        for (suPath in suPaths) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf(suPath, "-c", cmd))
                val code = process.waitFor()
                if (code == 0) return true
            } catch (_: Throwable) {}
        }
        Log.e(TAG, "Failed to run root cmd: $cmd")
        return false
    }
}
