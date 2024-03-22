package net.pipe01.pinepartner.scripting

import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeConsole
import org.mozilla.javascript.ScriptStackElement
import org.mozilla.javascript.Scriptable

class ConsolePrinter(private val onNewEvent: (EventSeverity, String) -> Unit) : NativeConsole.ConsolePrinter {
    override fun print(cx: Context?, scope: Scriptable?, level: NativeConsole.Level?, args: Array<out Any>?, stack: Array<out ScriptStackElement>?) {
        onNewEvent(
            when (level) {
                NativeConsole.Level.ERROR -> EventSeverity.ERROR
                NativeConsole.Level.WARN -> EventSeverity.WARN
                else -> EventSeverity.INFO
            },
            args?.joinToString(" ") ?: ""
        )
    }
}