package com.devbehindyou.atomicfilemanager.ui.components

import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult

/** Native animated drawable in the same gesture surface as static images. */
@Composable
internal fun AnimatedGifImage(
    node: FileNode,
    helper: ImagePreviewHelper,
    isActive: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val decoded by produceState<FileResult<Drawable>?>(null, isActive) {
        if (isActive && value == null) value = helper.decodeGif(node.id)
    }
    val drawable =
        when (val result = decoded) {
            null -> {
                ViewerState(loading = true, error = null)
                return
            }
            is FileResult.Failure -> {
                ImageLoadError(result.error, onRetry)
                return
            }
            is FileResult.Success -> result.value
        }
    val animation = drawable as? AnimatedImageDrawable
    val owner = LocalLifecycleOwner.current
    var requested by remember(drawable) { mutableStateOf(true) }
    var replay by remember(drawable) { mutableIntStateOf(0) }
    DisposableEffect(drawable, owner, isActive, requested, replay) {
        if (animation == null) {
            onDispose { }
        } else {
            fun synchronize() {
                if (isActive && requested && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    animation.start()
                } else {
                    animation.stop()
                }
            }
            val callback =
                object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable) {
                        if (isActive && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            requested = false
                        }
                    }
                }
            val observer = LifecycleEventObserver { _, _ -> synchronize() }
            animation.registerAnimationCallback(callback)
            owner.lifecycle.addObserver(observer)
            synchronize()
            onDispose {
                animation.unregisterAnimationCallback(callback)
                owner.lifecycle.removeObserver(observer)
                animation.stop()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            ZoomableImageSurface(
                node,
                Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat()),
                onZoomChange,
            ) { imageModifier ->
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            tag = "atomic_gif_drawable"
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            setImageDrawable(drawable)
                        }
                    },
                    modifier = imageModifier.semantics { contentDescription = node.name },
                    onReset = null,
                    onRelease = { view ->
                        (view.drawable as? AnimatedImageDrawable)?.stop()
                        view.setImageDrawable(null)
                    },
                )
            }
        }
        if (animation != null) {
            Row(
                Modifier.padding(AtomicSpacing.s8),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicIconButton(
                    AtomicIcons.Stop,
                    stringResource(R.string.gif_stop),
                    onClick = { requested = false },
                    enabled = requested,
                )
                AtomicIconButton(
                    AtomicIcons.Replay,
                    stringResource(R.string.gif_replay),
                    onClick = {
                        requested = true
                        replay++
                    },
                )
            }
        }
    }
}
