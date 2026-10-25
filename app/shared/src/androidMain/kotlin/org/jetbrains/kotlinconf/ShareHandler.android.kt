package org.jetbrains.kotlinconf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberShareHandler(): ShareHandler {
    val context = LocalContext.current
    return remember(context) { AndroidShareHandler(context) }
}

private class AndroidShareHandler(private val context: Context) : ShareHandler {
    override fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(send)
    }

    override fun shareFile(fileName: String, mimeType: String, content: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName.replace('/', '-')).apply { writeText(content) }
        // The provider is declared in the app's manifest; its authority is "<applicationId>.shareprovider"
        val uri = FileProvider.getUriForFile(context, context.packageName + ".shareprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // The chooser and the app picked from it read the file through the grant, which Android takes from
            // the ClipData; without it the system chooser logs "Permission Denial" while previewing the file
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(send)
    }

    // The report bundle is written to <cache>/report/, which the provider's paths include
    override fun shareFileAt(path: String, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".shareprovider", File(path))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(send)
    }

    private fun start(send: Intent) {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
