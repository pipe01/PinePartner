package net.pipe01.pinepartner.utils

interface Event<T> {
    fun addListener(listener: (T) -> Unit): () -> Unit
    fun removeListener(listener: (T) -> Unit)
}

class EventEmitter<T> : Event<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    override fun addListener(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)

        return { listeners.remove(listener) }
    }

    override fun removeListener(listener: (T) -> Unit) {
        listeners.remove(listener)
    }

    fun emit(event: T) {
        listeners.forEach { it(event) }
    }

    fun toEvent(): Event<T> = this
}