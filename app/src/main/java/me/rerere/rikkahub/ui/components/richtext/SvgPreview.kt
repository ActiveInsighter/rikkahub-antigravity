package me.rerere.rikkahub.ui.components.richtext

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import coil3.svg.SvgDecoder
import com.jvziyaoyao.scale.image.pager.ImagePager
import com.jvziyaoyao.scale.zoomable.pager.rememberZoomablePagerState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R

@Composable
internal fun SvgArtwork(model: Any, description: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = rememberSvgRequest(model),
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
internal fun SvgPreview(
    model: Any,
    description: String?,
    modifier: Modifier = Modifier,
) {
    var failed by remember(model) { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.White,
        contentColor = Color(0xFF303134),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = rememberSvgRequest(model),
                contentDescription = description,
                modifier = Modifier.fillMaxWidth(),
                onSuccess = { failed = false },
                onError = { failed = true },
            )
            if (failed) {
                Text(
                    text = stringResource(R.string.svg_preview_error),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun SvgPreviewDialog(
    model: Any,
    onDismiss: () -> Unit,
    description: String? = null,
    framed: Boolean = true,
) {
    val pagerState = rememberZoomablePagerState { 1 }
    val painter = rememberAsyncImagePainter(rememberSvgRequest(model, maxSizePx = 2400))
    val state by painter.state.collectAsState()
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = if (framed) Modifier.fillMaxSize().padding(16.dp) else Modifier.fillMaxSize(),
            shape = if (framed) MaterialTheme.shapes.extraLarge else RectangleShape,
            color = if (framed) Color.White else Color.Transparent,
            contentColor = if (framed) Color(0xFF303134) else Color.White,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ImagePager(
                    modifier = Modifier.fillMaxSize().semantics {
                        if (!description.isNullOrBlank()) contentDescription = description
                    },
                    pagerState = pagerState,
                    imageLoader = { Pair(painter, painter.intrinsicSize) },
                )
                if (state is AsyncImagePainter.State.Loading) {
                    LoadingIndicator()
                }
                if (state is AsyncImagePainter.State.Error) {
                    Text(stringResource(R.string.svg_preview_error))
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .zIndex(1f),
                    shape = CircleShape,
                    color = if (framed) {
                        Color(0xFF303134).copy(alpha = 0.08f)
                    } else {
                        Color.Black.copy(alpha = 0.45f)
                    },
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            HugeIcons.Cancel01,
                            contentDescription = stringResource(R.string.update_card_close),
                            tint = if (framed) Color(0xFF303134) else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSvgRequest(model: Any, maxSizePx: Int? = null): ImageRequest {
    val context = LocalContext.current
    return remember(context, model, maxSizePx) {
        buildSvgImageRequest(context, model, maxSizePx)
    }
}

@OptIn(ExperimentalCoilApi::class)
internal fun buildSvgImageRequest(
    context: Context,
    model: Any,
    maxSizePx: Int? = null,
): ImageRequest = ImageRequest.Builder(context)
    .data(model)
    .diskCachePolicy(CachePolicy.ENABLED)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .apply {
        if (maxSizePx != null) {
            size(Size(maxSizePx, maxSizePx))
            precision(Precision.INEXACT)
        }
    }
    .decoderFactory { result, options, _ ->
        SvgDecoder(result.source, options, parser = compatibleSvgParser, renderToBitmap = true)
    }
    .build()
