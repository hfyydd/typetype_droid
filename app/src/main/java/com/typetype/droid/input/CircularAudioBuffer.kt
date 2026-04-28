package com.typetype.droid.input

import java.util.concurrent.atomic.AtomicInteger

class CircularAudioBuffer(capacity: Int) {
    private val buffer = Array(capacity) { FloatArray(0) }
    private val head = AtomicInteger(0)
    private val count = AtomicInteger(0)

    fun add(samples: FloatArray) {
        val pos = (head.get() + count.get()) % buffer.size
        buffer[pos] = samples
        if (count.get() < buffer.size) {
            count.incrementAndGet()
        } else {
            head.incrementAndGet()
        }
    }

    fun drain(consumer: (FloatArray) -> Unit) {
        var remaining = count.get()
        while (remaining > 0) {
            val index = head.get()
            consumer(buffer[index])
            head.set((index + 1) % buffer.size)
            count.decrementAndGet()
            remaining--
        }
    }

    fun clear() {
        head.set(0)
        count.set(0)
    }

    fun isEmpty() = count.get() == 0
}
