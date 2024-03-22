package net.pipe01.pinepartner.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import net.pipe01.pinepartner.service.Action
import net.pipe01.pinepartner.service.CODE_ERROR
import net.pipe01.pinepartner.service.CODE_EXCEPTION
import net.pipe01.pinepartner.service.CODE_OK
import net.pipe01.pinepartner.service.ServiceException

suspend fun callIntent(context: Context, action: Action, request: (Bundle.() -> Unit)? = null): Bundle? {
    Log.d("callIntent", "Sending intent: $action, has request data: ${request != null}")

    val completable = CompletableDeferred<Pair<Int, Bundle?>>()

    val intent = Intent(action.fullName)
    val extras = Bundle()

    if (request != null) {
        request(extras)
        intent.putExtras(extras)
    }

    context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            completable.complete(Pair(resultCode, getResultExtras(true)))

            Log.d("callIntent", "Got response code $resultCode for intent $action")
        }
    }, null, android.app.Activity.RESULT_OK, null, null)

    val result = completable.await()

    when (result.first) {
        CODE_OK -> return result.second
        CODE_ERROR -> throw ServiceException(result.second?.getString("serviceError") ?: "Unknown error")
        CODE_EXCEPTION -> {
            val exception = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.second?.getSerializable("exception", Exception::class.java)
            } else {
                result.second?.getSerializable("exception") as? Exception
            }

            throw if (exception != null)
                ServiceException("Exception occurred in service handler", exception)
            else
                ServiceException("Unknown exception occurred in service handler")
        }
        else -> throw Exception("Unknown error occurred")
    }
}

suspend fun <T : Parcelable> callIntent(context: Context, action: Action, responseClass: Class<T>, request: (Bundle.() -> Unit)? = null): T {
    val result = callIntent(context, action, request)
    if (result != null) {
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.getParcelable("data", responseClass)
        } else {
            result.getParcelable("data")
        }

        if (data != null) {
            return data
        }
    }

    throw Exception("No data in response")
}

suspend fun <T : Parcelable> callIntentArray(
    context: Context,
    action: Action,
    responseClass: Class<T>,
    request: (Bundle.() -> Unit)? = null
): List<T> {
    val result = callIntent(context, action, request)
    if (result != null) {
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.getParcelableArray("data", responseClass)
        } else {
            result.getParcelableArray("data")
        }

        if (data != null) {
            return data.map { it as T }
        }
    }

    throw Exception("No data in response")
}
