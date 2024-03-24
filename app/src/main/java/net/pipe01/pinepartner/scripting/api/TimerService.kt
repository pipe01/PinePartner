package net.pipe01.pinepartner.scripting.api

import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

class TimerService : ApiScriptableObject(TimerService::class) {
    private val minPeriod = 100

    private val timers = mutableMapOf<Int, Timer>()

    override fun finalize() {
        super.finalize()

        timers.values.forEach { it.cancel() }
    }

    private fun generateId(): Int {
        var id: Int
        do {
            id = (1..Int.MAX_VALUE).random()
        } while (timers.containsKey(id))

        return id
    }

    @JSFunction
    fun setInterval(callback: Function, period: Int): Int {
        if (period < minPeriod) {
            throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Period must be at least $minPeriod"))
        }

        val id = generateId()

        val timer = Timer()
        timers[id] = timer

        timer.scheduleAtFixedRate(period.toLong(), period.toLong()) {
            enterAndCall(callback)
        }

        return id
    }

    @JSFunction
    fun setTimeout(callback: Function, delay: Int): Int {
        if (delay < 0) {
            throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Delay must be positive"))
        }

        val id = generateId()

        val timer = Timer()
        timers[id] = timer

        timer.schedule(delay.toLong()) {
            clear(id)
            enterAndCall(callback)
        }

        return id
    }

    @JSFunction
    fun clear(id: Int) {
        timers.remove(id)?.cancel()
    }
}