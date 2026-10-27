package com.ku.pierdoon.ui.remote

import com.ku.pierdoon.ui.components.MenuAction
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.AppMenu
import com.ku.pierdoon.remote.PcDownload
import com.ku.pierdoon.data.download.findUrl
import com.ku.pierdoon.data.download.SearchSource
import com.ku.pierdoon.data.download.SearchHit
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import android.content.ClipboardManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.R
import com.ku.pierdoon.remote.FoundPc
import com.ku.pierdoon.remote.PcLibrary
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.remote.PcSong
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.RemoteImage
import com.ku.pierdoon.ui.formatDuration
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.settings.Group
import com.ku.pierdoon.ui.settings.ItemContainer
import kotlinx.coroutines.launch

/**
 * The phone as a remote for Pier.doon on a computer: pairing first, then the computer's volume
 * and its whole library. What it plays shows in the app's own player (see rememberPcPlayback).
 */
@Composable
fun PcRemoteScreen(onBack: () -> Unit, bottomInset: Dp, onSaveToPhone: ((List<PcSong>) -> Unit)? = null) {
    val paired by PcRemote.paired.collectAsStateWithLifecycle()
    val connection by PcRemote.connection.collectAsStateWithLifecycle()

    // The event stream lives only while this screen is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, paired != null) {
        var held = false
        fun hold(on: Boolean) {
            if (on == held) return
            held = on
            if (on) PcRemote.acquire() else PcRemote.release()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> hold(true)
                Lifecycle.Event.ON_STOP -> hold(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            hold(false)
        }
    }

    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val revoked = connection == PcRemote.Connection.Revoked
    AnimatedContent(
        targetState = paired?.takeIf { !revoked }?.name,
        contentKey = { it != null },
        transitionSpec = {
            (fadeIn(effects) + scaleIn(spatial, initialScale = 0.96f))
                .togetherWith(fadeOut(effects) + scaleOut(spatial, targetScale = 1.02f))
        },
        label = "pcScreen",
    ) { name ->
        if (name == null) {
            PairingScreen(onBack, bottomInset, revoked = revoked)
        } else {
            RemoteScreen(name, connection, onBack, bottomInset, onSaveToPhone)
        }
    }
}

// Pairing ----------------------------------------------------------------------------------------

@Composable
private fun PairingScreen(onBack: () -> Unit, bottomInset: Dp, revoked: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Android 17 asks the user before an app may talk to devices in the local network.
    val permission = "android.permission.ACCESS_LOCAL_NETWORK"
    fun granted() = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<FoundPc>>(emptyList()) }
    var searchRound by remember { mutableStateOf(0) }
    LaunchedEffect(allowed, searchRound) {
        if (!allowed) return@LaunchedEffect
        searching = true
        found = runCatching { PcRemote.discover() }.getOrDefault(emptyList())
        searching = false
    }

    var chosen by remember { mutableStateOf<FoundPc?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    var addressError by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var pairError by remember { mutableStateOf<Int?>(if (revoked) R.string.pc_revoked else null) }
    var pairing by remember { mutableStateOf(false) }

    AppScaffold(title = stringResource(R.string.pc_title), onBack = onBack) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "intro") {
                Box(Modifier.animateItem()) {
                    Text(
                        stringResource(R.string.pc_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            if (!allowed) {
                item(key = "permission") {
                    Box(Modifier.animateItem()) {
                        Group(null) {
                            listOf<@Composable (Shape) -> Unit>({ shape ->
                                ItemContainer(shape) {
                                    Column(Modifier.padding(20.dp)) {
                                        Text(stringResource(R.string.pc_permission), style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { request.launch(permission) }) {
                                            Text(stringResource(R.string.pc_permission_grant))
                                        }
                                    }
                                }
                            })
                        }
                    }
                }
            }
            item(key = "found") {
                Box(Modifier.animateItem()) {
                    Group(stringResource(R.string.pc_found)) {
                        buildList<@Composable (Shape) -> Unit> {
                            found.forEach { pc ->
                                add { shape ->
                                    PcRow(shape, pc, selected = chosen == pc) {
                                        chosen = pc
                                        pairError = null
                                    }
                                }
                            }
                            if (found.isEmpty()) add { shape ->
                                ItemContainer(shape) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                    ) {
                                        if (searching) {
                                            LoadingIndicator(Modifier.size(32.dp))
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Text(
                                            stringResource(if (searching || !allowed) R.string.pc_searching else R.string.pc_none_found),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            add { shape ->
                                ItemContainer(shape) {
                                    Text(
                                        stringResource(R.string.pc_search_again),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (searching) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = allowed && !searching) { searchRound++ }
                                            .padding(horizontal = 20.dp, vertical = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val pc = chosen
            if (pc != null) {
                item(key = "code") {
                    Box(Modifier.animateItem()) {
                        Group(stringResource(R.string.pc_code_title, pc.name)) {
                            listOf<@Composable (Shape) -> Unit>({ shape ->
                                ItemContainer(shape) {
                                    val submit = {
                                        if (code.length == 6 && !pairing) {
                                            pairing = true
                                            scope.launch {
                                                pairError = when (PcRemote.pair(pc, code)) {
                                                    PcRemote.PairResult.Ok -> null
                                                    PcRemote.PairResult.WrongCode -> R.string.pc_wrong_code
                                                    PcRemote.PairResult.TooManyAttempts -> R.string.pc_too_many_attempts
                                                    PcRemote.PairResult.Unreachable -> R.string.pc_unreachable
                                                }
                                                if (pairError != null) code = ""
                                                pairing = false
                                            }
                                        }
                                    }
                                    val focus = remember { FocusRequester() }
                                    LaunchedEffect(pc) { focus.requestFocus() }
                                    Column(Modifier.padding(16.dp)) {
                                        OutlinedTextField(
                                            value = code,
                                            onValueChange = { v -> code = v.filter(Char::isDigit).take(6); pairError = null },
                                            placeholder = { Text(stringResource(R.string.pc_code_hint)) },
                                            singleLine = true,
                                            isError = pairError != null,
                                            supportingText = pairError?.let { { Text(stringResource(it)) } },
                                            textStyle = MaterialTheme.typography.headlineSmall,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { submit() }),
                                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = submit, enabled = code.length == 6 && !pairing, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.pc_connect))
                                        }
                                    }
                                }
                            })
                        }
                    }
                }
            } else if (pairError != null) {
                item(key = "revoked") {
                    Box(Modifier.animateItem()) {
                        Text(
                            stringResource(pairError!!),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                        )
                    }
                }
            }
            item(key = "manual") {
                Box(Modifier.animateItem()) {
                    Group(stringResource(R.string.pc_manual)) {
                        listOf<@Composable (Shape) -> Unit>({ shape ->
                            ItemContainer(shape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                                ) {
                                    OutlinedTextField(
                                        value = address,
                                        onValueChange = { address = it.trim(); addressError = false },
                                        placeholder = { Text(stringResource(R.string.pc_manual_hint)) },
                                        singleLine = true,
                                        isError = addressError,
                                        supportingText = if (addressError) ({ Text(stringResource(R.string.pc_manual_failed)) }) else null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        enabled = address.isNotBlank(),
                                        onClick = {
                                            scope.launch {
                                                val pc = PcRemote.probe(address)
                                                addressError = pc == null
                                                if (pc != null) {
                                                    if (found.none { it.host == pc.host }) found = found + pc
                                                    chosen = pc
                                                    pairError = null
                                                }
                                            }
                                        },
                                    ) { Text(stringResource(R.string.pc_manual_check)) }
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PcRow(shape: Shape, pc: FoundPc, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pcRow",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(PlayerIcons.Desktop, contentDescription = null, tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(pc.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pc.host + if (pc.version.isNotBlank()) " · Pier.doon ${pc.version}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (selected) Icon(PlayerIcons.Check, contentDescription = null, tint = colors.primary)
    }
}

// The remote -------------------------------------------------------------------------------------

private enum class LibraryTab { Songs, Albums, Artists, Playlists }

/** An album, artist or playlist opened inside the library. */
private data class OpenCollection(val title: String, val ids: List<Long>)

/** What the "download on the computer" box is showing under the field. */
private sealed interface SendState {
    data object Idle : SendState
    data object Searching : SendState
    data class Found(val hits: List<SearchHit>) : SendState
    /** A one-line outcome: sent, the computer is too old, it didn't answer, nothing found. */
    data class Note(val text: Int, val error: Boolean) : SendState
}

@Composable
private fun RemoteScreen(
    name: String,
    connection: PcRemote.Connection,
    onBack: () -> Unit,
    bottomInset: Dp,
    onSaveToPhone: ((List<PcSong>) -> Unit)?,
) {
    val state by PcRemote.state.collectAsStateWithLifecycle()
    val library by PcRemote.library.collectAsStateWithLifecycle()
    val downloads by PcRemote.downloads.collectAsStateWithLifecycle()
    val pcApi by PcRemote.pcApi.collectAsStateWithLifecycle()
    // Whichever side speaks the older protocol misses features; say which one to update.
    val versionNote = pcApi?.let {
        when {
            it < PcRemote.API_VERSION -> R.string.pc_update_computer
            it > PcRemote.API_VERSION -> R.string.pc_update_phone
            else -> null
        }
    }
    var libraryTab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<OpenCollection?>(null) }
    var menu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    var link by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(SearchSource.YouTube) }
    var send by remember { mutableStateOf<SendState>(SendState.Idle) }
    var sentUrls by remember { mutableStateOf(emptySet<String>()) }

    fun deliver(url: String, title: String? = null, artist: String? = null) {
        scope.launch {
            send = when (PcRemote.download(url, title, artist)) {
                PcRemote.SendResult.Ok -> {
                    sentUrls = sentUrls + url
                    // Picked from the results: keep them up to pick more.
                    if (send is SendState.Found) send else SendState.Note(R.string.pc_sent, error = false)
                }
                PcRemote.SendResult.Outdated -> SendState.Note(R.string.pc_send_outdated, error = true)
                PcRemote.SendResult.Unreachable -> SendState.Note(R.string.pc_send_failed, error = true)
            }
        }
    }
    // A link sends at once; anything else is looked up on the computer first.
    fun submit() {
        val text = link.trim().takeIf { it.isNotEmpty() } ?: return
        focus.clearFocus()
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val url = findUrl(text)
        if (url != null) {
            link = ""
            deliver(url)
            return
        }
        send = SendState.Searching
        scope.launch {
            send = when (val r = PcRemote.searchDownloads(text, source)) {
                is PcRemote.Search.Found ->
                    if (r.hits.isEmpty()) SendState.Note(R.string.download_search_empty, error = false) else SendState.Found(r.hits)
                PcRemote.Search.Outdated -> SendState.Note(R.string.pc_send_outdated, error = true)
                PcRemote.Search.Failed -> SendState.Note(R.string.download_search_failed, error = true)
            }
        }
    }

    // "Share → Pier.doon → to the computer" lands here with the link.
    val pending by PcRemote.pendingDownload.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val url = pending ?: return@LaunchedEffect
        PcRemote.pendingDownload.value = null
        deliver(url)
    }

    AppScaffold(
        title = name,
        subtitle = stringResource(
            when (connection) {
                PcRemote.Connection.Connected -> R.string.pc_status_connected
                PcRemote.Connection.Offline -> R.string.pc_status_offline
                else -> R.string.pc_status_connecting
            }
        ),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(PlayerIcons.More, contentDescription = null) }
                AppMenu(
                    expanded = menu,
                    onDismiss = { menu = false },
                    groups = listOf(
                        listOf(
                            MenuAction(
                                label = stringResource(R.string.pc_forget),
                                icon = PlayerIcons.LinkOff,
                                destructive = true,
                                onClick = PcRemote::forget,
                            ),
                        ),
                    ),
                )
            }
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = listBottomPadding(bottomInset)),
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            if (versionNote != null) {
                item(key = "version") {
                    Box(Modifier.animateItem().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                        Group(null) {
                            listOf<@Composable (Shape) -> Unit>({ shape ->
                                ItemContainer(shape) {
                                    Text(
                                        stringResource(versionNote),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    )
                                }
                            })
                        }
                    }
                }
            }
            item(key = "volume") {
                Box(Modifier.animateItem().padding(horizontal = 16.dp)) {
                    Group(null) {
                        listOf<@Composable (Shape) -> Unit>({ shape ->
                            ItemContainer(shape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        if (state.muted || state.volume == 0f) PlayerIcons.VolumeOff else PlayerIcons.VolumeUp,
                                        contentDescription = stringResource(R.string.pc_volume),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Slider(
                                        value = if (state.muted) 0f else state.volume,
                                        onValueChange = PcRemote::setVolume,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        })
                    }
                }
            }
            item(key = "send") {
                Box(Modifier.animateItem().padding(horizontal = 16.dp)) {
                    SendBox(
                        link = link,
                        onLink = {
                            link = it
                            if (send is SendState.Note) send = SendState.Idle
                        },
                        source = source,
                        onSource = {
                            source = it
                            if (send !is SendState.Idle && send !is SendState.Note) submit()
                        },
                        send = send,
                        sentUrls = sentUrls,
                        onSubmit = ::submit,
                        onPick = { hit -> deliver(hit.url, hit.title, hit.artist.ifBlank { null }) },
                    )
                }
            }
            if (downloads.isNotEmpty()) {
                item(key = "downloads") {
                    Box(Modifier.animateItem().padding(horizontal = 16.dp)) {
                        Group(stringResource(R.string.pc_downloads)) {
                            downloads.take(MAX_DOWNLOADS_SHOWN).map<PcDownload, @Composable (Shape) -> Unit> { d ->
                                { shape -> DownloadRow(d, shape) }
                            }
                        }
                    }
                }
            }
            item(key = "library_gap") { Spacer(Modifier.height(20.dp)) }
            libraryItems(
                library = library,
                currentId = state.song?.id,
                tab = libraryTab,
                onTab = { libraryTab = it; open = null },
                query = query,
                onQuery = { query = it },
                open = open,
                onOpen = { open = it },
                onSave = onSaveToPhone,
            )
        }
    }
}

/** "Download on the computer": a link (or a name to look up) goes to the computer's downloads. */
@Composable
private fun SendBox(
    link: String,
    onLink: (String) -> Unit,
    source: SearchSource,
    onSource: (SearchSource) -> Unit,
    send: SendState,
    sentUrls: Set<String>,
    onSubmit: () -> Unit,
    onPick: (SearchHit) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val url = findUrl(link)
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Group(stringResource(R.string.pc_send_title)) {
        buildList<@Composable (Shape) -> Unit> {
            add { shape ->
                ItemContainer(shape, Modifier.animateContentSize(spatial)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                    ) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = onLink,
                            label = { Text(stringResource(R.string.download_link)) },
                            singleLine = true,
                            trailingIcon = {
                                if (link.isEmpty()) {
                                    IconButton(onClick = {
                                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                                        text?.let { findUrl(it.toString()) }?.let(onLink)
                                    }) {
                                        Icon(PlayerIcons.Paste, contentDescription = stringResource(R.string.download_paste))
                                    }
                                } else {
                                    IconButton(onClick = { onLink("") }) {
                                        Icon(PlayerIcons.Close, contentDescription = stringResource(R.string.cancel))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = if (url != null) ImeAction.Send else ImeAction.Search),
                            keyboardActions = KeyboardActions(onSend = { onSubmit() }, onSearch = { onSubmit() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        val ready = link.isNotBlank()
                        val container by animateColorAsState(
                            if (ready) colors.primary else colors.surfaceContainerHighest,
                            MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "sendButton",
                        )
                        ExpressiveIconButton(
                            onClick = onSubmit,
                            icon = if (url != null || !ready) PlayerIcons.Download else PlayerIcons.Search,
                            contentDescription = stringResource(if (url != null || !ready) R.string.pc_send else R.string.download_search),
                            containerColor = container,
                            contentColor = if (ready) colors.onPrimary else colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp).size(56.dp),
                        )
                    }
                    AnimatedVisibility(
                        visible = link.isNotBlank() && url == null,
                        enter = fadeIn(effects) + expandVertically(spatial),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(spatial),
                    ) {
                        ConnectedChoice(
                            options = listOf(
                                stringResource(R.string.download_source_youtube),
                                stringResource(R.string.download_source_soundcloud),
                            ),
                            selected = source.ordinal,
                            onSelect = { onSource(SearchSource.entries[it]) },
                            containerColor = colors.surfaceContainerHighest,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        )
                    }
                    AnimatedContent(
                        targetState = send.takeIf { it is SendState.Note || it is SendState.Searching },
                        transitionSpec = {
                            (fadeIn(effects) + expandVertically(spatial)).togetherWith(fadeOut(effects) + shrinkVertically(spatial))
                        },
                        label = "sendNote",
                    ) { note ->
                        when (note) {
                            SendState.Searching -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(stringResource(R.string.download_searching), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                            }
                            is SendState.Note -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    if (note.error) PlayerIcons.Close else PlayerIcons.Check,
                                    contentDescription = null,
                                    tint = if (note.error) colors.error else colors.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(note.text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (note.error) colors.error else colors.onSurface,
                                )
                            }
                            else -> Spacer(Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (send is SendState.Found) {
                send.hits.forEach { hit ->
                    add { shape -> SendHitRow(hit, shape, sent = hit.url in sentUrls) { onPick(hit) } }
                }
            }
            if (link.isEmpty() && send !is SendState.Found) {
                add { shape ->
                    ItemContainer(shape) {
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                            Tip(PlayerIcons.Smartphone, stringResource(R.string.pc_send_share))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SendHitRow(hit: SearchHit, shape: Shape, sent: Boolean, onSend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !sent, onClick = onSend)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        ) {
            RemoteImage(hit.thumbnail, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    hit.artist.ifBlank { null },
                    hit.durationSec?.let { formatDuration(it * 1000L) },
                    if (sent) stringResource(R.string.pc_sent) else null,
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val container by animateColorAsState(
                if (sent) colors.secondaryContainer else Color.Transparent,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "sent",
            )
            ExpressiveIconButton(
                onClick = { if (!sent) onSend() },
                icon = if (sent) PlayerIcons.Check else PlayerIcons.Download,
                contentDescription = stringResource(if (sent) R.string.pc_sent else R.string.pc_send),
                containerColor = container,
                contentColor = if (sent) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

/** One of the computer's downloads: what it is and how far along. */
@Composable
private fun DownloadRow(d: PcDownload, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    ItemContainer(shape, Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                d.title ?: d.url,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    d.artist,
                    when (d.status) {
                        PcDownload.Status.Queued -> stringResource(R.string.download_queued)
                        PcDownload.Status.Preparing -> stringResource(R.string.download_preparing)
                        PcDownload.Status.Downloading -> d.progress?.let {
                            stringResource(R.string.download_progress, (it * 100).toInt())
                        } ?: stringResource(R.string.download_running)
                        PcDownload.Status.Saving -> stringResource(R.string.download_saving)
                        PcDownload.Status.Done -> stringResource(R.string.download_done)
                        PcDownload.Status.Failed -> stringResource(R.string.download_fail_other)
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (d.status == PcDownload.Status.Failed) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedVisibility(d.active) {
                val progress = d.progress
                Column {
                    Spacer(Modifier.height(8.dp))
                    if (d.status == PcDownload.Status.Downloading && progress != null) {
                        val shown by animateFloatAsState(progress, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "pcDownload")
                        LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// Library ----------------------------------------------------------------------------------------

private fun LazyListScope.libraryItems(
    library: PcLibrary,
    currentId: Long?,
    tab: LibraryTab,
    onTab: (LibraryTab) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    open: OpenCollection?,
    onOpen: (OpenCollection?) -> Unit,
    onSave: ((List<PcSong>) -> Unit)?,
) {
    if (library.version < 0) {
        item(key = "lib_loading") { Hint(stringResource(R.string.pc_library_loading), Modifier.animateItem()) }
        return
    }
    if (open != null) {
        item(key = "lib_open") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem().padding(horizontal = 8.dp)) {
                ExpressiveIconButton(
                    onClick = { onOpen(null) },
                    icon = PlayerIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    open.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onSave != null && open.ids.isNotEmpty()) {
                    ExpressiveIconButton(
                        onClick = { onSave(library.resolve(open.ids)) },
                        icon = PlayerIcons.Smartphone,
                        contentDescription = stringResource(R.string.transfer_save_all_to_phone),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        songItems(library.resolve(open.ids), currentId, "open", onSave)
        return
    }
    item(key = "lib_search") {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.pc_search)) },
            leadingIcon = { Icon(PlayerIcons.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) ({
                IconButton(onClick = { onQuery("") }) { Icon(PlayerIcons.Close, contentDescription = null) }
            }) else null,
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
    if (query.isBlank()) {
        item(key = "lib_tabs") {
            // Chips that scroll sideways: four Russian labels don't fit a phone's width as segments.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                LibraryTab.entries.forEach { t ->
                    FilterChip(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        label = {
                            Text(
                                stringResource(
                                    when (t) {
                                        LibraryTab.Songs -> R.string.nav_songs
                                        LibraryTab.Albums -> R.string.nav_albums
                                        LibraryTab.Artists -> R.string.nav_artists
                                        LibraryTab.Playlists -> R.string.nav_playlists
                                    }
                                )
                            )
                        },
                    )
                }
            }
        }
    }
    val q = query.trim().lowercase()
    if (q.isNotEmpty()) {
        val hits = library.songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
        if (hits.isEmpty()) item(key = "lib_none") { Hint(stringResource(R.string.pc_nothing_found), Modifier.animateItem()) }
        songItems(hits, currentId, "hit", onSave)
        return
    }
    when (tab) {
        LibraryTab.Songs -> songItems(library.songs, currentId, "song", onSave)
        LibraryTab.Albums -> itemsIndexed(library.albums, key = { _, a -> "album_${a.id}" }) { _, a ->
            CollectionRow(
                modifier = Modifier.animateItem(),
                title = a.title,
                subtitle = a.artist.ifBlank { pluralStringResource(R.plurals.songs_count, a.songIds.size, a.songIds.size) },
                cover = a.songIds.firstOrNull()?.let(library.byId::get),
                onClick = { onOpen(OpenCollection(a.title, a.songIds)) },
            )
        }
        LibraryTab.Artists -> itemsIndexed(library.artists, key = { _, a -> "artist_${a.name}" }) { _, a ->
            CollectionRow(
                modifier = Modifier.animateItem(),
                title = a.name,
                subtitle = pluralStringResource(R.plurals.songs_count, a.songIds.size, a.songIds.size),
                cover = a.songIds.firstOrNull()?.let(library.byId::get),
                round = true,
                onClick = { onOpen(OpenCollection(a.name, a.songIds)) },
            )
        }
        LibraryTab.Playlists -> {
            item(key = "pl_favorites") {
                val title = stringResource(R.string.favorites)
                CollectionRow(
                    modifier = Modifier.animateItem(),
                    title = title,
                    subtitle = pluralStringResource(R.plurals.songs_count, library.favorites.size, library.favorites.size),
                    cover = library.favorites.firstOrNull()?.let(library.byId::get),
                    onClick = { onOpen(OpenCollection(title, library.favorites)) },
                )
            }
            itemsIndexed(library.playlists, key = { _, p -> "pl_${p.id}" }) { _, p ->
                CollectionRow(
                    modifier = Modifier.animateItem(),
                    title = p.name,
                    subtitle = pluralStringResource(R.plurals.songs_count, p.songIds.size, p.songIds.size),
                    cover = p.songIds.firstOrNull()?.let(library.byId::get),
                    onClick = { onOpen(OpenCollection(p.name, p.songIds)) },
                )
            }
        }
    }
}

private fun LazyListScope.songItems(songs: List<PcSong>, currentId: Long?, prefix: String, onSave: ((List<PcSong>) -> Unit)?) {
    val ids = songs.map { it.id }
    itemsIndexed(songs, key = { i, s -> "${prefix}_${s.id}_$i" }) { i, song ->
        var menu by remember { mutableStateOf(false) }
        Box(Modifier.animateItem()) {
            PcSongRow(
                song,
                isCurrent = song.id == currentId,
                onClick = { PcRemote.playSongs(ids, i) },
                onLongClick = { menu = true },
            )
            // Under the title, not the cover: the menu is about this one track.
            Box(Modifier.align(Alignment.BottomStart).padding(start = 88.dp)) {
                AppMenu(
                    expanded = menu,
                    onDismiss = { menu = false },
                    groups = listOf(
                        listOf(
                            MenuAction(song.title, PlayerIcons.Play, supporting = song.artist.ifBlank { null }) {
                                PcRemote.playSongs(ids, i)
                            },
                        ),
                        listOf(
                            MenuAction(stringResource(R.string.play_next), PlayerIcons.PlayNext) {
                                PcRemote.playNext(listOf(song.id))
                            },
                            MenuAction(stringResource(R.string.add_to_queue), PlayerIcons.Queue) {
                                PcRemote.addToQueue(listOf(song.id))
                            },
                        ),
                        listOfNotNull(
                            onSave?.let { save ->
                                MenuAction(stringResource(R.string.transfer_save_to_phone), PlayerIcons.Smartphone) {
                                    save(listOf(song))
                                }
                            },
                        ),
                    ).filter { it.isNotEmpty() },
                    offset = DpOffset(0.dp, (-4).dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PcSongRow(
    song: PcSong,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (isCurrent) colors.secondaryContainer else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pcSong",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .combinedClickable(
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        RemoteImage(PcRemote.coverUrl(song, THUMB_PX), Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(song.artist, formatDuration(song.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun CollectionRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    cover: PcSong?,
    onClick: () -> Unit,
    round: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        RemoteImage(
            cover?.let { PcRemote.coverUrl(it, THUMB_PX) },
            Modifier.size(56.dp).clip(if (round) CircleShape else RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(PlayerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
    )
}

private const val THUMB_PX = 160
private const val MAX_DOWNLOADS_SHOWN = 5
