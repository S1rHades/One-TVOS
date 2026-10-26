package app.pill.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// ---------- Data ----------

data class AppEntry(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val icon: ImageBitmap,
)

private fun loadApps(pm: PackageManager, self: String): List<AppEntry> {
    val seen = LinkedHashMap<String, AppEntry>()
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(query, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == self || seen.containsKey(pkg)) continue
            val bitmap = try {
                info.activityInfo.loadIcon(pm).toBitmap(160, 160).asImageBitmap()
            } catch (e: Exception) {
                continue
            }
            seen[pkg] = AppEntry(
                label = info.loadLabel(pm).toString(),
                pkg = pkg,
                component = ComponentName(pkg, info.activityInfo.name),
                icon = bitmap,
            )
        }
    }
    return seen.values.sortedBy { it.label.lowercase() }
}

// ---------- Squircle shape (superellipse) ----------

class SquircleShape(private val n: Double = 5.0) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val a = size.width / 2.0
        val b = size.height / 2.0
        val steps = 160
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2.0 / n)
            val y = b + b * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

private val Squircle = SquircleShape()
private val CardColor = Color(0x26FFFFFF)

// ---------- Activity ----------

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val apps by produceState(emptyList<AppEntry>(), refresh) {
                value = withContext(Dispatchers.Default) { loadApps(packageManager, packageName) }
            }
            var pins by remember { mutableStateOf(readPins()) }

            BackHandler(enabled = true) { /* a launcher never exits on Back */ }

            LauncherScreen(
                apps = apps,
                pins = pins,
                onOpen = { openApp(it) },
                onTogglePin = { app ->
                    pins = if (app.pkg in pins) pins - app.pkg else pins + app.pkg
                    savePins(pins)
                },
                onSettings = {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        // ignore
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun openApp(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // app was removed or disabled
        } catch (e: SecurityException) {
            // app not exported
        }
    }

    private fun readPins(): List<String> =
        getSharedPreferences("launcher", MODE_PRIVATE)
            .getString("pins", "")
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }

    private fun savePins(pins: List<String>) {
        getSharedPreferences("launcher", MODE_PRIVATE)
            .edit()
            .putString("pins", pins.joinToString(","))
            .apply()
    }
}

// ---------- UI ----------

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    pins: List<String>,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val time = DateFormat.getTimeFormat(context).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    val pinnedApps = pins.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0F1A), Color(0xFF1B2540)))),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {

            // Header: clock + date on the left, pill buttons on the right
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(time, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
                    Text(date, color = Color(0xB3FFFFFF), fontSize = 18.sp)
                }
                Pill(text = "Settings", onClick = onSettings)
            }

            Spacer(Modifier.height(28.dp))

            SectionTitle("Favorites")
            if (pinnedApps.isEmpty()) {
                Text(
                    "Hold OK on any app below to pin it here",
                    color = Color(0x99FFFFFF),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 22.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pinnedApps, key = { it.pkg }) { app ->
                        AppTile(app, 108.dp, true, { onOpen(app) }, { onTogglePin(app) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("All apps")

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(apps, key = { it.pkg }) { app ->
                    AppTile(app, 80.dp, app.pkg in pins, { onOpen(app) }, { onTogglePin(app) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (focused) Color.White else CardColor)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (focused) Color(0xFF0B0F1A) else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    iconSize: Dp,
    pinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tileScale")

    Column(
        Modifier
            .width(iconSize + 28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onOpen, onLongClick = onTogglePin)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .clip(Squircle)
                .background(CardColor)
                .border(if (focused) 3.dp else 0.dp, Color.White, Squircle),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize().padding(iconSize / 6),
            )
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            color = if (focused) Color.White else Color(0xCCFFFFFF),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
