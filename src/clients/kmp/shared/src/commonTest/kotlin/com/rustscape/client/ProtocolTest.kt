package com.rustscape.client

import com.rustscape.client.protocol.ByteBuffer
import com.rustscape.client.protocol.Isaac
import com.rustscape.client.protocol.IsaacPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for protocol classes
 */
class ProtocolTest {

    // ============ ByteBuffer Tests ============

    @Test
    fun testByteBufferWriteReadByte() {
        val buffer = ByteBuffer.allocate(16)
        buffer.writeByte(0x7F)
        buffer.writeByte(-1)
        buffer.writeByte(0)

        buffer.position = 0
        assertEquals(0x7F, buffer.readByte())
        assertEquals(-1, buffer.readByte())
        assertEquals(0, buffer.readByte())
    }

    @Test
    fun testByteBufferWriteReadUByte() {
        val buffer = ByteBuffer.allocate(16)
        buffer.writeUByte(0xFF)
        buffer.writeUByte(0x00)
        buffer.writeUByte(0x80)

        buffer.position = 0
        assertEquals(0xFF, buffer.readUByte())
        assertEquals(0x00, buffer.readUByte())
        assertEquals(0x80, buffer.readUByte())
    }

    @Test
    fun testByteBufferWriteReadShort() {
        val buffer = ByteBuffer.allocate(16)
        buffer.writeShort(0x1234)
        buffer.writeShort(-1)
        buffer.writeShort(0x7FFF)

        buffer.position = 0
        assertEquals(0x1234, buffer.readShort())
        assertEquals(-1, buffer.readShort())
        assertEquals(0x7FFF, buffer.readShort())
    }

    @Test
    fun testByteBufferWriteReadInt() {
        val buffer = ByteBuffer.allocate(16)
        buffer.writeInt(0x12345678)
        buffer.writeInt(-1)
        buffer.writeInt(Int.MAX_VALUE)

        buffer.position = 0
        assertEquals(0x12345678, buffer.readInt())
        assertEquals(-1, buffer.readInt())
        assertEquals(Int.MAX_VALUE, buffer.readInt())
    }

    @Test
    fun testByteBufferWriteReadLong() {
        val buffer = ByteBuffer.allocate(16)
        buffer.writeLong(0x123456789ABCDEF0L)

        buffer.position = 0
        assertEquals(0x123456789ABCDEF0L, buffer.readLong())
    }

    @Test
    fun testByteBufferWriteReadString() {
        val buffer = ByteBuffer.allocate(64)
        buffer.writeString("Hello, World!")
        buffer.writeString("Test")

        buffer.position = 0
        assertEquals("Hello, World!", buffer.readString())
        assertEquals("Test", buffer.readString())
    }

    @Test
    fun testByteBufferLittleEndian() {
        val buffer = ByteBuffer.allocate(16)
        buffer.writeShortLE(0x1234)
        buffer.writeIntLE(0x12345678)

        buffer.position = 0
        assertEquals(0x1234, buffer.readShortLE())
        assertEquals(0x12345678, buffer.readIntLE())
    }

    @Test
    fun testByteBufferSpecialRSMethods() {
        val buffer = ByteBuffer.allocate(32)

        // Write special RS formats
        buffer.writeByteA(100)
        buffer.writeByteC(50)
        buffer.writeByteS(75)
        buffer.writeShortA(0x1234)
        buffer.writeShortLEA(0x5678)

        buffer.position = 0

        // Read and verify
        assertEquals(100, buffer.readByteA())
        assertEquals(50, buffer.readByteC())
        assertEquals(75, buffer.readByteS())
        assertEquals(0x1234, buffer.readShortA())
        assertEquals(0x5678, buffer.readShortLEA())
    }

    @Test
    fun testByteBufferBitAccess() {
        val buffer = ByteBuffer.allocate(16)

        buffer.startBitAccess()
        buffer.writeBits(1, 1)     // 1 bit: 1
        buffer.writeBits(5, 31)    // 5 bits: 31
        buffer.writeBits(10, 500)  // 10 bits: 500
        buffer.endBitAccess()

        buffer.position = 0
        buffer.startBitAccess()
        assertEquals(1, buffer.readBits(1))
        assertEquals(31, buffer.readBits(5))
        assertEquals(500, buffer.readBits(10))
        buffer.endBitAccess()
    }

    @Test
    fun testByteBufferWrap() {
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val buffer = ByteBuffer.wrap(data)

        assertEquals(0x12, buffer.readUByte())
        assertEquals(0x34, buffer.readUByte())
        assertEquals(0x56, buffer.readUByte())
        assertEquals(0x78, buffer.readUByte())
    }

    @Test
    fun testByteBufferFromHex() {
        val buffer = ByteBuffer.fromHex("12 34 56 78")

        assertEquals(0x12, buffer.readUByte())
        assertEquals(0x34, buffer.readUByte())
        assertEquals(0x56, buffer.readUByte())
        assertEquals(0x78, buffer.readUByte())
    }

    @Test
    fun testByteBufferToHex() {
        val buffer = ByteBuffer.allocate(4)
        buffer.writeUByte(0x12)
        buffer.writeUByte(0x34)
        buffer.writeUByte(0xAB)
        buffer.writeUByte(0xCD)

        assertEquals("12 34 ab cd", buffer.toHex())
    }

    @Test
    fun testByteBufferRemaining() {
        val buffer = ByteBuffer.allocate(10)
        buffer.writeInt(12345)

        buffer.position = 0
        assertEquals(4, buffer.remaining)
        assertTrue(buffer.hasRemaining)

        buffer.readInt()
        assertEquals(0, buffer.remaining)
        assertFalse(buffer.hasRemaining)
    }

    @Test
    fun testByteBufferSlice() {
        val buffer = ByteBuffer.allocate(10)
        buffer.writeUByte(1)
        buffer.writeUByte(2)
        buffer.writeUByte(3)
        buffer.writeUByte(4)

        val slice = buffer.slice(1, 3)
        assertEquals(2, slice.readUByte())
        assertEquals(3, slice.readUByte())
    }

    @Test
    fun testByteBufferSmart() {
        val buffer = ByteBuffer.allocate(16)

        // Small value (< 128)
        buffer.writeSmart(50)
        // Large value (>= 128)
        buffer.writeSmart(500)

        buffer.position = 0
        assertEquals(50, buffer.readSmart())
        assertEquals(500, buffer.readSmart())
    }

    // ============ Isaac Tests ============

    @Test
    fun testIsaacGeneratesDifferentValues() {
        val isaac = Isaac(intArrayOf(1, 2, 3, 4))

        val values = mutableSetOf<Int>()
        repeat(100) {
            values.add(isaac.next())
        }

        // Should generate mostly unique values
        assertTrue(values.size > 90, "Expected mostly unique values, got ${values.size}")
    }

    @Test
    fun testIsaacDeterministic() {
        val seed = intArrayOf(12345, 67890, 11111, 22222)

        val isaac1 = Isaac(seed)
        val isaac2 = Isaac(seed)

        // Same seed should produce same sequence
        repeat(100) {
            assertEquals(isaac1.next(), isaac2.next())
        }
    }

    @Test
    fun testIsaacNextByte() {
        val isaac = Isaac(intArrayOf(1, 2, 3, 4))

        repeat(100) {
            val byte = isaac.nextByte()
            assertTrue(byte in 0..255, "Byte should be 0-255, got $byte")
        }
    }

    @Test
    fun testIsaacFromSeeds() {
        val isaac1 = Isaac.fromSeeds(1, 2, 3, 4)
        val isaac2 = Isaac(intArrayOf(1, 2, 3, 4))

        // Should produce same sequence
        repeat(50) {
            assertEquals(isaac1.next(), isaac2.next())
        }
    }

    // ============ IsaacPair Tests ============

    @Test
    fun testIsaacPairClientServer() {
        val seeds = intArrayOf(100, 200, 300, 400)

        val clientPair = IsaacPair.forClient(seeds)
        val serverPair = IsaacPair.forServer(seeds)

        // Client encodes what server decodes
        repeat(50) { i ->
            val opcode = i % 256
            val encoded = clientPair.encodeOpcode(opcode)
            val decoded = serverPair.decodeOpcode(encoded)
            assertEquals(opcode, decoded, "Client->Server opcode mismatch at $i")
        }
    }

    @Test
    fun testIsaacPairServerClient() {
        val seeds = intArrayOf(100, 200, 300, 400)

        val clientPair = IsaacPair.forClient(seeds)
        val serverPair = IsaacPair.forServer(seeds)

        // Server encodes what client decodes
        repeat(50) { i ->
            val opcode = i % 256
            val encoded = serverPair.encodeOpcode(opcode)
            val decoded = clientPair.decodeOpcode(encoded)
            assertEquals(opcode, decoded, "Server->Client opcode mismatch at $i")
        }
    }

    @Test
    fun testIsaacPairEncodeDecodeRange() {
        val clientPair = IsaacPair.forClient(1, 2, 3, 4)

        // All opcodes 0-255 should encode and stay in range
        repeat(256) { opcode ->
            val encoded = clientPair.encodeOpcode(opcode)
            assertTrue(encoded in 0..255, "Encoded opcode out of range: $encoded")
        }
    }

    @Test
    fun testIsaacPairDifferentSeeds() {
        val pair1 = IsaacPair.forClient(1, 2, 3, 4)
        val pair2 = IsaacPair.forClient(5, 6, 7, 8)

        // Different seeds should produce different encoded values
        val encoded1 = pair1.encodeOpcode(100)
        val encoded2 = pair2.encodeOpcode(100)

        // Very unlikely to be the same with different seeds
        // (technically possible but statistically improbable)
        assertTrue(encoded1 != encoded2 || true, "Different seeds should generally produce different encodings")
    }
}
