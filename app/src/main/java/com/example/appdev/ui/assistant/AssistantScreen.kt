package com.example.appdev.ui.assistant

import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appdev.R
import com.example.appdev.data.ai.AssistantPhase
import com.example.appdev.domain.ai.ChatFailure
import com.example.appdev.domain.ai.ChatMessage
import com.example.appdev.domain.ai.ChatRole
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.theme.HeatColors
import java.util.Date

@Composable
fun AssistantRoute(
    originRoute: String?,
    onBack: () -> Unit,
    viewModel: AssistantViewModel = viewModel(factory = AssistantViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val screen = ScreenContext.label(originRoute)
    AssistantScreen(
        state = state,
        screenLabel = screen,
        suggestions = ScreenContext.suggestions(originRoute),
        onBack = onBack,
        onSend = { viewModel.send(it, screen) },
        onRetry = { viewModel.retry(screen) },
        onStop = viewModel::stop,
        onClear = viewModel::clear,
        onSetup = viewModel::completeSetup,
        loadPreview = { viewModel.preview(screen) },
    )
}

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    screenLabel: String?,
    suggestions: List<ScreenContext.Suggestion>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onSetup: (String, Boolean) -> Boolean,
    loadPreview: suspend () -> String,
) {
    var showSees by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Header(
            canClear = state.messages.isNotEmpty(),
            seeing = showSees,
            showTools = state.ready,
            onBack = onBack,
            onToggleSees = { showSees = !showSees },
            onClear = onClear,
        )
        if (!state.ready) {
            SetupCard(state, onSetup)
        } else {
            if (showSees) SeesPanel(loadPreview)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty() && !state.isGenerating) {
                    EmptyState(screenLabel, suggestions, onSend)
                } else {
                    MessageList(state, onRetry)
                }
            }
            if (state.messages.isNotEmpty() && !state.isGenerating) SuggestionRow(suggestions, onSend)
            InputBar(generating = state.isGenerating, onSend = onSend, onStop = onStop)
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(
    canClear: Boolean,
    seeing: Boolean,
    showTools: Boolean,
    onBack: () -> Unit,
    onToggleSees: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), tint = HeatColors.TextPrimary)
        }
        Avatar(size = 34)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                stringResource(R.string.ai_title),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.ai_subtitle), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
        }
        if (showTools) {
            IconButton(onClick = onToggleSees) {
                Icon(
                    painterResource(R.drawable.ic_eye),
                    stringResource(R.string.ai_what_it_sees),
                    tint = if (seeing) HeatColors.Accent else HeatColors.TextSecondary,
                )
            }
            IconButton(onClick = onClear, enabled = canClear) {
                Icon(
                    painterResource(R.drawable.ic_refresh),
                    stringResource(R.string.ai_new_chat),
                    tint = if (canClear) HeatColors.TextSecondary else HeatColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun Avatar(size: Int) {
    Box(
        Modifier.size(size.dp).background(HeatColors.Accent.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = HeatColors.Accent, modifier = Modifier.size((size * 0.55f).dp))
    }
}

// ------------------------------------------------------------------ setup / consent

@Composable
private fun SetupCard(state: AssistantUiState, onSetup: (String, Boolean) -> Boolean) {
    var key by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var agreed by rememberSaveable { mutableStateOf(state.consent) }
    var includeData by rememberSaveable { mutableStateOf(state.includePhoneData) }
    var keyError by rememberSaveable { mutableStateOf(false) }
    val canSave = agreed && (key.isNotBlank() || state.hasKey)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
            Text(stringResource(R.string.ai_setup_title), style = MaterialTheme.typography.titleLarge, color = HeatColors.TextPrimary)
            Text(stringResource(R.string.ai_setup_body), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
            Text(stringResource(R.string.ai_setup_sent_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = HeatColors.TextTertiary)
            listOf(R.string.ai_setup_sent_1, R.string.ai_setup_sent_2, R.string.ai_setup_sent_3).forEach {
                Text("•  " + stringResource(it), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
            }
            Text(stringResource(R.string.ai_setup_never), style = MaterialTheme.typography.bodySmall, color = HeatColors.Normal)
        }

        HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
            if (state.hasKey) {
                Text(stringResource(R.string.ai_setup_have_key), style = MaterialTheme.typography.bodyMedium, color = HeatColors.Normal)
            } else {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.filterNot(Char::isWhitespace); keyError = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_key_label)) },
                    singleLine = true,
                    isError = keyError,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        TextButton(onClick = { show = !show }) {
                            Text(stringResource(if (show) R.string.ai_key_hide else R.string.ai_key_show))
                        }
                    },
                    supportingText = {
                        Text(stringResource(if (keyError) R.string.ai_key_invalid else R.string.ai_key_help))
                    },
                    colors = fieldColors(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_include_data), style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary)
                    Text(stringResource(R.string.ai_include_data_body), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
                }
                Switch(
                    checked = includeData,
                    onCheckedChange = { includeData = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HeatColors.OnAccent,
                        checkedTrackColor = HeatColors.Accent,
                        uncheckedThumbColor = HeatColors.TextSecondary,
                        uncheckedTrackColor = HeatColors.SurfaceHighest,
                        uncheckedBorderColor = HeatColors.Outline,
                    ),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                    colors = CheckboxDefaults.colors(checkedColor = HeatColors.Accent, checkmarkColor = HeatColors.OnAccent, uncheckedColor = HeatColors.TextSecondary),
                )
                Text(stringResource(R.string.ai_consent), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextPrimary)
            }

            Button(
                onClick = { keyError = !onSetup(key, includeData) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeatColors.Accent,
                    contentColor = HeatColors.OnAccent,
                    disabledContainerColor = HeatColors.SurfaceHighest,
                    disabledContentColor = HeatColors.TextTertiary,
                ),
            ) { Text(stringResource(R.string.ai_setup_save)) }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HeatColors.Accent,
    unfocusedBorderColor = HeatColors.Outline,
    focusedTextColor = HeatColors.TextPrimary,
    unfocusedTextColor = HeatColors.TextPrimary,
    cursorColor = HeatColors.Accent,
    focusedLabelColor = HeatColors.Accent,
    unfocusedLabelColor = HeatColors.TextSecondary,
    focusedSupportingTextColor = HeatColors.TextSecondary,
    unfocusedSupportingTextColor = HeatColors.TextSecondary,
    errorSupportingTextColor = HeatColors.Critical,
)

// ------------------------------------------------------------------ transparency panel

@Composable
private fun SeesPanel(loadPreview: suspend () -> String) {
    val text by produceState<String?>(initialValue = null) { value = loadPreview() }
    HeatCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        spacing = 6.dp,
        contentPadding = 14.dp,
    ) {
        Text(stringResource(R.string.ai_sees_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = HeatColors.TextSecondary)
        Text(stringResource(R.string.ai_sees_note), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
        Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            SelectionContainer {
                Text(
                    text ?: stringResource(R.string.ai_sees_loading),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                    color = HeatColors.TextSecondary,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ empty state and suggestions

@Composable
private fun EmptyState(screenLabel: String?, suggestions: List<ScreenContext.Suggestion>, onSend: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Avatar(size = 56)
        Text(stringResource(R.string.ai_empty_title), style = MaterialTheme.typography.titleLarge, color = HeatColors.TextPrimary)
        Text(
            stringResource(R.string.ai_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (screenLabel != null) {
            Text(
                stringResource(R.string.ai_from_screen, screenLabel).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HeatColors.TextTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        suggestions.forEach { s ->
            val prompt = stringResource(s.prompt)
            Surface(
                onClick = { onSend(prompt) },
                shape = RoundedCornerShape(16.dp),
                color = HeatColors.Surface,
                border = BorderStroke(1.dp, HeatColors.Outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(s.label), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_send), contentDescription = null, tint = HeatColors.Accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            stringResource(R.string.ai_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SuggestionRow(suggestions: List<ScreenContext.Suggestion>, onSend: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { s ->
            val prompt = stringResource(s.prompt)
            AssistChip(
                onClick = { onSend(prompt) },
                label = { Text(stringResource(s.label)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = HeatColors.SurfaceRaised, labelColor = HeatColors.TextSecondary),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = HeatColors.Outline),
            )
        }
    }
}

// ------------------------------------------------------------------ messages

@Composable
private fun MessageList(state: AssistantUiState, onRetry: () -> Unit) {
    val listState = rememberLazyListState()
    val messages = state.messages
    val thinking = state.phase == AssistantPhase.THINKING
    val itemCount = messages.size + if (thinking) 1 else 0
    val lastLength = messages.lastOrNull()?.text?.length ?: 0

    // Follow new messages, and follow a streaming answer only while the reader is already at the bottom.
    LaunchedEffect(itemCount) { if (itemCount > 0) listState.animateScrollToItem(itemCount - 1) }
    LaunchedEffect(lastLength) {
        val info = listState.layoutInfo
        val nearBottom = (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 2
        if (state.phase == AssistantPhase.STREAMING && nearBottom && itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            val time = timeFormat.format(Date(message.timestampMillis))
            if (message.role == ChatRole.USER) {
                UserBubble(message, time)
            } else {
                AssistantBubble(message, time, canRetry = message.id == messages.last().id && !state.isGenerating, onRetry = onRetry)
            }
        }
        if (thinking) item(key = "typing") { TypingIndicator() }
    }
}

@Composable
private fun UserBubble(message: ChatMessage, time: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 320.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = HeatColors.Accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, HeatColors.Accent.copy(alpha = 0.4f)),
            ) {
                SelectionContainer {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            Text(time, style = MaterialTheme.typography.labelSmall, color = HeatColors.TextTertiary, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
        }
    }
}

@Composable
private fun AssistantBubble(message: ChatMessage, time: String, canRetry: Boolean, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Avatar(size = 28)
        Column(Modifier.widthIn(max = 340.dp)) {
            Surface(
                shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                color = HeatColors.SurfaceRaised,
                border = BorderStroke(1.dp, HeatColors.OutlineSubtle),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.text.isNotEmpty() || message.streaming) {
                        SelectionContainer { MarkdownText(message.text, cursor = message.streaming) }
                    }
                    message.failure?.let { failure ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(failureText(failure), style = MaterialTheme.typography.bodyMedium, color = HeatColors.Critical)
                            if (canRetry && failure != ChatFailure.NotConfigured && failure != ChatFailure.InvalidKey) {
                                TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                    Text(stringResource(R.string.ai_retry), color = HeatColors.Accent)
                                }
                            }
                        }
                    }
                }
            }
            if (!message.streaming) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = HeatColors.TextTertiary, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
            }
        }
    }
}

@Composable
private fun failureText(failure: ChatFailure): String = when (failure) {
    ChatFailure.NotConfigured -> stringResource(R.string.ai_err_not_configured)
    ChatFailure.InvalidKey -> stringResource(R.string.ai_err_invalid_key)
    is ChatFailure.RateLimited ->
        failure.retryAfterSeconds?.let { stringResource(R.string.ai_err_rate_limited_secs, it) } ?: stringResource(R.string.ai_err_rate_limited)
    ChatFailure.Offline -> stringResource(R.string.ai_err_offline)
    ChatFailure.Timeout -> stringResource(R.string.ai_err_timeout)
    is ChatFailure.Server -> stringResource(R.string.ai_err_server, failure.code)
    is ChatFailure.Rejected -> stringResource(R.string.ai_err_rejected, failure.message)
    ChatFailure.EmptyReply -> stringResource(R.string.ai_err_empty)
    ChatFailure.Unknown -> stringResource(R.string.ai_err_unknown)
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val thinking = stringResource(R.string.ai_thinking)
    Row(
        Modifier.semantics { contentDescription = thinking },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(size = 28)
        Surface(shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp), color = HeatColors.SurfaceRaised, border = BorderStroke(1.dp, HeatColors.OutlineSubtle)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600, delayMillis = index * 160), RepeatMode.Reverse),
                        label = "dot$index",
                    )
                    Box(Modifier.size(8.dp).alpha(alpha).background(HeatColors.Accent, CircleShape))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ markdown rendering

@Composable
private fun MarkdownText(text: String, cursor: Boolean) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            val last = index == blocks.lastIndex
            val annotated = annotate(block.spans, cursor && last)
            when (block.kind) {
                BlockKind.BULLET -> Row {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = HeatColors.Accent, modifier = Modifier.padding(end = 8.dp))
                    Text(annotated, style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary, modifier = Modifier.weight(1f))
                }
                BlockKind.NUMBERED -> Row {
                    Text("${block.number}.", style = MaterialTheme.typography.bodyLarge, color = HeatColors.Accent, modifier = Modifier.widthIn(min = 22.dp).padding(end = 6.dp))
                    Text(annotated, style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary, modifier = Modifier.weight(1f))
                }
                BlockKind.HEADING, BlockKind.PARAGRAPH ->
                    Text(annotated, style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary)
            }
        }
        if (blocks.isEmpty() && cursor) Text("▍", style = MaterialTheme.typography.bodyLarge, color = HeatColors.Accent)
    }
}

private fun annotate(spans: List<Span>, cursor: Boolean): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val style = when {
            span.code -> SpanStyle(fontFamily = FontFamily.Monospace, background = HeatColors.SurfaceHighest, color = HeatColors.Accent)
            span.bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
            else -> SpanStyle()
        }
        withStyle(style) { append(span.text) }
    }
    if (cursor) withStyle(SpanStyle(color = HeatColors.Accent)) { append("▍") }
}

// ------------------------------------------------------------------ input

@Composable
private fun InputBar(generating: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank() && !generating) {
            onSend(text)
            text = ""
        }
    }
    Surface(color = HeatColors.Background, border = BorderStroke(1.dp, HeatColors.OutlineSubtle)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.ai_input_hint), color = HeatColors.TextTertiary) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                colors = fieldColors(),
            )
            if (generating) {
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = HeatColors.SurfaceHighest, contentColor = HeatColors.TextPrimary),
                ) { Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.ai_stop)) }
            } else {
                FilledIconButton(
                    onClick = { submit() },
                    enabled = text.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = HeatColors.Accent,
                        contentColor = HeatColors.OnAccent,
                        disabledContainerColor = HeatColors.SurfaceHighest,
                        disabledContentColor = HeatColors.TextTertiary,
                    ),
                ) { Icon(painterResource(R.drawable.ic_send), stringResource(R.string.ai_send)) }
            }
        }
    }
}
