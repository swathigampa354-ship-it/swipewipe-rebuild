package com.swipewipe.rebuild.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swipewipe.rebuild.GalleryItem
import com.swipewipe.rebuild.SettingsStore
import com.swipewipe.rebuild.UserSettings
import com.swipewipe.rebuild.workers.CompressState
import kotlinx.coroutines.launch

@Composable
fun ThemeWrapper(darkMode: Int, content: @Composable () -> Unit) {
    SwipewipeTheme(darkMode) { content() }
}

@Composable
fun collectAsStateHolder(flow: kotlinx.coroutines.flow.Flow<UserSettings>): androidx.compose.runtime.State<UserSettings> {
    return flow.collectAsState(initial = UserSettings())
}

@Composable
fun viewModelHolder(): AppVm = viewModel()

@Composable
fun LockedGate(onUnlocked: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(activity)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnlocked()
        } else {
            val prompt = BiometricPrompt(
                activity,
                androidx.core.content.ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Swipewipe Rebuild")
                .setDescription("Confirm it's you to continue")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(info)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Swipewipe Rebuild")
        Text("Locked — authenticate with your device")
        TextButton(
            onClick = {
                val prompt = BiometricPrompt(
                    activity,
                    androidx.core.content.ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onUnlocked()
                        }
                    }
                )
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Swipewipe Rebuild")
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build()
                )
            },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text("Unlock again") }
    }
}

@Composable
fun AppScaffold(settings: UserSettings, vm: AppVm) {
    var tab by remember { mutableStateOf(0) }
    val galleryState by vm.gallery.collectAsState()
    val loading by vm.loading.collectAsState()
    val toastMsg by vm.toast.collectAsState()
    val groupsTick by vm.similarReloadTick.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val bookmarks by androidx.compose.runtime.produceState(
        initialValue = emptyList<com.swipewipe.rebuild.db.BookmarkEntity>(),
        groupsTick
    ) {
        vm.a.db.appDao().observeBookmarks().collect { value = it }
    }

    val stats by androidx.compose.runtime.produceState(
        initialValue = emptyList<com.swipewipe.rebuild.db.StatsEntity>()
    ) {
        vm.a.db.appDao().observeAll().collect { value = it }
    }

    val aggregates by androidx.compose.runtime.produceState(
        initialValue = emptyList<Pair<String, Int>>()
    ) {
        vm.a.db.appDao().observeAggregateAll().collect { list ->
            value = list.map { it.monthKey to it.itemCount }
        }
    }

    var groups by remember { mutableStateOf<List<List<GalleryItem>>>(emptyList()) }
    var groupsLoading by remember { mutableStateOf(false) }

    fun reloadGroups() {
        groupsLoading = true
        vm.loadGroups { result ->
            groups = result
            groupsLoading = false
        }
    }
    LaunchedEffect(groupsTick) { reloadGroups() }

    var confirmDeleteItem by remember { mutableStateOf<GalleryItem?>(null) }
    var onConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val byId = galleryState.associateBy { it.id }

    LaunchedEffect(Unit) {
        vm.loadGallery()
        vm.toast.collect { msg ->
            if (msg != null) {
                snackbar.showSnackbar(msg)
                vm.toast.value = null
            }
        }
    }

    val decideHandler: (GalleryItem, SwipeDecision, (() -> Unit)?) -> Unit =
        { item, decision, after ->
            when (decision) {
                SwipeDecision.DELETE -> {
                    if (settings.confirmDelete) {
                        confirmDeleteItem = item
                        onConfirmAction = {
                            vm.decideDelete(item) { freed ->
                                if (freed <= 0L) {
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "MediaStore denied deletion — grant 'Manage all files' or confirm in the system dialog"
                                        )
                                    }
                                } else {
                                    scope.launch {
                                        snackbar.showSnackbar("Deleted ${item.displayName}")
                                    }
                                }
                            }
                        }
                    } else {
                        vm.decideDelete(item) { freed -> after?.invoke() }
                    }
                }
                SwipeDecision.KEEP -> vm.decideKeep(item)
                SwipeDecision.BOOKMARK -> vm.decideBookmark(item)
            }
        }

    val localSettings = LocalContext.current
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    0 to "Deck" to Icons.Default.Album,
                    1 to "Groups" to Icons.Default.AutoAwesome,
                    2 to "Bookmarks" to Icons.Default.Favorite,
                    3 to "Stats" to Icons.Default.Insights,
                    4 to "Settings" to Icons.Default.Settings
                ).forEach { (pair, icon) ->
                    val (index, label) = pair
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> DeckScreen(
                    items = vm.deckOf(null),
                    loading = loading,
                    confirmDelete = settings.confirmDelete,
                    onDecide = decideHandler,
                    onRescan = { vm.loadGallery() }
                )
                1 -> GroupsScreen(
                    groups = groups,
                    scanning = groupsLoading,
                    onScan = { vm.runSimilarScan() },
                    onDeleteGroup = { group ->
                        val ids = group.map { it.id }
                        val src = group.firstOrNull()?.mediaSource ?: "image"
                        vm.bulkDelete(ids, src) { n ->
                            scope.launch {
                                snackbar.showSnackbar("Deleted $n of ${group.size} in group")
                            }
                            reloadGroups()
                        }
                    }
                )
                2 -> BookmarksScreen(
                    bookmarks = bookmarks,
                    byId = byId,
                    onRemove = { vm.removeBookmark(it) }
                )
                3 -> StatsScreen(
                    stats = stats,
                    cloudAggregates = aggregates
                )
                4 -> SettingsScreen(
                    settings = settings,
                    onSetting = { block ->
                        scope.launch {
                            block.invoke(
                                SettingsStore(
                                    localSettings.applicationContext as android.content.Context
                                )
                            )
                        }
                    },
                    onRunSimilarScan = { vm.runSimilarScan() },
                    onRunCloudSync = { vm.runCloudSync(it) },
                    onCompressNow = {
                        vm.compressNow(galleryState.filter { !it.isVideo })
                    },
                    onTestStorage = {}
                )
            }
            if (CompressState.lastProgressTotal > 0) {
                Text(
                    "compressing ${CompressState.lastProgressDone}/${CompressState.lastProgressTotal}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    color = Color.White
                )
            }
        }
    }

    confirmDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDeleteItem = null },
            title = { Text("Delete file?") },
            text = { Text("'${item.displayName}' will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteItem = null
                    onConfirmAction?.invoke()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteItem = null }) { Text("Keep") }
            }
        )
    }
}
