@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.gerko.fourenote.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

internal inline fun <R> ByteArray.withPointer(block: (CPointer<ByteVar>?) -> R): R =
    if (isEmpty()) block(null) else usePinned { block(it.addressOf(0)) }

internal fun ByteArray.toNSData(): NSData = withPointer { NSData.create(bytes = it, length = size.convert()) }

internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
