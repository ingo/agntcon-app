@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("DEPRECATION")

package org.jetbrains.kotlinconf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberShareHandler(): ShareHandler = remember { IosShareHandler }

private object IosShareHandler : ShareHandler {
    override fun shareText(subject: String, text: String) = present(listOf(text))

    override fun shareFile(fileName: String, mimeType: String, content: String) {
        val path = NSTemporaryDirectory().trimEnd('/') + "/" + fileName.replace('/', '-')
        val written = NSString.create(string = content)
            .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (written) present(listOf(NSURL.fileURLWithPath(path)))
    }

    private fun present(items: List<Any>) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        var top: UIViewController = root
        while (true) top = top.presentedViewController ?: break

        val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
        // On iPad the sheet is a popover and needs somewhere to point at
        sheet.popoverPresentationController?.let { popover ->
            popover.sourceView = top.view
            top.view.bounds.useContents {
                popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
            }
        }
        top.presentViewController(sheet, animated = true, completion = null)
    }
}
