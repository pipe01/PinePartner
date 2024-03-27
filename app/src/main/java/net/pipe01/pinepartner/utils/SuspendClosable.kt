package net.pipe01.pinepartner.utils

interface SuspendClosable {
    suspend fun close()
}

suspend inline fun <T : SuspendClosable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}