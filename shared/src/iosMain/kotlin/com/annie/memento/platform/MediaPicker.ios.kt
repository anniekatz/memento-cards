package com.annie.memento.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.darwin.NSObject

@Composable
actual fun rememberImagePicker(onResult: (List<PickedMedia>) -> Unit): () -> Unit {
    val holder = remember { DelegateHolder() }
    return {
        val picker = UIImagePickerController()
        val delegate = ImagePickerDelegate { media ->
            holder.delegate = null
            onResult(media)
        }
        holder.delegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberSingleImagePicker(onResult: (PickedMedia?) -> Unit): () -> Unit {
    val holder = remember { DelegateHolder() }
    return {
        val picker = UIImagePickerController()
        val delegate = ImagePickerDelegate { media ->
            holder.delegate = null
            onResult(media.firstOrNull())
        }
        holder.delegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberAudioPicker(onResult: (List<PickedMedia>) -> Unit): () -> Unit {
    val holder = remember { DelegateHolder() }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio))
        picker.allowsMultipleSelection = true
        val delegate = DocumentPickerDelegate { media ->
            holder.delegate = null
            onResult(media)
        }
        holder.delegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class DelegateHolder {
    var delegate: NSObject? = null
}

private class ImagePickerDelegate(
    private val onResult: (List<PickedMedia>) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        val media = image?.let { UIImageJPEGRepresentation(it, 0.9) }?.toByteArray()?.let { PickedMedia(it, "jpg") }
        onResult(listOfNotNull(media))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(emptyList())
    }
}

private class DocumentPickerDelegate(
    private val onResult: (List<PickedMedia>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val media = didPickDocumentsAtURLs.mapNotNull { it as? NSURL }.mapNotNull { url ->
            val accessed = url.startAccessingSecurityScopedResource()
            val data = NSData.dataWithContentsOfURL(url)
            if (accessed) url.stopAccessingSecurityScopedResource()
            val extension = url.pathExtension?.takeIf { it.isNotBlank() } ?: "m4a"
            data?.toByteArray()?.let { PickedMedia(it, extension) }
        }
        onResult(media)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(emptyList())
    }
}

private fun topViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.keyWindow ?: application.windows.firstOrNull() as? UIWindow
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
