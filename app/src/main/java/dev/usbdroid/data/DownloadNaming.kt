package dev.usbdroid.data

import android.net.Uri
import java.io.File

fun downloadFilename(release: Release): String = Uri.parse(release.url).lastPathSegment?.takeIf { File(it).extension.lowercase() in listOf("iso", "img") } ?: "${release.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}-${release.version}.iso"
