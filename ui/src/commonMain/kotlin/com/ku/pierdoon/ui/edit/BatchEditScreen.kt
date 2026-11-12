package com.ku.pierdoon.ui.edit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.SharedSettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.metadata.CoverEdit
import com.ku.pierdoon.data.metadata.SaveTarget
import com.ku.pierdoon.data.metadata.TrackEdit
import com.ku.pierdoon.ui.PlatformStrings
import com.ku.pierdoon.ui.components.rememberImagePicker
import com.ku.pierdoon.ui.components.rememberToast
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.MixCollage
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.settings.Group
import com.ku.pierdoon.ui.settings.ItemContainer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * One artist, album or cover for several tracks at once; titles stay each track's own. A field
 * starts filled when all the tracks agree on it, and left empty it changes nothing.
 */
@Composable
fun BatchEditScreen(songs: List<Song>, vm: TrackEditor, onBack: () -> Unit, bottomInset: Dp) {
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val settings by SharedSettingsStore.settings.collectAsState()
    val toast = rememberToast()

    val sharedArtist = remember(songs) { songs.map { it.artist }.distinct().singleOrNull().orEmpty() }
    val sharedAlbum = remember(songs) { songs.map { it.album }.distinct().singleOrNull().orEmpty() }
    var artist by rememberSaveable { mutableStateOf(sharedArtist) }
    var album by rememberSaveable { mutableStateOf(sharedAlbum) }
    var pickedCover by rememberSaveable { mutableStateOf<String?>(null) }
    var coverLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }

    val target = settings.editTarget
    // The tracks whose files take tags; null while that's being found out.
    val writable by produceState<Set<Long>?>(null, songs) {
        value = songs.filter { vm.canWriteTags(it) }.mapTo(HashSet()) { it.id }
    }

    fun editFor(song: Song) = TrackEdit(
        title = song.title,
        artist = artist.trim().ifEmpty { song.artist },
        album = album.trim().ifEmpty { song.album },
        cover = pickedCover?.let { CoverEdit.Replace(File(it)) } ?: CoverEdit.Keep,
    )
    fun changes(song: Song, edit: TrackEdit) =
        edit.artist != song.artist || edit.album != song.album || edit.cover != CoverEdit.Keep

    val coverFailed = stringResource(Res.string.cover_failed)
    val pickImage = rememberImagePicker { image ->
        coverLoading = true
        scope.launch {
            val file = vm.importCover(image)
            coverLoading = false
            if (file != null) pickedCover = file.path else toast(coverFailed)
        }
    }

    val savedAll = stringResource(Res.string.saved)
    val failedText = stringResource(Res.string.save_failed)
    val nothingText = stringResource(Res.string.batch_nothing)
    // The edits outlive a quick back press: half-written files are worse than a late toast.
    fun persist(edits: List<Pair<Song, TrackEdit>>) {
        saving = true
        done = 0
        total = edits.size
        scope.launch {
            val saved = withContext(NonCancellable) { vm.saveAll(edits, target) { done = it } }
            saving = false
            toast(
                when (saved) {
                    edits.size -> savedAll
                    0 -> failedText
                    else -> getString(Res.string.batch_saved, saved, edits.size)
                },
            )
            if (saved > 0) onBack()
        }
    }
    var pendingFileEdits by remember { mutableStateOf<List<Pair<Song, TrackEdit>>?>(null) }
    val askWrite = rememberWriteConsent(vm) { granted ->
        val edits = pendingFileEdits
        pendingFileEdits = null
        if (granted && edits != null) persist(edits)
    }
    fun save() {
        focus.clearFocus()
        val edits = songs.map { it to editFor(it) }.filter { (song, edit) -> changes(song, edit) }
            .filter { (song, _) -> target == SaveTarget.App || song.id in writable.orEmpty() }
        if (edits.isEmpty()) {
            toast(nothingText)
            return
        }
        if (target == SaveTarget.App) {
            persist(edits)
        } else {
            pendingFileEdits = edits
            askWrite(edits.map { it.first })
        }
    }

    AppScaffold(
        title = stringResource(Res.string.edit_tags),
        subtitle = pluralStringResource(Res.plurals.songs_count, songs.size, songs.size),
        onBack = onBack,
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            // A phone's width of form, even in a wide window.
            val column = Modifier.widthIn(max = 560.dp).fillMaxWidth()
            item(key = "cover") {
                Column(column) {
                    CoverHeader(
                        original = { m -> Box(m) { MixCollage(songs, Modifier.fillMaxSize()) } },
                        picked = pickedCover,
                        removed = false,
                        loading = coverLoading,
                        canRemove = pickedCover != null,
                        onPick = pickImage,
                        onRemove = {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            pickedCover = null
                        },
                    )
                }
            }
            item(key = "tags") {
                Column(column) {
                    val mixed = stringResource(Res.string.batch_mixed)
                    Group(stringResource(Res.string.tags)) {
                        listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>({ shape ->
                            ItemContainer(shape) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    TagField(artist, { artist = it }, Res.string.tag_artist, note = mixed.takeIf { sharedArtist.isEmpty() && artist.isBlank() })
                                    TagField(album, { album = it }, Res.string.tag_album, last = true, note = mixed.takeIf { sharedAlbum.isEmpty() && album.isBlank() })
                                }
                            }
                        })
                    }
                }
            }
            item(key = "target") {
                Column(column) {
                    val unwritable = writable?.let { w -> songs.count { it.id !in w } } ?: 0
                    val problem = PlatformStrings.fileWritesUnavailable?.let { stringResource(it) }
                        ?: when (unwritable) {
                            0 -> null
                            songs.size -> stringResource(Res.string.save_in_file_unsupported)
                            else -> stringResource(Res.string.batch_unwritable, unwritable, songs.size)
                        }
                    SaveTargetGroup(target, fileProblem = problem)
                }
            }
            item(key = "actions") {
                val canSave = !saving && !coverLoading && (
                    target == SaveTarget.App ||
                        (PlatformStrings.fileWritesUnavailable == null && writable?.isNotEmpty() == true)
                    )
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        save()
                    },
                    enabled = canSave,
                    shapes = ButtonDefaults.shapes(),
                    modifier = column.padding(top = 24.dp).height(56.dp),
                ) {
                    Crossfade(saving, label = "batchSaveLabel") { busy ->
                        Text(
                            if (busy) stringResource(Res.string.batch_progress, done, total) else stringResource(Res.string.save),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}
