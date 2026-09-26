package com.example.llama.memory.vector

import java.nio.ByteBuffer
import java.nio.ByteOrder

object VectorSerializer {

    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer
            .allocate(vector.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)

        vector.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) {
            "Invalid vector byte length: ${bytes.size}"
        }

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.getFloat() }
    }
}
