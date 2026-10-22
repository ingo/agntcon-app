package org.jetbrains.kotlinconf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

@Composable
actual fun rememberShareHandler(): ShareHandler = remember { JvmShareHandler }

/** The desktop has no share sheet: text goes to the clipboard and files open in the default app. */
private object JvmShareHandler : ShareHandler {
    override fun shareText(subject: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    override fun shareFile(fileName: String, mimeType: String, content: String) {
        val dir = File(System.getProperty("java.io.tmpdir"), "kotlinconf-share").apply { mkdirs() }
        val file = File(dir, fileName.replace('/', '-'))
        file.writeText(content)
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        }
    }
}
