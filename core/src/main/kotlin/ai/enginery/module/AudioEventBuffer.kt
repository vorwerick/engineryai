package ai.enginery.module

import java.nio.ByteBuffer

class AudioEventBuffer(val eventId: Int) {

    val buffer :MutableList<ByteArray> = mutableListOf()
    var lastWrite: Long? = null


    fun joinSample(eventId: Int, byteArray: ByteArray) {
        if (eventId != this.eventId) {
            return
        }
        buffer.add(byteArray)
        lastWrite = System.currentTimeMillis()
    }

    fun getAudio(): ByteArray {
        return buffer.flatMap { it.toList() }.toByteArray()
    }
}
