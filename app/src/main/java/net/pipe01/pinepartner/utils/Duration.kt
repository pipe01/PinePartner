package net.pipe01.pinepartner.utils

import java.time.Duration

fun Duration.toMinutesSeconds(): String {
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}