package com.davidzhu.wantnote.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.location.LocationPoiService
import com.davidzhu.wantnote.location.MapProvider
import com.davidzhu.wantnote.location.PoiItem
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 0.18.0：编辑器位置选择弹窗（支持高德地图 / 腾讯地图 POI 检索 + 系统定位兜底 + 自定义位置）。
 */
@Composable
fun LocationPickerDialog(
    currentLocationName: String,
    settings: AppSettings,
    onPick: (PoiItem) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settingsRepo = LocalSettings.current
    val scope = rememberCoroutineScope()

    var provider by remember(settings.mapProvider) { mutableStateOf(MapProvider.fromStorage(settings.mapProvider)) }
    var apiKeyDraft by remember(settings.mapApiKey) { mutableStateOf(settings.mapApiKey) }
    var showMapConfig by remember { mutableStateOf(false) }

    var keyword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var pois by remember { mutableStateOf<List<PoiItem>>(emptyList()) }
    var sourceLabel by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun loadPois(query: String = keyword, refreshCoords: Boolean = false) {
        if (loading) return
        loading = true
        scope.launch {
            val activeCoords = if (refreshCoords || coords == null) {
                if (hasLocationPermission()) {
                    LocationPoiService.getCurrentCoordinates(context).also { coords = it }
                } else {
                    coords
                }
            } else {
                coords
            }
            val result = LocationPoiService.searchPois(
                context = context,
                provider = provider,
                apiKey = apiKeyDraft,
                keyword = query,
                coords = activeCoords,
            )
            pois = result.items
            sourceLabel = result.sourceLabel
            hint = result.hint
            loading = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            loadPois(refreshCoords = true)
        } else {
            hint = "未授予系统定位权限，仍可直接搜索地点名称或手动输入位置。"
            loadPois(refreshCoords = false)
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission()) {
            loadPois(refreshCoords = true)
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    WantAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("选择所在位置", style = MaterialTheme.typography.titleMedium)
                WantTextButton(
                    onClick = { showMapConfig = !showMapConfig },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(if (showMapConfig) "收起配置" else "地图配置", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showMapConfig) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MapProvider.entries.forEach { mp ->
                                WantFilterChip(
                                    selected = provider == mp,
                                    onClick = {
                                        provider = mp
                                        scope.launch { settingsRepo.update { it.copy(mapProvider = mp.name) } }
                                    },
                                    label = { Text(mp.label) },
                                )
                            }
                        }
                        WantTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            label = "${provider.label} Web服务 Key（选填）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WantButton(
                                onClick = {
                                    val cleanKey = apiKeyDraft.trim()
                                    scope.launch {
                                        settingsRepo.update {
                                            it.copy(mapProvider = provider.name, mapApiKey = cleanKey)
                                        }
                                        showMapConfig = false
                                        loadPois(refreshCoords = false)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("保存并刷新")
                            }
                            WantTextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyConsoleUrl)))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text("申请 Key")
                            }
                        }
                    }
                }

                // 搜索框 + 定位刷新
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WantTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        placeholder = "搜索附近地点或输入位置名",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    WantButton(
                        onClick = { loadPois(query = keyword, refreshCoords = false) },
                        enabled = !loading,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索", modifier = Modifier.size(18.dp))
                    }
                    WantTextButton(
                        onClick = {
                            keyword = ""
                            if (hasLocationPermission()) {
                                loadPois(query = "", refreshCoords = true)
                            } else {
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                        enabled = !loading,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = "重新定位", modifier = Modifier.size(18.dp))
                    }
                }

                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (currentLocationName.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onClear)
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "不显示位置（当前：$currentLocationName）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }

                    if (keyword.trim().isNotEmpty()) {
                        val customName = keyword.trim()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(
                                        PoiItem(
                                            id = "custom",
                                            name = customName,
                                            address = "",
                                            lat = coords?.first ?: 0.0,
                                            lng = coords?.second ?: 0.0,
                                        ),
                                    )
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "直接使用「$customName」",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }

                    if (loading) {
                        Text(
                            text = "正在获取附近 POI…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    } else if (pois.isEmpty()) {
                        Text(
                            text = "暂无匹配 POI，可输入关键词搜索或直接使用上方自定义位置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    } else {
                        pois.forEachIndexed { idx, poi ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(poi) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = poi.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val sub = listOf(poi.city, poi.address).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                val dist = poi.formattedDistance()
                                if (dist.isNotBlank()) {
                                    Text(
                                        text = dist,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (idx < pois.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            }
                        }
                    }
                }
                if (sourceLabel.isNotBlank()) {
                    Text(
                        text = "数据来源：$sourceLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            WantTextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
