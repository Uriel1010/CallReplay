package com.kitsumed.shizucallrecorder.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsumed.shizucallrecorder.R

@Composable
internal fun WaveMark(size: Dp = 64.dp, square: Boolean = false) {
    val ink = if (square) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Surface(shape = if (square) RoundedCornerShape(size / 4) else CircleShape,
        color = if (square) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size)) {
        Canvas(Modifier.padding(size * .23f)) {
            listOf(.28f, .66f, 1f, .74f, .38f).forEachIndexed { index, height ->
                val x = this.size.width * (index + .5f) / 5
                val half = this.size.height * height / 2
                drawLine(ink, Offset(x, this.size.height / 2 - half),
                    Offset(x, this.size.height / 2 + half), this.size.width / 12, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun CallReplaySplashScreen() {
    val primary = MaterialTheme.colorScheme.primaryContainer
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().backgroundForSplash(background)) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight(.32f).align(Alignment.BottomCenter)) {
            val w = size.width; val h = size.height
            val wave = Path().apply {
                moveTo(0f, h * .25f)
                cubicTo(w * .35f, -h * .05f, w * .45f, h * .65f, w, h * .2f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(wave, primary.copy(alpha = .5f))
            val foregroundWave = Path().apply {
                moveTo(0f, h * .55f)
                cubicTo(w * .35f, h * .12f, w * .55f, h * .85f, w, h * .48f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(foregroundWave, primary.copy(alpha = .72f))
        }
        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            WaveMark(80.dp, square = true)
            Text("CallReplay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.replay_tagline), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun Modifier.backgroundForSplash(color: androidx.compose.ui.graphics.Color) =
    this.then(Modifier.background(color))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplayPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        TopAppBar(title = { Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.replay_back))
            } }, actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }
    }, content = content)
}

@Composable
internal fun ReplaySettingRow(title: String, detail: String? = null, icon: ImageVector, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
