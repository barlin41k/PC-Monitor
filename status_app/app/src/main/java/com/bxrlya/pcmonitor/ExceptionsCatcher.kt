package com.bxrlya.pcmonitor

import android.content.Context

suspend fun IOExceptionCatch (context: Context, isAppVisible: Boolean, serverIp: String) {
    if (isAppVisible) {
        showErrorDialog(
            context,
            context.getString(R.string.no_internet_connection),
            context.getString(R.string.no_internet_connection_text, serverIp)
        )
    }
}
suspend fun unknownExceptionCatch (context: Context, isAppVisible: Boolean, message: String) {
    if (isAppVisible) {
        showErrorDialog(
            context,
            context.getString(R.string.unknown_error),
            message
        )
    }
}