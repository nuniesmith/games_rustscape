package com.rustscape.client.protocol

/**
 * ByteBuffer - A cross-platform utility class for reading and writing binary data
 * Used for RS protocol packet handling.
 *
 * This is the Kotlin Multiplatform port of the TypeScript ByteBuffer implementation,
 * providing identical functionality across JVM, JS/WASM, and Native targets.
 */
class ByteBuffer private constructor(
    private var data: ByteArray,
    private var _capacity: Int
) {
    private var _position: Int = 0
    private var _bitPosition: Int = 0
    private var _inBitMode: Boolean = false

    /**
     * Create a new ByteBuffer with the specified initial capacity
     */
    constructor(initialCapacity: Int = 256) : this(ByteArray(initialCapacity), initialCapacity)

    // ============ Properties ============

    var position: Int
        get() = _position
        set(value) {
            _position = value
        }

    val length: Int
        get() = _capacity

    val remaining: Int
        get() = _capacity - _position

    val hasRemaining: Boolean
        get() = remaining > 0

    // ============ Reading Methods ============

    fun readByte(): Int {
        checkReadable(1)
        return data[_position++].toInt()
    }

    fun readUByte(): Int {
        checkReadable(1)
        return data[_position++].toInt() and 0xFF
    }

    fun readShort(): Int {
        checkReadable(2)
        val b1 = data[_position++].toInt() and 0xFF
        val b2 = data[_position++].toInt() and 0xFF
        val value = (b1 shl 8) or b2
        // Sign extend if negative
        return if (value >= 0x8000) value - 0x10000 else value
    }

    fun readUShort(): Int {
        checkReadable(2)
        val b1 = data[_position++].toInt() and 0xFF
        val b2 = data[_position++].toInt() and 0xFF
        return (b1 shl 8) or b2
    }

    fun readShortLE(): Int {
        checkReadable(2)
        val b1 = data[_position++].toInt() and 0xFF
        val b2 = data[_position++].toInt() and 0xFF
        val value = (b2 shl 8) or b1
        return if (value >= 0x8000) value - 0x10000 else value
    }

    fun readUShortLE(): Int {
        checkReadable(2)
        val b1 = data[_position++].toInt() and 0xFF
        val b2 = data[_position++].toInt() and 0xFF
        return (b2 shl 8) or b1
    }

    fun readInt24(): Int {
        checkReadable(3)
        val b1 = data[_position++].toInt() and 0xFF
        val b2 = data[_position++].toInt() and 0xFF
        val b3 = data[_position++].toInt() and 0xFF
        return (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readInt(): Int {
        checkReadable(4)
        val b1 = data[_position++].toInt() and 0xFF
        val b2 = data[_position++].toInt() and 0xFF
        val b3 = data[_position++].toInt() and 0xFF
        val b4 = data[_position++].toInt() and 0xFF
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    fun readUInt(): Long {
        checkReadable(4)
        val b1 = data[_position++].toLong() and 0xFF
        val b2 = data[_position++].toLong() and 0xFF
        val b3 = data[_position++].toLong() and 0xFF
        val b4 = data[_position++].toLong() and 0xFF
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    fun readIntLE(): Int {
        checkReadable(4)
        val b1 = data[_position++].toInt() and 0xFF
        val b2 = data[_position++].toInt() and 0xFF
        val b3 = data[_position++].toInt() and 0xFF
        val b4 = data[_position++].toInt() and 0xFF
        return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
    }

    fun readLong(): Long {
        checkReadable(8)
        val high = readUInt()
        val low = readUInt()
        return (high shl 32) or low
    }

    fun readString(): String {
        val sb = StringBuilder()
        var char: Int
        while (true) {
            char = readUByte()
            if (char == 0) break
            sb.append(char.toChar())
        }
        return sb.toString()
    }

    fun readStringJagex(): String {
        val marker = readUByte()
        if (marker != 0) {
            throw IllegalStateException("Invalid Jagex string marker: $marker")
        }
        return readString()
    }

    fun readBytes(length: Int): ByteArray {
        checkReadable(length)
        val bytes = data.copyOfRange(_position, _position + length)
        _position += length
        return bytes
    }

    fun readSmart(): Int {
        val peek = data[_position].toInt() and 0xFF
        return if (peek < 128) {
            readUByte()
        } else {
            readUShort() - 32768
        }
    }

    fun readSmartSigned(): Int {
        val peek = data[_position].toInt() and 0xFF
        return if (peek < 128) {
            readUByte() - 64
        } else {
            readUShort() - 49152
        }
    }

    fun readBigSmart(): Int {
        val peek = data[_position].toInt()
        return if (peek < 0) {
            readInt() and 0x7FFFFFFF
        } else {
            val value = readUShort()
            if (value == 32767) -1 else value
        }
    }

    // ============ Special RS Reading Methods ============

    fun readByteA(): Int {
        return (readUByte() - 128) and 0xFF
    }

    fun readByteC(): Int {
        return -readByte()
    }

    fun readByteS(): Int {
        return (128 - readUByte()) and 0xFF
    }

    fun readShortA(): Int {
        val b1 = readUByte()
        val b2 = (readUByte() - 128) and 0xFF
        return (b1 shl 8) or b2
    }

    fun readShortLEA(): Int {
        val b1 = (readUByte() - 128) and 0xFF
        val b2 = readUByte()
        return (b2 shl 8) or b1
    }

    fun readIntV1(): Int {
        val b1 = readUByte()
        val b2 = readUByte()
        val b3 = readUByte()
        val b4 = readUByte()
        return (b3 shl 24) or (b4 shl 16) or (b1 shl 8) or b2
    }

    fun readIntV2(): Int {
        val b1 = readUByte()
        val b2 = readUByte()
        val b3 = readUByte()
        val b4 = readUByte()
        return (b2 shl 24) or (b1 shl 16) or (b4 shl 8) or b3
    }

    // ============ Writing Methods ============

    fun writeByte(value: Int): ByteBuffer {
        ensureCapacity(1)
        data[_position++] = value.toByte()
        return this
    }

    fun writeUByte(value: Int): ByteBuffer {
        ensureCapacity(1)
        data[_position++] = (value and 0xFF).toByte()
        return this
    }

    fun writeShort(value: Int): ByteBuffer {
        ensureCapacity(2)
        data[_position++] = (value shr 8).toByte()
        data[_position++] = value.toByte()
        return this
    }

    fun writeUShort(value: Int): ByteBuffer {
        ensureCapacity(2)
        data[_position++] = ((value shr 8) and 0xFF).toByte()
        data[_position++] = (value and 0xFF).toByte()
        return this
    }

    fun writeShortLE(value: Int): ByteBuffer {
        ensureCapacity(2)
        data[_position++] = value.toByte()
        data[_position++] = (value shr 8).toByte()
        return this
    }

    fun writeInt24(value: Int): ByteBuffer {
        ensureCapacity(3)
        data[_position++] = ((value shr 16) and 0xFF).toByte()
        data[_position++] = ((value shr 8) and 0xFF).toByte()
        data[_position++] = (value and 0xFF).toByte()
        return this
    }

    fun writeInt(value: Int): ByteBuffer {
        ensureCapacity(4)
        data[_position++] = (value shr 24).toByte()
        data[_position++] = (value shr 16).toByte()
        data[_position++] = (value shr 8).toByte()
        data[_position++] = value.toByte()
        return this
    }

    fun writeIntLE(value: Int): ByteBuffer {
        ensureCapacity(4)
        data[_position++] = value.toByte()
        data[_position++] = (value shr 8).toByte()
        data[_position++] = (value shr 16).toByte()
        data[_position++] = (value shr 24).toByte()
        return this
    }

    fun writeLong(value: Long): ByteBuffer {
        ensureCapacity(8)
        writeInt((value shr 32).toInt())
        writeInt(value.toInt())
        return this
    }

    fun writeString(value: String): ByteBuffer {
        for (char in value) {
            writeUByte(char.code)
        }
        writeUByte(0) // Null terminator
        return this
    }

    fun writeStringJagex(value: String): ByteBuffer {
        writeUByte(0) // Jagex string marker
        return writeString(value)
    }

    fun writeBytes(bytes: ByteArray): ByteBuffer {
        ensureCapacity(bytes.size)
        bytes.copyInto(data, _position)
        _position += bytes.size
        return this
    }

    fun writeSmart(value: Int): ByteBuffer {
        return if (value < 128) {
            writeUByte(value)
        } else {
            writeUShort(value + 32768)
        }
    }

    // ============ Special RS Writing Methods ============

    fun writeByteA(value: Int): ByteBuffer {
        return writeUByte((value + 128) and 0xFF)
    }

    fun writeByteC(value: Int): ByteBuffer {
        return writeByte(-value)
    }

    fun writeByteS(value: Int): ByteBuffer {
        return writeUByte((128 - value) and 0xFF)
    }

    fun writeShortA(value: Int): ByteBuffer {
        writeUByte((value shr 8) and 0xFF)
        writeUByte((value + 128) and 0xFF)
        return this
    }

    fun writeShortLEA(value: Int): ByteBuffer {
        writeUByte((value + 128) and 0xFF)
        writeUByte((value shr 8) and 0xFF)
        return this
    }

    fun writeIntV1(value: Int): ByteBuffer {
        writeUByte((value shr 8) and 0xFF)
        writeUByte(value and 0xFF)
        writeUByte((value shr 24) and 0xFF)
        writeUByte((value shr 16) and 0xFF)
        return this
    }

    fun writeIntV2(value: Int): ByteBuffer {
        writeUByte((value shr 16) and 0xFF)
        writeUByte((value shr 24) and 0xFF)
        writeUByte(value and 0xFF)
        writeUByte((value shr 8) and 0xFF)
        return this
    }

    // ============ Bit Access Methods ============

    fun startBitAccess(): ByteBuffer {
        _bitPosition = _position * 8
        _inBitMode = true
        return this
    }

    fun endBitAccess(): ByteBuffer {
        _position = (_bitPosition + 7) / 8
        _inBitMode = false
        return this
    }

    fun readBits(count: Int): Int {
        check(_inBitMode) { "Not in bit access mode" }

        var bytePos = _bitPosition shr 3
        var bitOffset = 8 - (_bitPosition and 7)
        var value = 0
        var remaining = count

        _bitPosition += count

        while (remaining > bitOffset) {
            value += (data[bytePos++].toInt() and ((1 shl bitOffset) - 1)) shl (remaining - bitOffset)
            remaining -= bitOffset
            bitOffset = 8
        }

        value += if (remaining == bitOffset) {
            data[bytePos].toInt() and ((1 shl bitOffset) - 1)
        } else {
            (data[bytePos].toInt() shr (bitOffset - remaining)) and ((1 shl remaining) - 1)
        }

        return value
    }

    fun writeBits(count: Int, value: Int): ByteBuffer {
        check(_inBitMode) { "Not in bit access mode" }

        var bytePos = _bitPosition shr 3
        var bitOffset = 8 - (_bitPosition and 7)
        var remaining = count

        _bitPosition += count

        // Ensure capacity
        val neededBytes = (_bitPosition + 7) / 8
        ensureCapacity(neededBytes - _position)

        while (remaining > bitOffset) {
            var current = data[bytePos].toInt() and 0xFF
            current = current and ((1 shl bitOffset) - 1).inv()
            current = current or ((value shr (remaining - bitOffset)) and ((1 shl bitOffset) - 1))
            data[bytePos++] = current.toByte()
            remaining -= bitOffset
            bitOffset = 8
        }

        var current = data[bytePos].toInt() and 0xFF
        if (remaining == bitOffset) {
            current = current and ((1 shl bitOffset) - 1).inv()
            current = current or (value and ((1 shl bitOffset) - 1))
        } else {
            current = current and (((1 shl remaining) - 1) shl (bitOffset - remaining)).inv()
            current = current or ((value and ((1 shl remaining) - 1)) shl (bitOffset - remaining))
        }
        data[bytePos] = current.toByte()

        return this
    }

    // ============ Utility Methods ============

    fun toByteArray(): ByteArray {
        return data.copyOf(_position)
    }

    fun toFullByteArray(): ByteArray {
        return data.copyOf(_capacity)
    }

    fun slice(start: Int = 0, end: Int = _position): ByteBuffer {
        return wrap(data.copyOfRange(start, end))
    }

    fun reset(): ByteBuffer {
        _position = 0
        _bitPosition = 0
        _inBitMode = false
        return this
    }

    fun flip(): ByteBuffer {
        _capacity = _position
        _position = 0
        return this
    }

    fun skip(bytes: Int): ByteBuffer {
        _position += bytes
        return this
    }

    fun peek(): Int {
        return data[_position].toInt() and 0xFF
    }

    private fun checkReadable(bytes: Int) {
        if (_position + bytes > _capacity) {
            throw IndexOutOfBoundsException(
                "Cannot read $bytes bytes at position $_position (capacity: $_capacity)"
            )
        }
    }

    private fun ensureCapacity(additionalBytes: Int) {
        val required = _position + additionalBytes
        if (required > data.size) {
            val newSize = maxOf(required, data.size * 2)
            data = data.copyOf(newSize)
        }
        if (required > _capacity) {
            _capacity = required
        }
    }

    fun toHex(): String {
        return toByteArray().joinToString(" ") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    override fun toString(): String {
        return "ByteBuffer(position=$_position, capacity=$_capacity, data=${toHex()})"
    }

    // ============ Static Factory Methods ============

    companion object {
        /**
         * Allocate a new ByteBuffer with the specified size
         */
        fun allocate(size: Int): ByteBuffer {
            return ByteBuffer(size)
        }

        /**
         * Wrap an existing byte array in a ByteBuffer
         */
        fun wrap(data: ByteArray): ByteBuffer {
            return ByteBuffer(data.copyOf(), data.size).apply {
                _capacity = data.size
            }
        }

        /**
         * Create a ByteBuffer from a hex string (e.g., "01 02 03" or "010203")
         */
        fun fromHex(hex: String): ByteBuffer {
            val cleanHex = hex.replace(Regex("\\s"), "")
            val bytes = ByteArray(cleanHex.length / 2) { i ->
                cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return wrap(bytes)
        }
    }
}
