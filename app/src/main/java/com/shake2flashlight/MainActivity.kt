package com.shake2flashlight

import android.Manifest
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    // Bumped on every resume so permission/battery warnings re-check after returning from Settings.
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ChopTheme { App(resumeTick) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // The service dies on app update or a system kill; bring it back so the "active" switch stays truthful.
        if (Prefs.get(this).getBoolean(Prefs.ENABLED, false) && !ChopService.running) ChopService.setEnabled(this, true)
    }
}

@Composable
private fun ChopTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/** Reads a value from SharedPreferences and recomposes whenever it changes. */
@Composable
private fun <T> SharedPreferences.observe(read: SharedPreferences.() -> T): State<T> {
    val state = remember { mutableStateOf(read()) }
    DisposableEffect(this) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> state.value = p.read() }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@Composable
private fun App(resumeTick: Int) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Início") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Configurações") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tab == 0) HomeScreen(resumeTick) else SettingsScreen(resumeTick)
        }
    }
}

@Composable
private fun Header(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun HomeScreen(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val enabled by prefs.observe { getBoolean(Prefs.ENABLED, false) }

    Header("Shake2Flashlight")

    val colors = if (enabled) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(enabled, role = Role.Switch) { ChopService.setEnabled(context, it) }
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_tile), null, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(if (enabled) "Chop-chop ativo" else "Chop-chop desligado", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (enabled) "Chacoalhe o celular para ligar ou desligar a lanterna, mesmo com a tela bloqueada."
                    else "Ative para usar o gesto.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }

    TorchCard()

    // Warnings re-evaluated on every resume (resumeTick) so they disappear once fixed.
    val notificationsOk = remember(resumeTick) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    if (!batteryOk) {
        WarningCard(
            "Otimização de bateria ativa",
            "A One UI pode encerrar o gesto em segundo plano. Libere o app para funcionar sempre.",
            "Liberar"
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            )
        }
    }
    if (!notificationsOk) {
        WarningCard(
            "Notificações bloqueadas",
            "Sem elas, a notificação com o botão Parar fica escondida.",
            "Permitir"
        ) { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

@Composable
private fun TorchCard() {
    val context = LocalContext.current
    val camera = remember { context.getSystemService(CameraManager::class.java) }
    val cameraId = remember {
        camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } ?: return
    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(camera) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) torchOn = enabled
            }
        }
        camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { camera.unregisterTorchCallback(callback) }
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lanterna", style = MaterialTheme.typography.titleMedium)
                Text(if (torchOn) "Acesa" else "Apagada", style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick = {
                try {
                    camera.setTorchMode(cameraId, !torchOn)
                } catch (_: CameraAccessException) {
                    // Camera in use by another app.
                }
            }) { Text(if (torchOn) "Apagar" else "Acender") }
        }
    }
}

@Composable
private fun WarningCard(title: String, text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

@Composable
private fun SettingsScreen(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val sensitivity by prefs.observe { getInt(Prefs.SENSITIVITY, 1) }
    val pocket by prefs.observe { getBoolean(Prefs.POCKET, true) }
    val vibrate by prefs.observe { getBoolean(Prefs.VIBRATE, true) }
    val autoOff by prefs.observe { getInt(Prefs.AUTO_OFF_MIN, 0) }
    val boot by prefs.observe { getBoolean(Prefs.START_ON_BOOT, false) }

    Header("Configurações")

    Section("Gesto") {
        Column(Modifier.padding(16.dp)) {
            Text("Sensibilidade", style = MaterialTheme.typography.titleMedium)
            Text(
                "Alta reconhece movimentos mais fracos; baixa evita disparos sem querer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Choices(listOf("Baixa", "Média", "Alta"), sensitivity) {
                prefs.edit().putInt(Prefs.SENSITIVITY, it).apply()
            }
        }
        SettingSwitch("Ignorar no bolso", "Não aciona com o sensor de proximidade coberto.", pocket) {
            prefs.edit().putBoolean(Prefs.POCKET, it).apply()
        }
        SettingSwitch("Vibrar ao acionar", "Vibração curta confirmando o gesto.", vibrate) {
            prefs.edit().putBoolean(Prefs.VIBRATE, it).apply()
        }
    }

    Section("Lanterna") {
        Column(Modifier.padding(16.dp)) {
            Text("Desligar sozinha depois de", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val minutes = listOf(0, 1, 5, 15)
            Choices(listOf("Nunca", "1 min", "5 min", "15 min"), minutes.indexOf(autoOff).coerceAtLeast(0)) {
                prefs.edit().putInt(Prefs.AUTO_OFF_MIN, minutes[it]).apply()
            }
        }
    }

    Section("Sistema") {
        SettingSwitch("Iniciar ao ligar o celular", "Volta a ativar o gesto depois de reiniciar.", boot) {
            prefs.edit().putBoolean(Prefs.START_ON_BOOT, it).apply()
        }
        // Android requires the foreground-service notification; only the user can hide its channel in system settings.
        val notificationShown = remember(resumeTick) {
            ChopService.ensureChannel(context)
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(ChopService.CHANNEL_ID).importance != NotificationManager.IMPORTANCE_NONE
        }
        SettingSwitch(
            "Mostrar notificação",
            if (notificationShown) "Toque para ocultar nas configurações do sistema."
            else "Oculta. O gesto continua funcionando normalmente.",
            notificationShown
        ) {
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, ChopService.CHANNEL_ID)
            )
        }
        ListItem(
            headlineContent = { Text("Botão no painel rápido") },
            supportingContent = { Text("Liga e desliga o gesto sem abrir o app.") },
            trailingContent = {
                OutlinedButton(onClick = { requestTile(context) }) { Text("Adicionar") }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        )
    }
    Spacer(Modifier.height(8.dp))
}

private fun requestTile(context: Context) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, ChopTileService::class.java),
        "Chop-chop",
        Icon.createWithResource(context, R.drawable.ic_tile),
        context.mainExecutor
    ) {}
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)
    )
}

@Composable
private fun Choices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size)
            ) { Text(label) }
        }
    }
}
