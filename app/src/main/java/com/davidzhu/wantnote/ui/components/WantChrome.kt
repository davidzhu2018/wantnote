package com.davidzhu.wantnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.davidzhu.wantnote.data.LiquidGlassSurfaceRole
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.Calendar

/**
 * 0.13.0：Miuix（小米 HyperOS 风）chrome 门面。
 *
 * 全部 shell / 弹窗 / 按钮 / 输入框 / 开关 / 分段控件 / 菜单 / 进度条都从这里出，
 * 内部统一用 Miuix 0.9.4 组件与 `MiuixTheme` 色板，避免出现「一半 Material 3、
 * 一半 Miuix」的混合外观。自定义版面（Markdown 编辑器 / 预览、@ 双链、洞察图谱、
 * 主页卡片）不走这里，继续用主题 token 自绘。
 *
 * 门面刻意吸收 Material 3 调用点上常见的 `colors` / `shape` / `elevation` 之类
 * 参数（接受但忽略），这样调用点可以保持原样，配色永远由 `MiuixTheme` 决定，
 * 不会残留 Material 组件的视觉。
 */

/** 弹窗按钮是否撑满一行（由 [WantAlertDialog] 提供）。 */
private val LocalFillDialogButtons = staticCompositionLocalOf { false }

/** Miuix 版 Scaffold：负责状态栏 / 导航栏 inset、Snackbar 与 overlay 弹层宿主。 */
@Composable
fun WantScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.background,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    @Suppress("UNUSED_PARAMETER") contentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") floatingActionButtonPosition: Any? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/** Miuix 顶栏（SmallTopAppBar）+ 返回键 / 动作区。 */
@Composable
fun WantTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: Any? = null,
    @Suppress("UNUSED_PARAMETER") windowInsets: Any? = null,
    @Suppress("UNUSED_PARAMETER") expandedHeight: Any? = null,
) {
    SmallTopAppBar(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        navigationIcon = navigationIcon ?: {},
        actions = actions,
    )
}

/** 带返回键的 Miuix 顶栏。 */
@Composable
fun WantBackTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    WantTopBar(
        title = title,
        subtitle = subtitle,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
        actions = actions,
    )
}

/**
 * Miuix 弹窗（WindowDialog）：标题 / 正文 / 底部按钮。
 *
 * 正文可滚动并限制高度，标签、卡片盒这类长列表不会撑破屏幕。
 */
@Composable
fun WantAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") icon: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") textContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") properties: Any? = null,
) {
    // 0.14.0 双保险：升级 activity-compose 1.13.0 后正常由宿主窗口提供
    // NavigationEventDispatcherOwner；此处兜底防止个别定制窗口返回 null 导致闪退。
    val existingOwner = LocalNavigationEventDispatcherOwner.current
    val fallbackOwner = remember(existingOwner) {
        existingOwner ?: object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides fallbackOwner) {
    WindowDialog(
        show = true,
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        content = {
            Column {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (title != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides MiuixTheme.colorScheme.onSurface,
                            ) {
                                title()
                            }
                        }
                    }
                    text?.invoke()
                }
                Spacer(modifier = Modifier.height(16.dp))
                CompositionLocalProvider(LocalFillDialogButtons provides true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissButton != null) {
                            Box(modifier = Modifier.weight(1f)) { dismissButton() }
                        }
                        Box(modifier = Modifier.weight(1f)) { confirmButton() }
                    }
                }
            }
        },
    )
    }
}

/** Miuix 立体液态玻璃主按钮（主题色底 + 顶部光泽 + 立体投影）。 */
@Composable
fun WantButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    shape: Shape? = null,
    contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") elevation: Any? = null,
    @Suppress("UNUSED_PARAMETER") border: Any? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val fill = LocalFillDialogButtons.current
    val btnShape = shape ?: RoundedCornerShape(13.dp)
    val baseColor = if (enabled) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.disabledPrimaryButton
    }
    val textColor = if (enabled) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.disabledOnPrimaryButton
    }
    Row(
        modifier = (if (fill) modifier.fillMaxWidth() else modifier)
            .defaultMinSize(minHeight = 42.dp)
            .liquidGlassSurface(
                shape = btnShape,
                role = LiquidGlassSurfaceRole.BUTTON,
                baseColor = baseColor,
                elevationOverride = if (enabled) 10.dp else 1.dp,
                borderWidth = 1.3.dp,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding ?: PaddingValues(horizontal = 16.dp, vertical = 10.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            content()
        }
    }
}

/** Miuix 立体液态玻璃次按钮（用于「取消 / 完成 / 工具栏」）。 */
@Composable
fun WantTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    shape: Shape? = null,
    contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") elevation: Any? = null,
    @Suppress("UNUSED_PARAMETER") border: Any? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val fill = LocalFillDialogButtons.current
    val btnShape = shape ?: RoundedCornerShape(13.dp)
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val liftedSurface = if (isDark) {
        lerp(MiuixTheme.colorScheme.surface, Color.White, 0.14f)
    } else {
        lerp(MiuixTheme.colorScheme.surface, Color.White, 0.78f)
    }
    val baseColor = if (enabled) liftedSurface else MiuixTheme.colorScheme.surfaceContainer
    val textColor = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurfaceSecondary
    }
    Row(
        modifier = (if (fill) modifier.fillMaxWidth() else modifier)
            .defaultMinSize(minHeight = 42.dp)
            .liquidGlassSurface(
                shape = btnShape,
                role = LiquidGlassSurfaceRole.BUTTON,
                baseColor = baseColor,
                elevationOverride = if (enabled) 8.dp else 1.dp,
                borderWidth = 1.2.dp,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding ?: PaddingValues(horizontal = 16.dp, vertical = 10.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            content()
        }
    }
}

/** Miuix 输入框（浮动标签 / 聚焦描边 / 圆角底色）。 */
@Composable
fun WantTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    @Suppress("UNUSED_PARAMETER") prefix: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") suffix: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: Any? = null,
) {
    val fieldColors = TextFieldDefaults.textFieldColors(
        backgroundColor = if (isError) {
            MiuixTheme.colorScheme.errorContainer
        } else {
            MiuixTheme.colorScheme.secondaryContainer
        },
        borderColor = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
        labelColor = MiuixTheme.colorScheme.onSecondaryContainer,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            colors = fieldColors,
            label = label.ifEmpty { placeholder },
            useLabelAsPlaceholder = label.isEmpty(),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
        if (supportingText != null) {
            Box(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) { supportingText() }
        }
    }
}

/** Miuix 开关。 */
@Composable
fun WantSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: Any? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/** Miuix 勾选框（Material 的布尔签名，内部转 ToggleableState）。 */
@Composable
fun WantCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: Any? = null,
) {
    Checkbox(
        state = androidx.compose.ui.state.ToggleableState(checked),
        onClick = if (onCheckedChange != null && enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * 选择用的胶囊 chip（Miuix 没有 chip 组件，按 HyperOS 的胶囊样式用主题色自绘）。
 * 保留 composable label，方便直接替换原来的 FilterChip 调用点。
 */
@Composable
fun WantFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") trailingIcon: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") avatar: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    @Suppress("UNUSED_PARAMETER") border: Any? = null,
    @Suppress("UNUSED_PARAMETER") elevation: Any? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: Any? = null,
) {
    val container = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val content = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurfaceContainer
    }
    Row(
        modifier = modifier
            .liquidGlassSurface(
                shape = RoundedCornerShape(50),
                role = LiquidGlassSurfaceRole.CHIP,
                baseColor = container,
                elevationOverride = if (selected) 7.dp else 4.dp,
                borderWidth = 1.dp,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            leadingIcon?.invoke()
            label()
        }
    }
}

/** 字符串版胶囊 chip。 */
@Composable
fun WantChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WantFilterChip(selected = selected, onClick = onClick, label = { Text(text) }, modifier = modifier)
}

/** Miuix 分段控件容器（替代 Material TabRow）。 */
@Composable
fun WantTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") contentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") indicator: Any? = null,
    @Suppress("UNUSED_PARAMETER") divider: Any? = null,
    tabs: @Composable RowScope.() -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val trackColor = if (isDark) {
        lerp(MiuixTheme.colorScheme.background, Color.Black, 0.25f)
    } else {
        lerp(MiuixTheme.colorScheme.surfaceContainer, Color.Black, 0.08f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50),
            )
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs,
        )
    }
}

/** Miuix 分段控件的一个页签（选中态为主题色立体胶囊，非选中态扁平弱化，对比清晰）。 */
@Composable
fun RowScope.WantTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable () -> Unit,
    @Suppress("UNUSED_PARAMETER") icon: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") selectedContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") unselectedContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") interactionSource: Any? = null,
) {
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurfaceSecondary
    }
    val tabModifier = if (selected) {
        modifier
            .weight(1f)
            .liquidGlassSurface(
                shape = RoundedCornerShape(50),
                role = LiquidGlassSurfaceRole.BUTTON,
                baseColor = MiuixTheme.colorScheme.primary,
                elevationOverride = 6.dp,
                borderWidth = 1.dp,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    } else {
        modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    }
    Box(
        modifier = tabModifier,
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            text()
        }
    }
}

/** Miuix 风格下拉菜单（圆角卡片 + 可滚动条目）。 */
@Composable
fun WantDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") offset: Any? = null,
    @Suppress("UNUSED_PARAMETER") scrollState: Any? = null,
    @Suppress("UNUSED_PARAMETER") properties: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = modifier
                .widthIn(min = 180.dp, max = 320.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    role = LiquidGlassSurfaceRole.DIALOG,
                    baseColor = MiuixTheme.colorScheme.surface,
                )
                .padding(vertical = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/** 下拉菜单条目。 */
@Composable
fun WantDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") trailingIcon: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: Any? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingIcon?.invoke()
        text()
    }
}

/** Miuix 线性进度条。 */
@Composable
fun WantLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    @Suppress("UNUSED_PARAMETER") color: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") trackColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") strokeCap: Any? = null,
    @Suppress("UNUSED_PARAMETER") gapSize: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") drawStopIndicator: Any? = null,
) {
    LinearProgressIndicator(modifier = modifier, progress = progress?.invoke())
}

/** Miuix 环形进度条。 */
@Composable
fun WantCircularProgressIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    @Suppress("UNUSED_PARAMETER") color: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") trackColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") strokeCap: Any? = null,
) {
    CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)
}

/** Miuix 表面容器（Material Surface 的参数签名，多出的 elevation/border 忽略）。 */
@Composable
fun WantSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    color: Color = MiuixTheme.colorScheme.surface,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") shadowElevation: Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = content,
        )
    }
}

/** Miuix 列表条目（Material ListItem 的参数签名）。 */
@Composable
fun WantListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") shadowElevation: Dp = 0.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingContent?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            overlineContent?.invoke()
            headlineContent()
            supportingContent?.invoke()
        }
        trailingContent?.invoke()
    }
}

/** Miuix 底部导航栏。 */
@Composable
fun WantNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MiuixTheme.colorScheme.surface,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") windowInsets: Any? = null,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(modifier = modifier, color = containerColor, content = content)
}

/** Miuix 底部导航栏条目（ImageVector + 字符串标签）。 */
@Composable
fun RowScope.WantNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = label,
        modifier = modifier,
        enabled = enabled,
    )
}

/** Miuix 悬浮操作按钮。 */
@Composable
fun WantFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = MiuixTheme.colorScheme.primary,
    contentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") elevation: Any? = null,
    content: @Composable () -> Unit,
) {
    val resolvedContentColor = if (contentColor != Color.Unspecified) {
        contentColor
    } else {
        MiuixTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = shape,
                role = LiquidGlassSurfaceRole.FAB,
                baseColor = containerColor,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            content()
        }
    }
}

/** 设置分组标题（Miuix SmallTitle）。 */
@Composable
fun WantSectionTitle(text: String) {
    SmallTitle(text = text)
}

/** Miuix 卡片（设置项 / 信息块）。 */
@Composable
fun WantCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.liquidGlassSurface(
            shape = RoundedCornerShape(18.dp),
            role = LiquidGlassSurfaceRole.CARD,
            baseColor = MiuixTheme.colorScheme.surfaceContainer,
        ),
        content = content,
    )
}

/** Miuix 分割线。 */
@Composable
fun WantDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier)
}

/**
 * 日期选择弹窗：三列 Miuix 滚轮（年 / 月 / 日），返回所选日期当天 00:00 的
 * 本地时间戳（毫秒）。替代 Material3 的 DatePicker，保持整套 chrome 都是 Miuix。
 */
@Composable
fun WantDatePickerDialog(
    initialMillis: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    title: String = "选择日期",
) {
    val calendar = remember(initialMillis) {
        Calendar.getInstance().apply {
            if (initialMillis != null) timeInMillis = initialMillis
        }
    }
    var year by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    val maxDay = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month - 1, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val safeDay = day.coerceAtMost(maxDay)
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    WantAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberPicker(
                    value = year,
                    onValueChange = { year = it },
                    modifier = Modifier.weight(1.4f),
                    range = 1970..thisYear + 5,
                    label = { "$it 年" },
                    visibleItemCount = 3,
                )
                NumberPicker(
                    value = month,
                    onValueChange = { month = it },
                    modifier = Modifier.weight(1f),
                    range = 1..12,
                    label = { "$it 月" },
                    visibleItemCount = 3,
                )
                NumberPicker(
                    value = safeDay,
                    onValueChange = { day = it },
                    modifier = Modifier.weight(1f),
                    range = 1..maxDay,
                    label = { "$it 日" },
                    visibleItemCount = 3,
                )
            }
        },
        confirmButton = {
            WantButton(onClick = {
                val picked = Calendar.getInstance().apply {
                    set(year, month - 1, safeDay, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onConfirm(picked.timeInMillis)
            }) { Text("确定") }
        },
        dismissButton = {
            WantTextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 一段可滚动的固定高度区域（弹窗内长列表用）。 */
@Composable
fun WantScrollColumn(
    modifier: Modifier = Modifier,
    maxHeight: Dp = 280.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/** 小圆形色块（标签 / 主题预览用）。 */
@Composable
fun WantColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** 固定宽度占位。 */
@Composable
fun WantSpacerWidth(width: Dp) {
    Spacer(modifier = Modifier.width(width))
}
