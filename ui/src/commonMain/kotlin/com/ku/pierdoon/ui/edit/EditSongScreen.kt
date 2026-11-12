package com.ku.pierdoon.ui.edit

import com.ku.pierdoon.ui.library.TWO_PANE_WIDTH
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.graphicsLayer
import com.ku.pierdoon.ui.PlatformStrings
import com.ku.pierdoon.ui.components.LocalArtworkLoader
import com.ku.pierdoon.ui.components.rememberImagePicker
import com.ku.pierdoon.ui.components.rememberToast
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
import androidx.compose.material3.TextField
import com.ku.pierdoon.ui.components.SoftFieldShape
import com.ku.pierdoon.ui.components.softFieldColors
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.data.SharedSettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.metadata.CoverEdit
import com.ku.pierdoon.data.metadata.MetadataCandidate
import com.ku.pierdoon.data.metadata.SaveTarget
import com.ku.pierdoon.data.metadata.TrackEdit
import com.ku.pierdoon.ui.components.Artwork
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
fun EditSongScreen(song: Song, vm: TrackEditor, onBack: () -> Unit, bottomInset: Dp) {
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val settings by SharedSettingsStore.settings.collectAsState()
    val toast = rememberToast()

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
    val hasArt = LocalArtworkLoader.current.hasArt(song.id, song.albumId)

    val coverEdit: CoverEdit = pickedCover?.let { CoverEdit.Replace(File(it)) }
        ?: if (coverRemoved) CoverEdit.Remove else CoverEdit.Keep
    fun currentEdit() = TrackEdit(title, artist, album, coverEdit)

    val coverFailed = stringResource(Res.string.cover_failed)
    fun takeCover(load: suspend () -> File?) {
        coverLoading = true
        scope.launch {
            val file = load()
            coverLoading = false
            if (file != null) {
                pickedCover = file.path
                coverRemoved = false
            } else {
                toast(coverFailed)
            }
        }
    }
    val pickImage = rememberImagePicker { image -> takeCover { vm.importCover(image) } }

    val savedText = stringResource(Res.string.saved)
    val failedText = stringResource(Res.string.save_failed)
    fun finish(ok: Boolean) {
        saving = false
        toast(if (ok) savedText else failedText)
        if (ok) onBack()
    }
    // The edit outlives a quick back press: half-written files are worse than a late toast.
    fun persist(block: suspend () -> Boolean) {
        saving = true
        scope.launch { finish(withContext(NonCancellable) { block() }) }
    }
    // Android asks the user before the app writes a file it didn't make; the edit waits for the answer.
    var pendingFileEdit by remember { mutableStateOf<TrackEdit?>(null) }
    val askWrite = rememberWriteConsent(vm) { granted ->
        val edit = pendingFileEdit
        pendingFileEdit = null
        if (granted && edit != null) persist { vm.saveToFile(song, edit) } else saving = false
    }
    fun save() {
        focus.clearFocus()
        val edit = currentEdit()
        if (target == SaveTarget.App) {
            persist { vm.saveInApp(song, edit); true }
            return
        }
        saving = true
        pendingFileEdit = edit
        askWrite(listOf(song))
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

    AppScaffold(title = stringResource(Res.string.edit_track_title), subtitle = song.title, onBack = onBack) { inner ->
        val padding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = inner.calculateTopPadding(),
            bottom = listBottomPadding(bottomInset),
        )
        // The cover and the tags.
        val tagItems: LazyListScope.() -> Unit = {
            item(key = "cover") {
                CoverHeader(
                    original = { m -> Artwork(song.id, song.albumId, m, large = true, placeholderIconSize = 64.dp) },
                    picked = pickedCover,
                    removed = coverRemoved,
                    loading = coverLoading,
                    canRemove = pickedCover != null || (!coverRemoved && hasArt),
                    onPick = {
                        pickImage()
                    },
                    onRemove = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        pickedCover = null
                        coverRemoved = true
                    },
                )
            }

            item(key = "tags") {
                Group(stringResource(Res.string.tags)) {
                    listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>({ shape ->
                        ItemContainer(shape) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            ) {
                                TagField(title, { title = it }, Res.string.tag_title)
                                TagField(artist, { artist = it }, Res.string.tag_artist)
                                TagField(album, { album = it }, Res.string.tag_album, last = true)
                            }
                        }
                    })
                }
            }
        }
        // Looking the track up online.
        val searchItems: LazyListScope.() -> Unit = {
            item(key = "search") {
                Group(stringResource(Res.string.search_online)) {
                    buildList<@Composable (androidx.compose.ui.graphics.Shape) -> Unit> {
                        add { shape ->
                            ItemContainer(shape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                                ) {
                                    TextField(
                                        shape = SoftFieldShape,
                                        colors = softFieldColors(),
                                        value = query,
                                        onValueChange = { query = it },
                                        label = { Text(stringResource(Res.string.search_query)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    ExpressiveIconButton(
                                        onClick = ::runSearch,
                                        icon = PlayerIcons.Search,
                                        contentDescription = stringResource(Res.string.search_online),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(56.dp),
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
                            SearchState.Error -> add { shape -> ItemContainer(shape) { Note(stringResource(Res.string.search_error)) } }
                            is SearchState.Done ->
                                if (s.results.isEmpty()) {
                                    add { shape -> ItemContainer(shape) { Note(stringResource(Res.string.search_nothing)) } }
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
                        stringResource(PlatformStrings.searchHint) + "\n" + stringResource(Res.string.search_sources),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
            }
        }
        // Where it's saved, and the save button.
        val saveItems: LazyListScope.() -> Unit = {
            item(key = "target") {
                val problem = PlatformStrings.fileWritesUnavailable?.let { stringResource(it) }
                    ?: if (fileWritable == false) stringResource(Res.string.save_in_file_unsupported) else null
                SaveTargetGroup(target, fileProblem = problem)
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
                                Text(stringResource(Res.string.save), style = MaterialTheme.typography.titleMedium)
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
                            Text(stringResource(Res.string.reset_edits))
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            if (maxWidth >= TWO_PANE_WIDTH) {
                // Wide: the track's own details on the left, the online search beside them.
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding = padding, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        tagItems()
                        saveItems()
                    }
                    LazyColumn(contentPadding = padding, modifier = Modifier.weight(1f).fillMaxHeight()) { searchItems() }
                }
            } else {
                LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                    tagItems()
                    searchItems()
                    saveItems()
                }
            }
        }
    }
}

/** Where an edit is saved, the app or the file, and what that means; [fileProblem] says why the file can't be written. */
@Composable
internal fun SaveTargetGroup(target: SaveTarget, fileProblem: String?) {
    Group(stringResource(Res.string.save_where)) {
        listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>({ shape ->
            ItemContainer(shape) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    ConnectedChoice(
                        options = listOf(
                            stringResource(Res.string.save_in_app),
                            stringResource(Res.string.save_in_file),
                        ),
                        selected = target.ordinal,
                        onSelect = { i -> SharedSettingsStore.update { copy(editTarget = SaveTarget.entries[i]) } },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(12.dp))
                    val isProblem = target == SaveTarget.File && fileProblem != null
                    val explanation = when {
                        target == SaveTarget.App -> stringResource(Res.string.save_in_app_desc)
                        fileProblem != null -> fileProblem
                        else -> stringResource(PlatformStrings.saveInFileDesc)
                    }
                    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = explanation to isProblem,
                        transitionSpec = { fadeIn(fadeInSpec).togetherWith(fadeOut(fadeOutSpec)) },
                        label = "saveTargetDesc",
                    ) { (text, problem) ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        })
    }
}

@Composable
internal fun CoverHeader(
    /** The cover as it is now. */
    original: @Composable (Modifier) -> Unit,
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
            // A scalloped tile of the theme colour turned behind the cover.
            Box(
                Modifier
                    .size(248.dp)
                    .graphicsLayer { rotationZ = 14f }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            val cover = Modifier.size(200.dp).clip(shape)
            Crossfade(
                targetState = picked ?: if (removed) "" else null,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "editCover",
            ) { state ->
                when (state) {
                    null -> original(cover)
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
                Text(stringResource(PlatformStrings.coverPick))
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
                    Text(stringResource(Res.string.cover_remove))
                }
            }
        }
    }
}

@Composable
internal fun TagField(
    value: String,
    onChange: (String) -> Unit,
    label: StringResource,
    last: Boolean = false,
    /** A line under the field, such as that the tracks differ here. */
    note: String? = null,
) {
    TextField(
        shape = SoftFieldShape,
        colors = softFieldColors(),
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = note?.let { { Text(it) } },
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

