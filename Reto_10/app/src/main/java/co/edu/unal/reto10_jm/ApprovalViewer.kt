package co.edu.unal.reto10_jm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

@Composable
internal fun ApprovalViewer(state: DocumentState, retry: () -> Unit, close: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var browserError by rememberSaveable(state.url.toString()) { mutableStateOf(false) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = close) { Text("Volver") }
            Text("Documento de aprobación", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(
            onClick = {
                browserError = try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.url.toASCIIString())))
                    false
                } catch (_: ActivityNotFoundException) {
                    true
                }
            }
        ) { Text("Abrir en el navegador") }
        if (browserError) {
            Text("No hay un navegador disponible.", Modifier.padding(horizontal = 16.dp))
        }
        when (state) {
            is DocumentState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DocumentState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message)
                    TextButton(onClick = retry) { Text("Reintentar") }
                }
            }
            is DocumentState.Ready -> DocumentPages(state)
        }
    }
}

@Composable
private fun DocumentPages(document: DocumentState.Ready) {
    var zoom by rememberSaveable(document.file.name) { mutableIntStateOf(100) }
    Column {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { zoom -= 25 }, enabled = zoom > 100) { Text("Reducir") }
            Text("$zoom %")
            TextButton(onClick = { zoom += 25 }, enabled = zoom < 250) { Text("Ampliar") }
            Text("${document.pageCount} pág.", Modifier.padding(start = 8.dp))
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            val pageWidth = maxWidth * (zoom / 100f)
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(
                    Modifier.width(pageWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(document.pageCount) { index ->
                        DocumentPage(document.file, index)
                    }
                }
            }
        }
    }
}

private sealed interface PdfPage {
    data object Loading : PdfPage
    data class Ready(val image: ImageBitmap) : PdfPage
    data object Error : PdfPage
}

@Composable
private fun DocumentPage(file: File, index: Int) {
    val page by produceState<PdfPage>(PdfPage.Loading, file, index) {
        value = try {
            withContext(Dispatchers.IO) { PdfPage.Ready(renderPage(file, index).asImageBitmap()) }
        } catch (_: IOException) {
            PdfPage.Error
        } catch (_: SecurityException) {
            PdfPage.Error
        }
    }
    when (val state = page) {
        PdfPage.Loading -> Box(
            Modifier.fillMaxWidth().height(320.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        PdfPage.Error -> Text("No se pudo mostrar la página ${index + 1}.", Modifier.padding(16.dp))
        is PdfPage.Ready -> Image(
            bitmap = state.image,
            contentDescription = "Página ${index + 1}",
            modifier = Modifier.fillMaxWidth()
                .aspectRatio(state.image.width.toFloat() / state.image.height)
                .background(Color.White)
        )
    }
}

private fun renderPage(file: File, index: Int): Bitmap =
    openPdf(file) { renderer ->
        renderer.openPage(index).use { page ->
            val scale = 1800f / maxOf(page.width, page.height)
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.WHITE)
                page.render(this, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
