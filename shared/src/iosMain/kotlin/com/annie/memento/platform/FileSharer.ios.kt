@file:OptIn(ExperimentalForeignApi::class)

package com.annie.memento.platform

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberFileSharer(): (String, String) -> Unit = { filePath, _ ->
    val controller = UIActivityViewController(
        activityItems = listOf(NSURL.fileURLWithPath(filePath)),
        applicationActivities = null,
    )
    val top = topShareViewController()
    // ipads use popover?
    top?.view?.let { anchor ->
        controller.popoverPresentationController?.sourceView = anchor
        controller.popoverPresentationController?.sourceRect = anchor.bounds
    }
    top?.presentViewController(controller, animated = true, completion = null)
}

private fun topShareViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.keyWindow ?: application.windows.firstOrNull() as? UIWindow
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
