package org.jetbrains.kotlinconf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.js.js

@Composable
actual fun rememberShareHandler(): ShareHandler = remember { WebShareHandler }

/** The browser's share sheet where there is one, else the clipboard (text) or a download (files). */
private object WebShareHandler : ShareHandler {
    override fun shareText(subject: String, text: String) = shareOrCopy(subject, text)

    override fun shareFile(fileName: String, mimeType: String, content: String) = download(fileName, mimeType, content)
}

private fun shareOrCopy(subject: String, text: String): Unit = js(
    """{
        if (navigator.share) { navigator.share({ title: subject, text: text }).catch(function () {}); }
        else if (navigator.clipboard) { navigator.clipboard.writeText(text); }
    }"""
)

private fun download(fileName: String, mimeType: String, content: String): Unit = js(
    """{
        var blob = new Blob([content], { type: mimeType + ';charset=utf-8' });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    }"""
)
