package com.jmnext.desktop
import com.jmnext.wallpaper.WallpaperMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.LaunchedEffect

import kotlinx.coroutines.CancellationException

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 界面风格与壁纸（桌面端，1.9.x 主题移植第一步）。
 *
 * Android 端有五套风格（WindowGlass / Translucent / FlatBlur / Miuix / Material），
 * 换的不只是配色 —— 圆角、表面透明度、描边、是否模糊一起换。
 *
 * **桌面端的限制先说清**：Android/Windows 那种"实时背景模糊"（Mica/Acrylic 取窗口后面的
 * 内容做模糊）在 Compose Desktop 上拿不到 —— 它需要窗口系统配合。这里用的是等效观感的
 * 近似做法：**壁纸层自己模糊**（Skia 的 blur，能做到），面板用**半透明表面**盖在上面。
 * 结果看起来是磨砂玻璃，但严格说不是真 backdrop blur，这一点不含糊。
 *
 * 面板的透明与圆角来自 [GlassStyle]，值取自 Android 端的 Palettes.kt（近似对齐，
 * 尚未逐个 token 对齐 —— 那一步等这版观感定了再做）。
 */
enum class GlassStyle(val label: String, val surfaceAlpha: Float, val corner: Int, val blurWallpaper: Boolean) {
    WindowGlass("毛玻璃", surfaceAlpha = 0.72f, corner = 12, blurWallpaper = true),
    Translucent("半透明", surfaceAlpha = 0.55f, corner = 18, blurWallpaper = true),
    FlatBlur("扁平模糊", surfaceAlpha = 0.85f, corner = 8, blurWallpaper = true),
    Miuix("MIUI", surfaceAlpha = 0.90f, corner = 22, blurWallpaper = false),
    Material("Material", surfaceAlpha = 1.00f, corner = 12, blurWallpaper = false),
}

/** 内置壁纸：与 Android 端一样提供几套内置渐变，桌面端另可指定本地图片。 */
enum class WallpaperPreset(val label: String, val light: List<Color>, val dark: List<Color>) {
    Aurora("极光", listOf(Color(0xFFBBD9F2), Color(0xFFD9C7F0), Color(0xFFF3D7E3)),
        listOf(Color(0xFF14243A), Color(0xFF241C38), Color(0xFF3A1F35))),
    Sand("沙丘", listOf(Color(0xFFF6E7D3), Color(0xFFEBD9C4), Color(0xFFD9E3E8)),
        listOf(Color(0xFF2A241C), Color(0xFF3A3226), Color(0xFF1E2A30))),
    Mint("薄荷", listOf(Color(0xFFD6F0E4), Color(0xFFCDE4F2), Color(0xFFE8E6F5)),
        listOf(Color(0xFF16281F), Color(0xFF152430), Color(0xFF211F33))),
    Ink("水墨", listOf(Color(0xFFEDEFF2), Color(0xFFDDE2E8), Color(0xFFF5F6F8)),
        listOf(Color(0xFF101214), Color(0xFF1A1D21), Color(0xFF0C0E10))),
}

/**
 * 当前外观设置（全局可读，界面侧栏可改）。
 *
 * 每个字段的 setter 顺手把值写进本地存储 —— 这样界面侧只管赋值，
 * 不必记得调用保存；重启后自动恢复。
 */
object Appearance {
    private val prefs = PreferencesKeyValueStore("jm_appearance")

    private var _style by mutableStateOf(
        GlassStyle.entries.firstOrNull { it.name == prefs.getString("style", null) } ?: GlassStyle.WindowGlass,
    )

    var style: GlassStyle
        get() = _style
        set(value) { _style = value; prefs.putString("style", value.name) }

    private var _preset by mutableStateOf(
        WallpaperPreset.entries.firstOrNull { it.name == prefs.getString("preset", null) } ?: WallpaperPreset.Aurora,
    )

    var preset: WallpaperPreset
        get() = _preset
        set(value) { _preset = value; prefs.putString("preset", value.name) }

    /** 用户指定的本地图片路径；为空表示用内置渐变。 */
    private var _wallpaperPath by mutableStateOf(prefs.getString("wallpaperPath", null))

    var wallpaperPath: String?
        get() = _wallpaperPath
        set(value) {
            _wallpaperPath = value
            if (value == null) prefs.remove("wallpaperPath") else prefs.putString("wallpaperPath", value)
        }

    /** 用户是否显式选过深浅色。没选过时跟随系统（由 BlogTheme 判断）。 */
    var userChoseDark: Boolean = prefs.getString("userChoseDark", null) == "true"

    private var _dark by mutableStateOf(prefs.getString("dark", null) == "true")

    var dark: Boolean
        get() = _dark
        set(value) {
            _dark = value
            userChoseDark = true
            prefs.putString("dark", value.toString())
            prefs.putString("userChoseDark", "true")
        }

    /** 壁纸模糊半径（dp）。观感很主观，所以交给用户调而不是我定死。 */
    private var _wallpaperBlur by mutableStateOf(prefs.getString("wallpaperBlur", null)?.toIntOrNull() ?: 5)

    var wallpaperBlur: Int
        get() = _wallpaperBlur
        set(value) { _wallpaperBlur = value; prefs.putString("wallpaperBlur", value.toString()) }

    /** 面板透明度倍率：在所选风格的基础上再整体调浓/调淡。 */
    private var _alphaScale by mutableStateOf(prefs.getString("alphaScale", null)?.toFloatOrNull() ?: 0.17f)

    var alphaScale: Float
        get() = _alphaScale
        set(value) { _alphaScale = value; prefs.putString("alphaScale", value.toString()) }

    /** 实际生效的面板透明度（风格值 × 倍率，并夹在合理区间内）。 */
    val effectiveAlpha: Float get() = (style.surfaceAlpha * alphaScale).coerceIn(0f, 1f)

    /**
     * 壁纸压暗百分比（0..80）。
     *
     * 用途：浅色壁纸配浅色文字会看不清，压暗一层就能提对比度；
     * 上限取 80 而不是 100 —— 到 100 壁纸等于纯黑，那还不如直接用纯色背景。
     */
    private var _dim by mutableStateOf(prefs.getString("dim", null)?.toIntOrNull() ?: 0)

    var dim: Int
        get() = _dim
        set(value) { _dim = value; prefs.putString("dim", value.toString()) }
}

/**
 * 壁纸层。放在所有内容下面，铺满窗口。
 *
 * 图片壁纸用 Skiko 的 blur 模糊 —— 这是"玻璃"观感的关键：
 * 壁纸本身糊掉之后，上面盖半透明面板才像磨砂玻璃，否则只是一层蒙版。
 */
@Composable
fun WallpaperLayer(modifier: Modifier = Modifier) {
    // 纯色底：设 JMNEXT_WALLPAPER=off 时不画渐变壁纸，便于单独看玻璃与描边的效果。
    if (System.getenv("JMNEXT_WALLPAPER")?.lowercase() in setOf("off", "none", "0")) {
        Box(
            modifier
                .fillMaxSize()
                .background(if (Appearance.dark) Color(0xFF16181E) else Color(0xFFF6F7FA)),
        )
        return
    }
    val preset = Appearance.preset
    val dark = Appearance.dark
    val colors = if (dark) preset.dark else preset.light
    // Glass blur needs more than the slider value: 5dp reads as "slightly soft" at desktop size.
    // Desktop windows are large and the wallpaper has fine detail, so boost it.
    val blurBoost = if (Appearance.style.blurWallpaper) 1.8f else 0f
    val blurRadius = Appearance.wallpaperBlur.toFloat() * blurBoost

    // 在线壁纸：进入非纯渐变模式时先取一张；设了自动轮换就按间隔换。
    // 键里带 mode 与间隔，改了任一项就重启计时（间隔改成 0 只手动时不再循环）。
    val remote = RemoteWallpaper.state
    LaunchedEffect(remote.mode, remote.intervalMinutes) {
        if (remote.mode == WallpaperMode.Off) return@LaunchedEffect
        if (remote.url.isNullOrBlank()) RemoteWallpaper.next()
        val minutes = remote.intervalMinutes
        while (minutes > 0) {
            kotlinx.coroutines.delay(minutes * 60_000L)
            RemoteWallpaper.next()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            // 告知当前窗口是不是竖长比例：Bing 的尺寸段据此取 1080x1920 或 1920x1080。
            // 这不是 Compose 状态（普通可变字段），在布局回调用它不会引发重组循环。
            .onSizeChanged { RemoteWallpaper.portraitHint = it.height > it.width },
    ) {
        if (blurRadius > 0f) {
            // 模糊会把边缘透出底色，所以画得比窗口大一圈
            Box(
                Modifier
                    .fillMaxSize()
                    .blur(blurRadius.dp)
                    .background(Brush.linearGradient(colors)),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)).background(Color.Black.copy(alpha = Appearance.dim / 100f)))
        }
        // 在线壁纸（模式非纯渐变且已取到地址时）。优先于本地图片：
        // 在外观页选了在线来源会自动清掉本地路径，反之选预设会把模式设回纯渐变，
        // 所以正常情况下两者不会同时存在；这里再按"在线优先"兜一层，避免两张图叠着打架。
        if (remote.showsImage) {
            val remoteImage = rememberRemoteImage(remote.url)
            if (remoteImage != null) {
                androidx.compose.foundation.Image(
                    bitmap = remoteImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }

        // 用户指定了图片就用它（叠在渐变上，透明处仍见渐变）。
        // **本地解码**：直接读文件字节交给 Skiko，不走网络加载器 ——
        // 之前用 file:// 借网络加载器读，那是为 HTTP 写的，读不到本地文件。
        Appearance.wallpaperPath?.let { path ->
            val image = remember(path) {
                runCatching {
                    val bytes = File(path).readBytes()
                    org.jetbrains.skia.Image.makeFromEncoded(bytes)
                        .toComposeImageBitmap()
                }.onFailure {
                    if (it is CancellationException) return@onFailure
                    Log.error("外观", "本地壁纸解码失败：$path", it) }.getOrNull()
            }
            if (image != null) {
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }

        // 压暗层必须盖在**所有**壁纸内容之上（渐变与本地图片之后）。
        // 原先我把它挂在渐变那一层里，于是用了本地图片壁纸时它被图片盖住 —— 表现成"压暗没生效"。
        if (Appearance.dim > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = Appearance.dim / 100f)),
            )
        }
        // 按风格压暗壁纸：Android 的 wallpaperScrim。没有这层时，浅色文字压在高对比
        // 二次元图上会糊成一片（截图里顶栏那行「未登录」就是这样）。
        val scrim = LocalJmSpec.current.wallpaperScrim
        if (scrim > 0f && (remote.showsImage || Appearance.wallpaperPath != null)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim)),
            )
        }
    }
}

/**
 * 玻璃面板：半透明表面 + 描边 + **上沿内高光**。
 *
 * 内高光是让面板"立起来"的关键细节：真实玻璃的边缘会反光，只靠半透明填充
 * 看起来像一层灰蒙版。这里在顶边画一道渐隐的亮线，成本几乎为零。
 */
@Composable
fun Modifier.glassPanel(
    alpha: Float = Appearance.effectiveAlpha,
    corner: Int = Appearance.style.corner,
    shape: Shape = RoundedCornerShape(corner.dp),
): Modifier = this
    .clip(shape)
    .background(
        MaterialThemeSurface(alpha),
    )
    .drawBehind {
        // 上沿高光：从左上稍亮、向右渐隐
        val highlight = if (Appearance.dark) Color(0x33FFFFFF) else Color(0x8CFFFFFF)
        drawLine(
            brush = Brush.horizontalGradient(listOf(highlight, Color.Transparent)),
            start = androidx.compose.ui.geometry.Offset(0f, 0.5f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0.5f),
            strokeWidth = 1f,
        )
    }
    .border(1.dp, MaterialThemeStroke(), shape)

/** 面板底色：浅色用白、深色用近黑，透明度由风格决定。 */
@Composable
private fun MaterialThemeSurface(alpha: Float): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = alpha)

/** 描边色：与博客令牌一致（浅色 rgba(15,23,42,0.09)、深色 白 10%）。 */
@Composable
private fun MaterialThemeStroke(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.outline
