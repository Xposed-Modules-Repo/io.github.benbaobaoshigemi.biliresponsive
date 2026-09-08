package io.github.benbaobaoshigemi.biliresponsive

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.benbaobaoshigemi.biliresponsive.ui.miuix.MiuixButton
import io.github.benbaobaoshigemi.biliresponsive.ui.miuix.MiuixCard
import io.github.benbaobaoshigemi.biliresponsive.ui.miuix.MiuixColors
import io.github.benbaobaoshigemi.biliresponsive.ui.miuix.MiuixDialog
import io.github.benbaobaoshigemi.biliresponsive.ui.miuix.MiuixPreferenceItem
import io.github.benbaobaoshigemi.biliresponsive.ui.miuix.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigManager.syncAllSettings(this)
        setContent {
            MiuixTheme {
                BiliResponsiveScreen()
            }
        }
    }
}

@Composable
fun BiliResponsiveScreen() {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) MiuixColors.BackgroundDark else MiuixColors.BackgroundLight
    val textPrimary = if (isDark) MiuixColors.TextPrimaryDark else MiuixColors.TextPrimaryLight
    val textSecondary = if (isDark) MiuixColors.TextSecondaryDark else MiuixColors.TextSecondaryLight

    var globalEnabled by remember {
        mutableStateOf(ConfigManager.getSetting(context, ConfigManager.KEY_GLOBAL_ENABLED, true))
    }
    var followingEnabled by remember {
        mutableStateOf(ConfigManager.getSetting(context, ConfigManager.KEY_FOLLOWING_OPTIMIZE, true))
    }
    var feedEnabled by remember {
        mutableStateOf(ConfigManager.getSetting(context, ConfigManager.KEY_FEED_OPTIMIZE, true))
    }
    var allowRotation by remember {
        mutableStateOf(ConfigManager.getSetting(context, ConfigManager.KEY_ALLOW_ROTATION, true))
    }

    var showRestartDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = "哔哩响应优化",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            MiuixCard {
                MiuixPreferenceItem(
                    title = "全局自适应优化",
                    summary = "竖屏自动切换为舒适单栏排版，横屏保留平板大屏",
                    checked = globalEnabled,
                    onCheckedChange = { newVal ->
                        globalEnabled = newVal
                        ConfigManager.setSetting(context, ConfigManager.KEY_GLOBAL_ENABLED, newVal)
                    },
                    showDivider = true
                )

                MiuixPreferenceItem(
                    title = "关注页单栏排版",
                    summary = "竖屏消除左侧列表挤压，恢复顶部水平滑动",
                    checked = followingEnabled,
                    onCheckedChange = { newVal ->
                        followingEnabled = newVal
                        ConfigManager.setSetting(context, ConfigManager.KEY_FOLLOWING_OPTIMIZE, newVal)
                    },
                    showDivider = true
                )

                MiuixPreferenceItem(
                    title = "首页推荐流双列",
                    summary = "竖屏消除 3 列卡片拥挤，恢复舒适双列排版",
                    checked = feedEnabled,
                    onCheckedChange = { newVal ->
                        feedEnabled = newVal
                        ConfigManager.setSetting(context, ConfigManager.KEY_FEED_OPTIMIZE, newVal)
                    },
                    showDivider = true
                )

                MiuixPreferenceItem(
                    title = "自由旋转保护",
                    summary = "禁止客户端强行锁定竖屏，支持任意姿态旋转",
                    checked = allowRotation,
                    onCheckedChange = { newVal ->
                        allowRotation = newVal
                        ConfigManager.setSetting(context, ConfigManager.KEY_ALLOW_ROTATION, newVal)
                    },
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            MiuixButton(
                text = "重启哔哩哔哩",
                onClick = {
                    showRestartDialog = true
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "配置修改即时生效，若未生效可点击一键重启应用",
                fontSize = 12.sp,
                color = textSecondary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        if (showRestartDialog) {
            MiuixDialog(
                title = "重启哔哩哔哩",
                message = "确认重启哔哩哔哩客户端以重新加载优化配置？",
                confirmText = "确认重启",
                dismissText = "取消",
                onConfirm = {
                    val success = ConfigManager.restartBiliProcess()
                    if (success) {
                        Toast.makeText(context, "已重启哔哩哔哩", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "重启失败，请检查 Root 权限", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {
                    showRestartDialog = false
                }
            )
        }
    }
}
