package com.annie.memento.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject

@Composable
actual fun rememberFilePicker(onResult: (PickedFile?) -> Unit): () -> Unit {
    val holder = remember { ApkgDelegateHolder() }
    val scope = rememberCoroutineScope()
    return {
        val apkgType = UTType.typeWithFilenameExtension("apkg")
        val types = if (apkgType != null) listOf(apkgType, UTTypeZIP) else listOf(UTTypeData)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.allowsMultipleSelection = false
        val delegate = ApkgPickerDelegate(scope) { picked ->
            holder.delegate = null
            onResult(picked)
        }
        holder.delegate = delegate
        picker.delegate = delegate
        topApkgViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class ApkgDelegateHolder {
    var delegate: NSObject? = null
}

private class ApkgPickerDelegate(
    private val scope: CoroutineScope,
    private val onResult: (PickedFile?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onResult(null)
            return
        }
        scope.launch {
            val picked = withContext(ioDispatcher) {
                val accessed = url.startAccessingSecurityScopedResource()
                try {
                    copyToImportDir(url)
                } finally {
                    if (accessed) url.stopAccessingSecurityScopedResource()
                }
            }
            onResult(picked)
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}

private fun copyToImportDir(url: NSURL): PickedFile? {
    val sourcePath = url.path ?: return null
    return runCatching {
        val tmpRoot = NSTemporaryDirectory().trimEnd('/').toPath()
        // sweep
        runCatching {
            FileSystem.SYSTEM.list(tmpRoot).forEach { stale ->
                if (stale.name.startsWith("anki_import_")) {
                    runCatching { FileSystem.SYSTEM.deleteRecursively(stale) }
                }
            }
        }
        val dir = tmpRoot / ("anki_import_" + NSUUID().UUIDString)
        val target = dir / "deck.apkg"
        FileSystem.SYSTEM.createDirectories(dir)
        FileSystem.SYSTEM.source(sourcePath.toPath()).use { source ->
            FileSystem.SYSTEM.sink(target).buffer().use { it.writeAll(source) }
        }
        PickedFile(
            tempPath = target.toString(),
            displayName = url.lastPathComponent ?: "deck.apkg",
            sizeBytes = FileSystem.SYSTEM.metadataOrNull(target)?.size ?: 0L,
        )
    }.getOrNull()
}

private fun topApkgViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.keyWindow ?: application.windows.firstOrNull() as? UIWindow
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
