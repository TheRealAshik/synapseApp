package com.synapse.social.studioasinc.feature.inbox.inbox.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.synapse.social.studioasinc.R
import com.synapse.social.studioasinc.feature.inbox.inbox.voice.VoiceMessagePlayerViewModel
import com.synapse.social.studioasinc.feature.shared.reels.VideoPlayerViewModel
import com.synapse.social.studioasinc.feature.shared.theme.Sizes
import com.synapse.social.studioasinc.feature.shared.theme.Spacing

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun formatSpeed(speed: Float): String {
    return when {
        speed == 1.5f -> "1.5x"
        speed >= 2.0f -> "2x"
        else -> "1x"
    }
}

@Composable
fun VoiceMessagePlayer(
    mediaUrl: String,
    tintColor: Color,
    isFromMe: Boolean,
    modifier: Modifier = Modifier,
    viewModel: VoiceMessagePlayerViewModel = hiltViewModel(key = mediaUrl)
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(mediaUrl) {
        viewModel.initialize(mediaUrl)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isPlaying = uiState.isPlaying
    val isDownloading = uiState.isDownloading
    val isBuffering = uiState.isBuffering
    val hasError = uiState.downloadError != null
    val currentPositionMs = uiState.currentPositionMs
    val durationMs = uiState.durationMs
    val speed = uiState.playbackSpeed
    val amplitudes = uiState.waveformAmplitudes

    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val buttonContainerColor = if (isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val buttonContentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
    val activeWaveformColor = if (isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val inactiveWaveformColor = activeWaveformColor.copy(alpha = 0.38f)
    val textColor = if (isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .widthIn(min = Sizes.ChatMaxWidth / 2, max = Sizes.ChatMaxWidth)
            .padding(vertical = Spacing.ExtraSmall)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall)
        ) {
            // Material 3 FilledTonalIconButton
            Box(
                modifier = Modifier
                    .size(Sizes.AvatarLarge),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isDownloading || isBuffering -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Sizes.IconLarge),
                            color = activeWaveformColor,
                            strokeWidth = Sizes.BorderDefault
                        )
                    }
                    hasError -> {
                        IconButton(
                            onClick = { viewModel.onRetryClicked() },
                            modifier = Modifier.size(Sizes.AvatarLarge)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.retry),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(Sizes.IconLarge)
                            )
                        }
                    }
                    else -> {
                        FilledTonalIconButton(
                            onClick = { viewModel.onPlayPauseClicked() },
                            modifier = Modifier.size(Sizes.AvatarLarge),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = buttonContainerColor,
                                contentColor = buttonContentColor
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.chat_action_pause_audio else R.string.chat_action_play_audio
                                ),
                                modifier = Modifier.size(Sizes.IconLarge)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            // Waveform and Controls Column
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Waveform Canvas (Seekable by tap or drag)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.IconLarge)
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                if (durationMs > 0 && size.width > 0) {
                                    val fraction = offset.x / size.width.toFloat()
                                    viewModel.onSeek(fraction)
                                }
                            }
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                if (durationMs > 0 && size.width > 0) {
                                    val fraction = change.position.x / size.width.toFloat()
                                    viewModel.onSeek(fraction)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val bars = if (amplitudes.isNotEmpty()) amplitudes else List(36) { 0.3f }
                        val barCount = bars.size
                        val spacingPx = Spacing.ExtraSmall.toPx()
                        val totalSpacing = spacingPx * (barCount - 1)
                        val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceAtLeast(Sizes.BorderDefault.toPx())

                        bars.forEachIndexed { index, amp ->
                            val x = index * (barWidth + spacingPx) + (barWidth / 2f)
                            val barHeight = (canvasHeight * amp.coerceIn(0.15f, 1.0f)).coerceAtLeast(Sizes.BorderThin.toPx() * 4)
                            val startY = (canvasHeight - barHeight) / 2f
                            val endY = startY + barHeight

                            val barFraction = index.toFloat() / barCount.toFloat()
                            val color = if (barFraction <= progress) {
                                activeWaveformColor
                            } else {
                                inactiveWaveformColor
                            }

                            drawLine(
                                color = color,
                                start = Offset(x, startY),
                                end = Offset(x, endY),
                                strokeWidth = barWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                // Time Display and Speed Chip Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time position / duration string
                    val formattedPosition = formatTime(currentPositionMs)
                    val formattedDuration = formatTime(durationMs)
                    val timeText = if (durationMs > 0) "$formattedPosition / $formattedDuration" else formattedPosition

                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.Medium
                    )

                    // Playback Speed Toggle Chip (1x -> 1.5x -> 2x -> 1x)
                    Surface(
                        shape = RoundedCornerShape(Sizes.CornerMedium),
                        color = buttonContainerColor,
                        contentColor = buttonContentColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Sizes.CornerMedium))
                            .clickable { viewModel.onSpeedToggle() }
                    ) {
                        Text(
                            text = formatSpeed(speed),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.Tiny)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerBox(
    mediaUrl: String,
    viewModel: VideoPlayerViewModel = hiltViewModel(key = mediaUrl)
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(mediaUrl) {
        viewModel.initializePlayer(mediaUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releasePlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = Sizes.ChatMaxWidth)
            .clip(RoundedCornerShape(Sizes.CornerMedium))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val player = viewModel.getPlayerInstance()
        if (player != null) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
