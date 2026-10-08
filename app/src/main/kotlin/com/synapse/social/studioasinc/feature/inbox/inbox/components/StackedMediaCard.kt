package com.synapse.social.studioasinc.feature.inbox.inbox.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synapse.social.studioasinc.R
import com.synapse.social.studioasinc.feature.shared.theme.Sizes
import com.synapse.social.studioasinc.feature.shared.theme.Spacing
import com.synapse.social.studioasinc.shared.domain.model.chat.AttachmentType
import com.synapse.social.studioasinc.shared.domain.model.chat.MessageAttachment

@Composable
fun StackedMediaCard(
    attachments: List<MessageAttachment>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    val totalCount = attachments.size
    val frontItem = attachments.first()
    val isVideo = frontItem.type == AttachmentType.VIDEO
    val stackDescription = stringResource(R.string.chat_cd_stacked_media, totalCount)
    val context = LocalContext.current

    // Pronounced Instagram-style card stack/fan dimensions
    val fanOffsetX2 = (-28).dp
    val fanOffsetY2 = (-16).dp
    val fanRotation2 = -12f

    val fanOffsetX1 = 26.dp
    val fanOffsetY1 = (-10).dp
    val fanRotation1 = 10f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.HeightMediaSingle)
            .semantics { contentDescription = stackDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Background card 2 (if 3+ items)
        if (totalCount >= 3) {
            val bg2Item = attachments.getOrNull(2)
            val progress2 = bg2Item?.uploadProgress ?: 1.0f
            val targetOpacity2 = (0.35f + (progress2 * 0.35f)).coerceIn(0.35f, 0.7f)
            val animatedOpacity2 by animateFloatAsState(
                targetValue = targetOpacity2,
                animationSpec = tween(durationMillis = 300),
                label = "card_opacity_2"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize(0.88f)
                    .offset(x = fanOffsetX2, y = fanOffsetY2)
                    .rotate(fanRotation2)
                    .clip(RoundedCornerShape(Sizes.CornerMedium))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = Sizes.BorderThin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(Sizes.CornerMedium)
                    )
            ) {
                if (bg2Item != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(bg2Item.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(animatedOpacity2)
                    )
                }
            }
        }

        // Background card 1 (if 2+ items)
        if (totalCount >= 2) {
            val bg1Item = attachments.getOrNull(1)
            val progress1 = bg1Item?.uploadProgress ?: 1.0f
            val targetOpacity1 = (0.4f + (progress1 * 0.45f)).coerceIn(0.4f, 0.85f)
            val animatedOpacity1 by animateFloatAsState(
                targetValue = targetOpacity1,
                animationSpec = tween(durationMillis = 300),
                label = "card_opacity_1"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .offset(x = fanOffsetX1, y = fanOffsetY1)
                    .rotate(fanRotation1)
                    .clip(RoundedCornerShape(Sizes.CornerMedium))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = Sizes.BorderThin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(Sizes.CornerMedium)
                    )
            ) {
                if (bg1Item != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(bg1Item.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(animatedOpacity1)
                    )
                }
            }
        }

        // Front Card
        val frontProgress = frontItem.uploadProgress
        val frontTargetOpacity = (0.4f + (frontProgress * 0.6f)).coerceIn(0.4f, 1.0f)
        val animatedFrontOpacity by animateFloatAsState(
            targetValue = frontTargetOpacity,
            animationSpec = tween(durationMillis = 300),
            label = "front_card_opacity"
        )

        Box(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .clip(RoundedCornerShape(Sizes.CornerMedium))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = Sizes.BorderDefault,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(Sizes.CornerMedium)
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(frontItem.url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(animatedFrontOpacity)
            )

            // Visual failure state overlay
            if (frontItem.isUploadFailed || attachments.any { it.isUploadFailed }) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(Sizes.IconExtraLarge)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.upload_failed),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(Sizes.IconLarge)
                    )
                }
            } else if (isVideo) {
                // Video play affordance
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(Sizes.IconExtraLarge)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.chat_action_play_video),
                        tint = Color.White,
                        modifier = Modifier.size(Sizes.IconLarge)
                    )
                }

                // Video duration badge
                val duration = frontItem.duration
                if (duration != null && duration > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(Sizes.CornerSmall),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spacing.Small)
                    ) {
                        val minutes = duration / 60
                        val seconds = duration % 60
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Tiny)
                        )
                    }
                }
            }

            // Stack total count badge overlay
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                shape = RoundedCornerShape(Sizes.CornerMedium),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.Small)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(Sizes.IconSmall)
                    )
                    Spacer(modifier = Modifier.width(Spacing.ExtraSmall))
                    Text(
                        text = totalCount.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
