package com.example.mobile_app_herp.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.mobile_app_herp.data.BillReading
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.HerpHttpException
import com.example.mobile_app_herp.data.ImagePrep
import com.example.mobile_app_herp.data.Property
import com.example.mobile_app_herp.ui.theme.HerpType
import kotlinx.coroutines.launch
import java.io.File

/**
 * Photograph a supplier bill and have it read.
 *
 * The reading is shown for a person to check, never treated as fact: the whole
 * point of the OCR is to save typing, not to decide what was delivered. Anything
 * the model flagged is called out, and the original printed text sits beside the
 * translation so a mistranslation is visible.
 */
@Composable
fun BillCaptureScreen(
    property: Property,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photo by remember { mutableStateOf<File?>(null) }
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    // The camera writes a full-resolution original — several megabytes. Once it
    // has been scaled down for upload the original is dead weight, so it is
    // deleted rather than left to accumulate one per delivery.
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var reading by remember { mutableStateOf<BillReading?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun accept(source: Uri?) {
        if (source == null) return
        working = true
        error = null
        reading = null
        scope.launch {
            val prepared = ImagePrep.prepare(context, source)
            working = false
            if (prepared == null) {
                error = "That image couldn't be opened. Take the photo again, or choose a " +
                    "different file — it needs to be a JPEG or PNG."
            } else {
                photo?.delete()
                photo = prepared
            }
            // Whether or not it worked: the full-size original has served its
            // purpose and must not sit in the cache.
            pendingCaptureFile?.delete()
            pendingCaptureFile = null
            pendingCapture = null
        }
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved) {
            accept(pendingCapture)
        } else {
            // Cancelled from the camera app — clear the empty placeholder file.
            pendingCaptureFile?.delete()
            pendingCaptureFile = null
            pendingCapture = null
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked -> accept(picked) }

    fun capture() {
        error = null
        // The camera app writes here; a file:// URI would throw
        // FileUriExposedException, and our cache is not readable by other apps.
        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val target = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        pendingCapture = uri
        pendingCaptureFile = target
        runCatching { takePhoto.launch(uri) }
            .onFailure {
                target.delete()
                pendingCaptureFile = null
                pendingCapture = null
                error = "No camera app was found. Choose a file instead."
            }
    }

    fun read() {
        val file = photo ?: return
        if (working) return
        working = true
        error = null
        scope.launch {
            runCatching { Herp.client.readBill(property.slug, file) }
                .onSuccess {
                    working = false
                    reading = it
                    if (it.isEmpty) {
                        error = "Nothing could be read off that photo. Try again with more light, " +
                            "holding the bill flat."
                    }
                }
                .onFailure {
                    working = false
                    error = if (it is HerpHttpException && it.status == 403) {
                        "You do not have permission to add goods received notes here"
                    } else {
                        it.message ?: "The bill could not be read"
                    }
                }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter),
    ) {
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onBack,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("← INVENTORY", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            eyebrow = property.name,
            title = "Upload a bill",
            subtitle = "Photograph the supplier bill and it will be read for you. " +
                "Check the result before anyone books it in.",
        )

        Spacer(Modifier.height(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = ::capture,
                enabled = !working,
                shape = CardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Text("TAKE PHOTO", style = HerpType.Action) }

            OutlinedButton(
                onClick = {
                    error = null
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !working,
                shape = CardShape,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("CHOOSE FILE", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
            }
        }

        photo?.let { file ->
            Spacer(Modifier.height(16.dp))
            SpineCard(spine = MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth()) {
                    Stamp("Bill photo", MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val preview = remember(file.path) { previewBitmap(file) }
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "The bill you photographed",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(CardShape),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        "${file.length() / 1024} KB, ready to send",
                        style = HerpType.Record,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::read,
                enabled = !working,
                shape = CardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (working) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("READING THE BILL…", style = HerpType.Action)
                } else {
                    Text("READ THIS BILL", style = HerpType.Action)
                }
            }
            if (working) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "This can take up to a minute. Keep the app open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Notice(error!!, label = "Couldn't read it")
        }

        reading?.takeIf { !it.isEmpty }?.let { bill -> BillResult(bill) }

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * The outcome, and nothing more.
 *
 * The extracted supplier, invoice number and item rows are deliberately not
 * shown. They are saved against the upload and belong on the desktop verify
 * screen, where they can be corrected and matched to inventory items before
 * anything is booked in. Printing them here would invite someone to treat a
 * machine reading as settled fact — the confidence figure is the one thing worth
 * knowing on a phone, because it tells you whether to reshoot before you walk
 * away from the delivery.
 */
@Composable
private fun BillResult(bill: BillReading) {
    val poor = bill.needsChecking
    val tint = if (poor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Spacer(Modifier.height(16.dp))

    SpineCard(spine = tint) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stamp(if (poor) "Read, but not clearly" else "Bill read", tint)

            Text(
                "${(bill.confidence * 100).toInt()}%",
                style = HerpType.Display,
                color = tint,
            )
            Text(
                if (poor) {
                    "Parts of this bill were hard to read. Take another photo in better " +
                        "light, holding the bill flat, before you leave the delivery."
                } else {
                    "The bill was read and saved."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (bill.reused) {
                Text(
                    "This photo had already been sent, so the saved reading was used again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    SpineCard(spine = MaterialTheme.colorScheme.outline) {
        Column(Modifier.fillMaxWidth()) {
            Stamp("Next", MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                "Open Inventory in the web app to create the goods received note. " +
                    "This bill is waiting there with everything that was read off it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A thumbnail of the prepared photo. Sub-sampled hard: this is a confirmation
 * that the right thing was photographed, and holding a full 2000px bitmap for it
 * would be the largest allocation on the screen for no benefit.
 */
private fun previewBitmap(file: File): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 900) sample *= 2
    BitmapFactory
        .decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}.getOrNull()
