package com.rustscape.client.protocol

/**
 * ISAAC (Indirection, Shift, Accumulate, Add, and Count) cipher implementation
 *
 * ISAAC is a cryptographically secure pseudorandom number generator used in the
 * RuneScape protocol for encrypting packet opcodes. This implementation follows
 * the original ISAAC specification by Bob Jenkins.
 *
 * Reference: http://www.burtleburtle.net/bob/rand/isaacafa.html
 *
 * This is the Kotlin Multiplatform port of the TypeScript Isaac implementation,
 * providing identical functionality across JVM, JS/WASM, and Native targets.
 */
class Isaac(seed: IntArray) {

    /** Results buffer - holds generated random values */
    private val results = IntArray(SIZE)

    /** Internal state memory */
    private val memory = IntArray(SIZE)

    /** Accumulator */
    private var aa: Int = 0

    /** Previous result */
    private var bb: Int = 0

    /** Counter */
    private var cc: Int = 0

    /** Current position in results buffer */
    private var count: Int = 0

    init {
        initialize(seed)
    }

    /**
     * Create a new ISAAC cipher from 4 seed values (common RS usage)
     */
    constructor(seed0: Int, seed1: Int, seed2: Int, seed3: Int) : this(intArrayOf(seed0, seed1, seed2, seed3))

    /**
     * Initialize the cipher with the given seed
     */
    private fun initialize(seed: IntArray) {
        // Initialize the results buffer with the seed
        for (i in 0 until minOf(seed.size, SIZE)) {
            results[i] = seed[i]
        }

        // Initialize with golden ratio
        var a = GOLDEN_RATIO
        var b = GOLDEN_RATIO
        var c = GOLDEN_RATIO
        var d = GOLDEN_RATIO
        var e = GOLDEN_RATIO
        var f = GOLDEN_RATIO
        var g = GOLDEN_RATIO
        var h = GOLDEN_RATIO

        // Scramble the initial values (4 iterations)
        repeat(4) {
            val mixed = mix(a, b, c, d, e, f, g, h)
            a = mixed[0]; b = mixed[1]; c = mixed[2]; d = mixed[3]
            e = mixed[4]; f = mixed[5]; g = mixed[6]; h = mixed[7]
        }

        // Fill the memory array with mixed seed values
        for (i in 0 until SIZE step 8) {
            a += results[i]
            b += results[i + 1]
            c += results[i + 2]
            d += results[i + 3]
            e += results[i + 4]
            f += results[i + 5]
            g += results[i + 6]
            h += results[i + 7]

            val mixed = mix(a, b, c, d, e, f, g, h)
            a = mixed[0]; b = mixed[1]; c = mixed[2]; d = mixed[3]
            e = mixed[4]; f = mixed[5]; g = mixed[6]; h = mixed[7]

            memory[i] = a
            memory[i + 1] = b
            memory[i + 2] = c
            memory[i + 3] = d
            memory[i + 4] = e
            memory[i + 5] = f
            memory[i + 6] = g
            memory[i + 7] = h
        }

        // Second pass to further diffuse the seed
        for (i in 0 until SIZE step 8) {
            a += memory[i]
            b += memory[i + 1]
            c += memory[i + 2]
            d += memory[i + 3]
            e += memory[i + 4]
            f += memory[i + 5]
            g += memory[i + 6]
            h += memory[i + 7]

            val mixed = mix(a, b, c, d, e, f, g, h)
            a = mixed[0]; b = mixed[1]; c = mixed[2]; d = mixed[3]
            e = mixed[4]; f = mixed[5]; g = mixed[6]; h = mixed[7]

            memory[i] = a
            memory[i + 1] = b
            memory[i + 2] = c
            memory[i + 3] = d
            memory[i + 4] = e
            memory[i + 5] = f
            memory[i + 6] = g
            memory[i + 7] = h
        }

        // Generate initial results
        generate()
        count = SIZE
    }

    /**
     * Mix function for ISAAC initialization
     * Uses the standard ISAAC mixing schedule
     */
    private fun mix(
        a: Int, b: Int, c: Int, d: Int,
        e: Int, f: Int, g: Int, h: Int
    ): IntArray {
        var va = a;
        var vb = b;
        var vc = c;
        var vd = d
        var ve = e;
        var vf = f;
        var vg = g;
        var vh = h

        va = va xor (vb shl 11); vd += va; vb += vc
        vb = vb xor (vc ushr 2); ve += vb; vc += vd
        vc = vc xor (vd shl 8); vf += vc; vd += ve
        vd = vd xor (ve ushr 16); vg += vd; ve += vf
        ve = ve xor (vf shl 10); vh += ve; vf += vg
        vf = vf xor (vg ushr 4); va += vf; vg += vh
        vg = vg xor (vh shl 8); vb += vg; vh += va
        vh = vh xor (va ushr 9); vc += vh; va += vb

        return intArrayOf(va, vb, vc, vd, ve, vf, vg, vh)
    }

    /**
     * Generate 256 new random values
     */
    private fun generate() {
        cc++
        bb += cc

        for (i in 0 until SIZE) {
            val x = memory[i]

            // Rotate accumulator based on position
            aa = when (i and 3) {
                0 -> aa xor (aa shl 13)
                1 -> aa xor (aa ushr 6)
                2 -> aa xor (aa shl 2)
                3 -> aa xor (aa ushr 16)
                else -> aa
            }

            aa += memory[(i + 128) and MASK]

            val y = memory[(x ushr 2) and MASK] + aa + bb
            memory[i] = y

            bb = memory[(y ushr 10) and MASK] + x
            results[i] = bb
        }
    }

    /**
     * Get the next random value from the generator
     * @returns A 32-bit integer
     */
    fun next(): Int {
        if (count == 0) {
            generate()
            count = SIZE
        }
        count--
        return results[count]
    }

    /**
     * Get the next random value and return only the lower 8 bits
     * Used for encrypting packet opcodes in RS protocol
     * @returns A value between 0-255
     */
    fun nextByte(): Int {
        return next() and 0xFF
    }

    /**
     * Peek at the next value without advancing the generator
     * @returns The next value that would be returned by next()
     */
    fun peek(): Int {
        return if (count == 0) {
            results[SIZE - 1]
        } else {
            results[count - 1]
        }
    }

    companion object {
        /** Size of the ISAAC state array (must be a power of 2) */
        private const val SIZE = 256

        /** Mask for array indexing (SIZE - 1) */
        private const val MASK = SIZE - 1

        /** Golden ratio constant used in initialization */
        private const val GOLDEN_RATIO: Int = 0x9e3779b9.toInt()

        /**
         * Create a new ISAAC cipher from 4 seed values (common RS usage)
         */
        fun fromSeeds(seed0: Int, seed1: Int, seed2: Int, seed3: Int): Isaac {
            return Isaac(intArrayOf(seed0, seed1, seed2, seed3))
        }
    }
}

/**
 * Paired ISAAC ciphers for encoding and decoding
 *
 * In the RS protocol, client and server each have an encode/decode pair
 * with related seeds (server decode = client encode + 50)
 */
class IsaacPair private constructor(
    /** Cipher for encoding outgoing packets */
    val encode: Isaac,
    /** Cipher for decoding incoming packets */
    val decode: Isaac
) {

    /**
     * Encode a packet opcode for sending
     * @param opcode The raw packet opcode (0-255)
     * @returns The encoded opcode
     */
    fun encodeOpcode(opcode: Int): Int {
        return (opcode + encode.nextByte()) and 0xFF
    }

    /**
     * Decode a received packet opcode
     * @param encoded The encoded packet opcode
     * @returns The decoded opcode (0-255)
     */
    fun decodeOpcode(encoded: Int): Int {
        return (encoded - decode.nextByte()) and 0xFF
    }

    companion object {
        /**
         * Create an ISAAC pair for the client side
         *
         * The client uses:
         * - Original seeds for encoding (what server will decode)
         * - Seeds + 50 for decoding (what server encoded)
         *
         * @param seeds Array of 4 seed values from the login process
         */
        fun forClient(seeds: IntArray): IsaacPair {
            require(seeds.size >= 4) { "Seeds must contain at least 4 values" }

            // Encode cipher uses the original seeds
            val encodeCipher = Isaac(seeds)

            // Decode cipher uses seeds + 50 (to decode what server encoded)
            val decodeSeeds = intArrayOf(
                seeds[0] + 50,
                seeds[1] + 50,
                seeds[2] + 50,
                seeds[3] + 50
            )
            val decodeCipher = Isaac(decodeSeeds)

            return IsaacPair(encodeCipher, decodeCipher)
        }

        /**
         * Create an ISAAC pair for the server side
         *
         * The server uses:
         * - Original seeds for decoding (what client encoded)
         * - Seeds + 50 for encoding (what client will decode)
         *
         * @param seeds Array of 4 seed values from the login process
         */
        fun forServer(seeds: IntArray): IsaacPair {
            require(seeds.size >= 4) { "Seeds must contain at least 4 values" }

            // Decode cipher uses the original seeds (what client encoded)
            val decodeCipher = Isaac(seeds)

            // Encode cipher uses seeds + 50 (what client will decode)
            val encodeSeeds = intArrayOf(
                seeds[0] + 50,
                seeds[1] + 50,
                seeds[2] + 50,
                seeds[3] + 50
            )
            val encodeCipher = Isaac(encodeSeeds)

            return IsaacPair(encodeCipher, decodeCipher)
        }

        /**
         * Create an ISAAC pair for the client side from 4 integer seeds
         */
        fun forClient(seed0: Int, seed1: Int, seed2: Int, seed3: Int): IsaacPair {
            return forClient(intArrayOf(seed0, seed1, seed2, seed3))
        }

        /**
         * Create an ISAAC pair for the server side from 4 integer seeds
         */
        fun forServer(seed0: Int, seed1: Int, seed2: Int, seed3: Int): IsaacPair {
            return forServer(intArrayOf(seed0, seed1, seed2, seed3))
        }
    }
}
