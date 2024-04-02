package net.pipe01.pinepartner.utils

class PineError(
    message: String,
    cause: Throwable,
    val onTryAgain: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
) : Exception(message, cause)
