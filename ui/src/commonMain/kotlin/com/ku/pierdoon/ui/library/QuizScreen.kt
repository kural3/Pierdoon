package com.ku.pierdoon.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.LyricQuote
import com.ku.pierdoon.QUIZ_SNIPPET_MS
import com.ku.pierdoon.QuizMode
import com.ku.pierdoon.QuizRound
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Where the quiz takes its tracks from: the whole library, the favourites, a playlist or an album. */
class QuizSource(
    val key: String,
    val title: String,
    val songs: List<Song>,
    val cover: @Composable (Modifier) -> Unit,
)

/** How the quiz's tracks are reached from it. */
class QuizActions(
    val nextRound: suspend (QuizMode, List<Song>, Set<Long>) -> QuizRound?,
    /** Plays a round's snippet, the track hidden in the players. */
    val onSnippet: (QuizRound) -> Unit,
    val onPause: () -> Unit,
    /** The round is answered: the players may show the track again. */
    val onReveal: () -> Unit,
    val onPlayQuote: (LyricQuote) -> Unit,
    val onPlay: (Song) -> Unit,
    /** The quiz is left or over: what played before it comes back. */
    val onEnd: () -> Unit,
)

private const val ROUNDS = 10

/** A played round: what it was and whether it was guessed. */
private class Answered(val round: QuizRound, val right: Boolean)

private sealed interface Phase {
    data object Setup : Phase
    /** [run] tells a game played again apart from the one before. */
    data class Game(val mode: QuizMode, val source: QuizSource, val rounds: Int, val run: Int = 0) : Phase
    data class Result(val game: Game, val answers: List<Answered>, val bestStreak: Int) : Phase
}

private sealed interface RoundState {
    data object Loading : RoundState
    data object Failed : RoundState
    data class Asking(val round: QuizRound, val picked: Song? = null) : RoundState
}

/**
 * Guess the song (issue #9): by a line of its lyrics or by ten seconds of it, from the whole
 * library, the favourites, a playlist or an album; ten rounds, then how it went.
 *
 * @param initialSource the key of the source to start with picked ("playlist:5"), or null.
 */
@Composable
fun QuizScreen(
    sources: List<QuizSource>,
    initialSource: String?,
    actions: QuizActions,
    onBack: () -> Unit,
    bottomInset: Dp,
) {
    var phase by remember { mutableStateOf<Phase>(Phase.Setup) }
    var mode by remember { mutableStateOf(QuizMode.Lyrics) }
    var sourceKey by remember { mutableStateOf(initialSource?.takeIf { k -> sources.any { it.key == k } } ?: sources.firstOrNull()?.key) }
    val end by rememberUpdatedState(actions.onEnd)
    DisposableEffect(Unit) { onDispose { end() } }

    AppScaffold(title = stringResource(Res.string.quiz_title), onBack = onBack) { inner ->
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = phase,
            contentKey = { it::class },
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "quizPhase",
            modifier = Modifier.padding(top = inner.calculateTopPadding()),
        ) { p ->
            when (p) {
                Phase.Setup -> Setup(
                    sources = sources,
                    mode = mode,
                    onMode = { mode = it },
                    sourceKey = sourceKey,
                    onSource = { sourceKey = it },
                    onStart = {
                        val source = sources.firstOrNull { it.key == sourceKey } ?: return@Setup
                        phase = Phase.Game(mode, source, minOf(ROUNDS, source.songs.size))
                    },
                    bottomInset = bottomInset,
                )
                is Phase.Game -> Game(
                    game = p,
                    actions = actions,
                    onDone = { answers, best ->
                        actions.onEnd()
                        phase = Phase.Result(p, answers, best)
                    },
                    onSetup = {
                        actions.onEnd()
                        phase = Phase.Setup
                    },
                    bottomInset = bottomInset,
                )
                is Phase.Result -> Result(
                    result = p,
                    onPlay = actions.onPlay,
                    onAgain = { phase = p.game.copy(run = p.game.run + 1) },
                    onSetup = { phase = Phase.Setup },
                    bottomInset = bottomInset,
                )
            }
        }
    }
}

// Setup ---------------------------------------------------------------------------------------------

@Composable
private fun Setup(
    sources: List<QuizSource>,
    mode: QuizMode,
    onMode: (QuizMode) -> Unit,
    sourceKey: String?,
    onSource: (String) -> Unit,
    onStart: () -> Unit,
    bottomInset: Dp,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = listBottomPadding(bottomInset, 96.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "modeTitle") { SetupTitle(stringResource(Res.string.quiz_how)) }
            item(key = "modes") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    ModeCard(
                        title = stringResource(Res.string.quiz_mode_lyrics),
                        detail = stringResource(Res.string.quiz_mode_lyrics_desc),
                        icon = PlayerIcons.Lyrics,
                        selected = mode == QuizMode.Lyrics,
                        onClick = { onMode(QuizMode.Lyrics) },
                        modifier = Modifier.weight(1f),
                    )
                    ModeCard(
                        title = stringResource(Res.string.quiz_mode_listen),
                        detail = stringResource(Res.string.quiz_mode_listen_desc),
                        icon = PlayerIcons.MusicNote,
                        selected = mode == QuizMode.Listen,
                        onClick = { onMode(QuizMode.Listen) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "sourceTitle") { SetupTitle(stringResource(Res.string.quiz_from)) }
            items(sources, key = { it.key }) { source ->
                SourceRow(source, selected = source.key == sourceKey, onClick = { onSource(source.key) })
            }
        }
        Button(
            onClick = onStart,
            enabled = sourceKey != null,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 16.dp),
        ) {
            Icon(PlayerIcons.Play, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.quiz_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SetupTitle(text: String, maxWidth: Dp = 640.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ModeCard(title: String, detail: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, effects, label = "modeBg")
    val content by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface, effects, label = "modeFg")
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .hairline(shape)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (selected) colors.primary else colors.surfaceContainerHighest),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) colors.onPrimary else colors.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = content)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f))
    }
}

@Composable
private fun SourceRow(source: QuizSource, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainer, effects, label = "sourceBg")
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        source.cover(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(Res.plurals.songs_count, source.songs.size, source.songs.size),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(PlayerIcons.Check, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp).size(24.dp))
        }
    }
}

// The game ------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Game(
    game: Phase.Game,
    actions: QuizActions,
    onDone: (List<Answered>, Int) -> Unit,
    onSetup: () -> Unit,
    bottomInset: Dp,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var state by remember(game) { mutableStateOf<RoundState>(RoundState.Loading) }
    val answers = remember(game) { mutableStateListOf<Answered>() }
    var streak by remember(game) { mutableIntStateOf(0) }
    var bestStreak by remember(game) { mutableIntStateOf(0) }
    val load: () -> Unit = {
        state = RoundState.Loading
        scope.launch {
            val used = answers.mapTo(HashSet()) { it.round.answer.id }
            state = actions.nextRound(game.mode, game.source.songs, used)?.let { RoundState.Asking(it) } ?: RoundState.Failed
        }
    }
    LaunchedEffect(game) { load() }
    // The round on screen: answered, it's still the one just played.
    val roundNumber = minOf(answers.size + if ((state as? RoundState.Asking)?.picked != null) 0 else 1, game.rounds).coerceAtLeast(1)
    val right = answers.count { it.right }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = listBottomPadding(bottomInset))
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.quiz_round, roundNumber, game.rounds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.quiz_score, right, answers.size) +
                        if (streak > 1) " · " + stringResource(Res.string.quiz_streak, streak) else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(
                progress = { answers.size / game.rounds.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = state,
            contentKey = { s -> if (s is RoundState.Asking) s.round else s },
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "quizRound",
            modifier = Modifier.widthIn(max = 560.dp),
        ) { s ->
            when (s) {
                RoundState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
                RoundState.Failed -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyMessage(
                        stringResource(if (game.mode == QuizMode.Lyrics) Res.string.quiz_empty else Res.string.quiz_empty_listen),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onSetup) { Text(stringResource(Res.string.quiz_change)) }
                        Button(onClick = load) { Text(stringResource(Res.string.quiz_retry)) }
                    }
                }
                is RoundState.Asking -> Round(
                    state = s,
                    mode = game.mode,
                    last = answers.size >= game.rounds,
                    actions = actions,
                    onPick = { song ->
                        if (s.picked != null) return@Round
                        val ok = song.id == s.round.answer.id
                        haptics.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                        answers += Answered(s.round, ok)
                        if (ok) {
                            streak++
                            bestStreak = maxOf(bestStreak, streak)
                        } else {
                            streak = 0
                        }
                        actions.onReveal()
                        state = s.copy(picked = song)
                    },
                    onNext = { if (answers.size >= game.rounds) onDone(answers.toList(), bestStreak) else load() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Round(
    state: RoundState.Asking,
    mode: QuizMode,
    last: Boolean,
    actions: QuizActions,
    onPick: (Song) -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val round = state.round
    val picked = state.picked
    val answered by rememberUpdatedState(picked != null)
    val card = RoundedCornerShape(28.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (mode) {
            QuizMode.Lyrics -> Text(
                text = stringResource(Res.string.greeting_quote, round.quote?.line.orEmpty()),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = colors.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(card)
                    .background(colors.secondaryContainer)
                    .hairline(card)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            )
            QuizMode.Listen -> {
                // Ten seconds of it, then it stops; answered, it plays on, now with its name.
                val progress = remember(round) { Animatable(0f) }
                var plays by remember(round) { mutableIntStateOf(0) }
                LaunchedEffect(round, plays) {
                    actions.onSnippet(round)
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(QUIZ_SNIPPET_MS.toInt(), easing = LinearEasing))
                    if (!answered) actions.onPause()
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(card)
                        .background(colors.secondaryContainer)
                        .hairline(card)
                        .padding(vertical = 18.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(
                            progress = { progress.value },
                            color = colors.primary,
                            modifier = Modifier.size(100.dp),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(colors.primary)
                                .clickable(enabled = picked == null) { plays++ },
                        ) {
                            Icon(
                                if (progress.value < 1f || picked != null) PlayerIcons.MusicNote else PlayerIcons.Refresh,
                                contentDescription = stringResource(Res.string.quiz_replay),
                                tint = colors.onPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(if (picked == null) Res.string.quiz_listen_hint else Res.string.quiz_listen_now),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSecondaryContainer,
                    )
                }
            }
        }
        // The verdict and what next take the question's place, so they stay in sight above the options.
        val swap = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = picked != null,
            transitionSpec = { fadeIn(swap) togetherWith fadeOut(swap) },
            contentAlignment = Alignment.Center,
            label = "verdict",
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) { done ->
            if (!done) {
                Text(
                    text = stringResource(Res.string.quiz_question),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            } else {
                Verdict(
                    right = picked?.id == round.answer.id,
                    answer = round.answer,
                    last = last,
                    onPlay = {
                        val quote = round.quote
                        if (quote != null) actions.onPlayQuote(quote) else actions.onPlay(round.answer)
                    },
                    onNext = onNext,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            round.options.forEach { song ->
                Option(
                    song = song,
                    mark = when {
                        picked == null -> Mark.None
                        song.id == round.answer.id -> Mark.Right
                        song.id == picked.id -> Mark.Wrong
                        else -> Mark.Faded
                    },
                    // By ear the covers would give it away before the snippet is heard.
                    showCover = mode == QuizMode.Lyrics || picked != null,
                    onClick = if (picked == null) { { onPick(song) } } else null,
                )
            }
        }
    }
}

@Composable
private fun Verdict(right: Boolean, answer: Song, last: Boolean, onPlay: () -> Unit, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (right) stringResource(Res.string.quiz_right) else stringResource(Res.string.quiz_wrong, answer.title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onPlay) {
                Icon(PlayerIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.quote_play))
            }
            Button(onClick = onNext) {
                Text(stringResource(if (last) Res.string.quiz_results else Res.string.quiz_next))
            }
        }
    }
}

/** [Got] and [Missed] are the results list's quieter marks. */
private enum class Mark { None, Right, Wrong, Faded, Got, Missed }

/** One track to pick: its cover, title and artist; once answered, marked right, wrong or faded. */
@Composable
private fun Option(song: Song, mark: Mark, showCover: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        when (mark) {
            Mark.Right -> colors.primary
            Mark.Wrong -> colors.errorContainer
            else -> colors.surfaceContainerHigh
        },
        effects, label = "optionBg",
    )
    val content by animateColorAsState(
        when (mark) {
            Mark.Right -> colors.onPrimary
            Mark.Wrong -> colors.onErrorContainer
            Mark.Faded -> colors.onSurface.copy(alpha = 0.5f)
            else -> colors.onSurface
        },
        effects, label = "optionFg",
    )
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .hairline(shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(10.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHighest)) {
            if (showCover) {
                Artwork(song.id, song.albumId, Modifier.fillMaxSize(), placeholderIconSize = 24.dp)
            } else {
                Icon(PlayerIcons.MusicNote, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = content.alpha * 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (mark != Mark.None && mark != Mark.Faded) {
            val good = mark == Mark.Right || mark == Mark.Got
            Icon(
                if (good) PlayerIcons.Check else PlayerIcons.Close,
                contentDescription = null,
                tint = when (mark) {
                    Mark.Got -> colors.primary
                    Mark.Missed -> colors.error
                    else -> content
                },
                modifier = Modifier.padding(horizontal = 8.dp).size(24.dp),
            )
        }
    }
}

// The result ----------------------------------------------------------------------------------------

@Composable
private fun Result(
    result: Phase.Result,
    onPlay: (Song) -> Unit,
    onAgain: () -> Unit,
    onSetup: () -> Unit,
    bottomInset: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val right = result.answers.count { it.right }
    val total = result.answers.size
    val verdict = when {
        total > 0 && right == total -> Res.string.quiz_verdict_all
        right * 10 >= total * 7 -> Res.string.quiz_verdict_great
        right * 10 >= total * 4 -> Res.string.quiz_verdict_ok
        else -> Res.string.quiz_verdict_low
    }
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = listBottomPadding(bottomInset)),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "score") {
            val card = RoundedCornerShape(32.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(card)
                    .background(colors.primaryContainer)
                    .hairline(card)
                    .padding(vertical = 28.dp, horizontal = 24.dp),
            ) {
                Text(
                    stringResource(Res.string.quiz_total, right, total),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    color = colors.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(verdict), style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer, textAlign = TextAlign.Center)
                if (result.bestStreak > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.quiz_best_streak, result.bestStreak),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onSetup) { Text(stringResource(Res.string.quiz_change)) }
                    Button(onClick = onAgain) { Text(stringResource(Res.string.quiz_again)) }
                }
            }
        }
        item(key = "tracksTitle") { SetupTitle(stringResource(Res.string.quiz_tracks), maxWidth = 560.dp) }
        items(result.answers.size) { i ->
            val a = result.answers[i]
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Option(
                    song = a.round.answer,
                    mark = if (a.right) Mark.Got else Mark.Missed,
                    showCover = true,
                    onClick = { onPlay(a.round.answer) },
                )
            }
        }
        item(key = "hint") {
            Text(
                stringResource(Res.string.quiz_tracks_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
