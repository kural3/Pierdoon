package com.ku.pierdoon.ui.edit

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.MainViewModel
import com.ku.pierdoon.R
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.metadata.CoverEdit
import com.ku.pierdoon.data.metadata.MetadataCandidate
import com.ku.pierdoon.data.metadata.SaveTarget
import com.ku.pierdoon.data.metadata.TrackEdit
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ArtworkCache
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.FileArtwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.RemoteImage
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.formatDuration
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.settings.Group
import com.ku.pierdoon.ui.settings.ItemContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val results: List<MetadataCandidate>) : SearchState
    data object Error : SearchState
}

/**
 * Title, artist, album and cover of one track, filled by hand or from an online match, saved
 * either as the app's own records or into the file (the user picks, the choice is remembered).
 */
@Composable
fun EditSongScreen(song: Song, vm: MainViewModel, onBack: () -> Unit, bottomInset: Dp) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()

    var title by rememberSaveable { mutableStateOf(song.title) }
    var artist by rememberSaveable { mutableStateOf(song.artist) }
    var album by rememberSaveable { mutableStateOf(song.album) }
    // A picked cover waits in the cache as a ready JPEG until saved.
    var pickedCover by rememberSaveable { mutableStateOf<String?>(null) }
    var coverRemoved by rememberSaveable { mutableStateOf(false) }
    var coverLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf(listOf(song.artist, song.title).filter { it.isNotBlank() }.joinToString(" ")) }
    var search by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var applied by remember { mutableStateOf<MetadataCandidate?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val target = settings.editTarget
    val fileWritable by produceState<Boolean?>(null, song.id) { value = vm.canWriteTags(song) }
    val hasInAppEdits = remember(song) { vm.hasInAppEdits(song.id) }
    val revision = ArtworkCache.albumRevision(song.albumId)
    val hasArt by produceState(false, song.id, song.albumId, CoverStore.songVersion(song.id), revision) {
        value = ArtworkCache.load(context.applicationContext, song.id, song.albumId, large = false) != null
    }

    val coverEdit: CoverEdit = pickedCover?.let { CoverEdit.Replace(File(it)) }
        ?: if (coverRemoved) CoverEdit.Remove else CoverEdit.Keep
    fun currentEdit() = TrackEdit(title, artist, album, coverEdit)

    val coverFailed = stringResource(R.string.cover_failed)
    fun takeCover(load: suspend () -> File?) {
        coverLoading = true
        scope.launch {
            val file = load()
            coverLoading = false
            if (file != null) {
                pickedCover = file.path
                coverRemoved = false
            } else {
                Toast.makeText(context, coverFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) takeCover { vm.importCover(uri) }
    }

    val savedText = stringResource(R.string.saved)
    val failedText = stringResource(R.string.save_failed)
    fun finish(ok: Boolean) {
        saving = false
        Toast.makeText(context, if (ok) savedText else failedText, Toast.LENGTH_SHORT).show()
        if (ok) onBack()
    }
    // The edit outlives a quick back press: half-written files are worse than a late toast.
    fun persist(block: suspend () -> Boolean) {
        saving = true
        scope.launch { finish(withContext(NonCancellable) { block() }) }
    }
    var pendingFileEdit by remember { mutableStateOf<TrackEdit?>(null) }
    val writeConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val edit = pendingFileEdit
        pendingFileEdit = null
        if (r.resultCode == Activity.RESULT_OK && edit != null) persist { vm.saveToFile(song, edit) } else saving = false
    }
    fun save() {
        focus.clearFocus()
        val edit = currentEdit()
        if (target == SaveTarget.App) {
            persist { vm.saveInApp(song, edit); true }
            return
        }
        val request = vm.fileWriteRequest(song) ?: return
        saving = true
        pendingFileEdit = edit
        writeConsent.launch(IntentSenderRequest.Builder(request).build())
    }

    fun runSearch() {
        focus.clearFocus()
        if (query.isBlank()) return
        search = SearchState.Loading
        applied = null
        searchJob?.cancel()
        searchJob = scope.launch {
            try {
                // Results grow as each catalogue answers; an empty first answer isn't "nothing found" yet.
                vm.searchMetadata(query.trim(), song.durationMs).collect { if (it.isNotEmpty()) search = SearchState.Done(it) }
                if (search == SearchState.Loading) search = SearchState.Done(emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (search == SearchState.Loading) search = SearchState.Error
            }
        }
    }

    AppScaffold(title = stringResource(R.string.edit_track_title), subtitle = song.title, onBack = onBack) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            item(key = "cover") {
                CoverHeader(
                    song = song,
                    picked = pickedCover,
                    removed = coverRemoved,
                    loading = coverLoading,
                    canRemove = pickedCover != null || (!coverRemoved && hasArt),
                    onPick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemove = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        pickedCover = null
                        coverRemoved = true
                    },
                )
            }

            item(key = "tags") {
                Group(stringResource(R.string.tags)) {
                    listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>({ shape ->
                        ItemContainer(shape) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            ) {
                                TagField(title, { title = it }, R.string.tag_title)
                                TagField(artist, { artist = it }, R.string.tag_artist)
                                TagField(album, { album = it }, R.string.tag_album, last = true)
                            }
                        }
                    })
                }
            }

            item(key = "search") {
                Group(stringResource(R.string.search_online)) {
                    buildList<@Composable (androidx.compose.ui.graphics.Shape) -> Unit> {
                        add { shape ->
                            ItemContainer(shape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                                ) {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        label = { Text(stringResource(R.string.search_query)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    ExpressiveIconButton(
                                        onClick = ::runSearch,
                                        icon = PlayerIcons.Search,
                                        contentDescription = stringResource(R.string.search_online),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(top = 8.dp).size(56.dp),
                                    )
                                }
                            }
                        }
                        when (val s = search) {
                            SearchState.Idle -> Unit
                            SearchState.Loading -> add { shape ->
                                ItemContainer(shape) {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        LoadingIndicator(Modifier.size(48.dp))
                                    }
                                }
                            }
                            SearchState.Error -> add { shape -> ItemContainer(shape) { Note(stringResource(R.string.search_error)) } }
                            is SearchState.Done ->
                                if (s.results.isEmpty()) {
                                    add { shape -> ItemContainer(shape) { Note(stringResource(R.string.search_nothing)) } }
                                } else {
                                    s.results.forEach { c ->
                                        add { shape ->
                                            ResultRow(c, selected = c == applied, shape = shape) {
                                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                applied = c
                                                title = c.title
                                                artist = c.artist
                                                if (c.album.isNotBlank()) album = c.album
                                                c.coverUrl?.let { url -> takeCover { vm.downloadCover(url) } }
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = search is SearchState.Done,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Text(
                        stringResource(R.string.search_hint) + "\n" + stringResource(R.string.search_sources),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
            }

            item(key = "target") {
                Group(stringResource(R.string.save_where)) {
                    listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>({ shape ->
                        ItemContainer(shape) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                ConnectedChoice(
                                    options = listOf(
                                        stringResource(R.string.save_in_app),
                                        stringResource(R.string.save_in_file),
                                    ),
                                    selected = target.ordinal,
                                    onSelect = { i -> SettingsStore.update { it.copy(editTarget = SaveTarget.entries[i]) } },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                                Spacer(Modifier.height(12.dp))
                                val explanation = when {
                                    target == SaveTarget.App -> R.string.save_in_app_desc
                                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> R.string.save_in_file_old
                                    fileWritable == false -> R.string.save_in_file_unsupported
                                    else -> R.string.save_in_file_desc
                                }
                                val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                                val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                                AnimatedContent(
                                    targetState = explanation,
                                    transitionSpec = { fadeIn(fadeInSpec).togetherWith(fadeOut(fadeOutSpec)) },
                                    label = "saveTargetDesc",
                                ) { res ->
                                    Text(
                                        stringResource(res),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (res == R.string.save_in_file_old || res == R.string.save_in_file_unsupported) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    })
                }
            }

            item(key = "actions") {
                val canSave = !saving && !coverLoading &&
                    (target == SaveTarget.App || fileWritable == true)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            save()
                        },
                        enabled = canSave,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Crossfade(saving, label = "saveLabel") { busy ->
                            if (busy) {
                                LoadingIndicator(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp),
                                )
                            } else {
                                Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    if (hasInAppEdits) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                persist { vm.resetEdits(song); true }
                            },
                            enabled = !saving,
                        ) {
                            Icon(PlayerIcons.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reset_edits))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverHeader(
    song: Song,
    picked: String?,
    removed: Boolean,
    loading: Boolean,
    canRemove: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(40.dp)
            val cover = Modifier.size(200.dp).clip(shape)
            Crossfade(
                targetState = picked ?: if (removed) "" else null,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "editCover",
            ) { state ->
                when (state) {
                    null -> Artwork(song.id, song.albumId, cover, large = true, placeholderIconSize = 64.dp)
                    "" -> Box(
                        cover.background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            PlayerIcons.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                    else -> FileArtwork(File(state), 0L, cover, large = true, placeholderIconSize = 64.dp)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = loading,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Box(
                    Modifier.size(200.dp).clip(shape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPick, shapes = ButtonDefaults.shapes()) {
                Icon(PlayerIcons.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cover_gallery))
            }
            AnimatedVisibility(
                visible = canRemove,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    androidx.compose.animation.expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    androidx.compose.animation.shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                FilledTonalButton(onClick = onRemove, shapes = ButtonDefaults.shapes()) {
                    Icon(PlayerIcons.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cover_remove))
                }
            }
        }
    }
}

@Composable
private fun TagField(value: String, onChange: (String) -> Unit, label: Int, last: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
    )
}

@Composable
private fun ResultRow(c: MetadataCandidate, selected: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.97f)
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "resultContainer",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scaleBy(scale)
            .clip(shape)
            .background(container)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        RemoteImage(c.thumbUrl, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(c.artist, c.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (c.durationMs > 0) {
                Text(formatDuration(c.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Text(c.source, style = MaterialTheme.typography.labelSmall, color = colors.outline)
        }
    }
}

