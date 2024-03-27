package net.pipe01.pinepartner.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

suspend fun runJobThrowing(coroutineScope: CoroutineScope, onStart: (Job) -> Unit, block: suspend CoroutineScope.() -> Unit) {
    var result = Result.success(Unit)

    val job = coroutineScope.launch {
        result = Result.runCatching {
            block()
        }
    }

    onStart(job)
    job.join()

    result.getOrThrow()
}