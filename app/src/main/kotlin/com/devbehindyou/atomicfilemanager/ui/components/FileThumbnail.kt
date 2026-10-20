package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.data.preview.FolderThumbnailReader
import com.devbehindyou.atomicfilemanager.data.preview.ThumbnailSource
import com.devbehindyou.atomicfilemanager.data.preview.buildThumbnailRequest
import com.devbehindyou.atomicfilemanager.data.preview.localThumbnailPathOf
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailSourceOf
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import kotlinx.coroutines.CancellationException

/**
 * The row's type tile, with local/SAF/MediaStore media or a bounded local folder miniature.
 * The tile stays underneath, so while loading, or when a file can't be decoded, the row still
 * shows its type icon. Decoded at the tile's pixel size, never full size; Coil caches and cancels
 * requests for rows that scroll away.
 */
@Composable
fun FileThumbnail(
    node: FileNode,
    modifier: Modifier = Modifier,
    folderLoader: (suspend (FileNode) -> List<ThumbnailSource>)? = null,
    size: Dp = AtomicSize.iconTile,
) {
    val source = remember(node) { thumbnailSourceOf(node) }
    Box(modifier.size(size).testTag("file_thumbnail_${node.id.raw}")) {
        AtomicIconTile(iconFor(node), Modifier.size(size).testTag("file_type_icon_${node.id.raw}"))
        if (source != null) {
            ThumbnailImage(source, size)
        } else if (node.isDirectory && !node.isHidden && node.access.readable) {
            key(node, folderLoader) {
                val reader = remember { FolderThumbnailReader() }
                val previews by produceState(emptyList<ThumbnailSource>(), node, folderLoader) {
                    value = emptyList()
                    value =
                        try {
                            (folderLoader ?: reader::read)(node)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                }
                if (previews.isNotEmpty()) {
                    Icon(
                        iconFor(node),
                        contentDescription = null,
                        tint = Atomic.colors.content,
                        modifier = Modifier.align(Alignment.TopStart).padding(AtomicSpacing.s4).size(AtomicSpacing.s16),
                    )
                    FolderMiniature(previews, Modifier.align(Alignment.BottomEnd).padding(AtomicSpacing.s4))
                }
            }
        }
    }
}

@Composable
private fun FolderMiniature(
    previews: List<ThumbnailSource>,
    modifier: Modifier,
) {
    val cell = (AtomicSpacing.s24 - AtomicSpacing.s2) / 2
    Column(
        modifier.size(AtomicSpacing.s24).clip(AtomicShape.xs).background(Atomic.colors.background)
            .testTag("folder_miniature"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s2),
    ) {
        previews.take(FolderThumbnailReader.MAX_PREVIEWS).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s2)) {
                row.forEach { ThumbnailImage(it, cell) }
            }
        }
    }
}

@Composable
private fun ThumbnailImage(
    source: ThumbnailSource,
    size: Dp,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val request =
        remember(context, source, px) {
            buildThumbnailRequest(context, source, px)
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(AtomicShape.sm).testTag("thumbnail_image_${source.cacheKey}"),
    )
}

internal fun thumbnailPathOf(node: FileNode): String? = localThumbnailPathOf(node)
