/**
 * ISAAC (Indirection, Shift, Accumulate, Add, and Count) cipher implementation
 *
 * ISAAC is a cryptographically secure pseudorandom number generator used in the
 * RuneScape protocol for encrypting packet opcodes. This implementation follows
 * the original ISAAC specification by Bob Jenkins.
 *
 * Reference: http://www.burtleburtle.net/bob/rand/isaacafa.html
 */

/** Size of the ISAAC state array (must be a power of 2) */
const SIZE = 256;

/** Mask for array indexing (SIZE - 1) */
const MASK = SIZE - 1;

/** Golden ratio constant used in initialization */
const GOLDEN_RATIO = 0x9e3779b9;

/**
 * ISAAC cipher state
 */
export class Isaac {
    /** Results buffer */
    private results: Uint32Array;
    /** Internal state */
    private memory: Uint32Array;
    /** Accumulator */
    private aa: number;
    /** Previous result */
    private bb: number;
    /** Counter */
    private cc: number;
    /** Current position in results buffer */
    private count: number;

    /**
     * Create a new ISAAC cipher with the given seed
     * @param seed Array of seed values (typically 4 values from login)
     */
    constructor(seed: number[]) {
        this.results = new Uint32Array(SIZE);
        this.memory = new Uint32Array(SIZE);
        this.aa = 0;
        this.bb = 0;
        this.cc = 0;
        this.count = 0;

        this.init(seed);
    }

    /**
     * Create a new ISAAC cipher from 4 seed values (common RS usage)
     */
    static fromSeeds(seed0: number, seed1: number, seed2: number, seed3: number): Isaac {
        return new Isaac([seed0, seed1, seed2, seed3]);
    }

    /**
     * Initialize the cipher with the given seed
     */
    private init(seed: number[]): void {
        // Initialize the results buffer with the seed
        for (let i = 0; i < Math.min(seed.length, SIZE); i++) {
            this.results[i] = seed[i] >>> 0; // Ensure unsigned 32-bit
        }

        // Initialize with golden ratio
        let a = GOLDEN_RATIO;
        let b = GOLDEN_RATIO;
        let c = GOLDEN_RATIO;
        let d = GOLDEN_RATIO;
        let e = GOLDEN_RATIO;
        let f = GOLDEN_RATIO;
        let g = GOLDEN_RATIO;
        let h = GOLDEN_RATIO;

        // Scramble the initial values (4 iterations)
        for (let i = 0; i < 4; i++) {
            [a, b, c, d, e, f, g, h] = this.mix(a, b, c, d, e, f, g, h);
        }

        // Fill the memory array with mixed seed values
        for (let i = 0; i < SIZE; i += 8) {
            a = (a + this.results[i]) >>> 0;
            b = (b + this.results[i + 1]) >>> 0;
            c = (c + this.results[i + 2]) >>> 0;
            d = (d + this.results[i + 3]) >>> 0;
            e = (e + this.results[i + 4]) >>> 0;
            f = (f + this.results[i + 5]) >>> 0;
            g = (g + this.results[i + 6]) >>> 0;
            h = (h + this.results[i + 7]) >>> 0;

            [a, b, c, d, e, f, g, h] = this.mix(a, b, c, d, e, f, g, h);

            this.memory[i] = a;
            this.memory[i + 1] = b;
            this.memory[i + 2] = c;
            this.memory[i + 3] = d;
            this.memory[i + 4] = e;
            this.memory[i + 5] = f;
            this.memory[i + 6] = g;
            this.memory[i + 7] = h;
        }

        // Second pass to further diffuse the seed
        for (let i = 0; i < SIZE; i += 8) {
            a = (a + this.memory[i]) >>> 0;
            b = (b + this.memory[i + 1]) >>> 0;
            c = (c + this.memory[i + 2]) >>> 0;
            d = (d + this.memory[i + 3]) >>> 0;
            e = (e + this.memory[i + 4]) >>> 0;
            f = (f + this.memory[i + 5]) >>> 0;
            g = (g + this.memory[i + 6]) >>> 0;
            h = (h + this.memory[i + 7]) >>> 0;

            [a, b, c, d, e, f, g, h] = this.mix(a, b, c, d, e, f, g, h);

            this.memory[i] = a;
            this.memory[i + 1] = b;
            this.memory[i + 2] = c;
            this.memory[i + 3] = d;
            this.memory[i + 4] = e;
            this.memory[i + 5] = f;
            this.memory[i + 6] = g;
            this.memory[i + 7] = h;
        }

        // Generate initial results
        this.generate();
        this.count = SIZE;
    }

    /**
     * Mix function for ISAAC initialization
     * Uses the standard ISAAC mixing schedule
     */
    private mix(
        a: number,
        b: number,
        c: number,
        d: number,
        e: number,
        f: number,
        g: number,
        h: number
    ): [number, number, number, number, number, number, number, number] {
        a ^= b << 11;
        d = (d + a) >>> 0;
        b = (b + c) >>> 0;

        b ^= c >>> 2;
        e = (e + b) >>> 0;
        c = (c + d) >>> 0;

        c ^= d << 8;
        f = (f + c) >>> 0;
        d = (d + e) >>> 0;

        d ^= e >>> 16;
        g = (g + d) >>> 0;
        e = (e + f) >>> 0;

        e ^= f << 10;
        h = (h + e) >>> 0;
        f = (f + g) >>> 0;

        f ^= g >>> 4;
        a = (a + f) >>> 0;
        g = (g + h) >>> 0;

        g ^= h << 8;
        b = (b + g) >>> 0;
        h = (h + a) >>> 0;

        h ^= a >>> 9;
        c = (c + h) >>> 0;
        a = (a + b) >>> 0;

        return [a >>> 0, b >>> 0, c >>> 0, d >>> 0, e >>> 0, f >>> 0, g >>> 0, h >>> 0];
    }

    /**
     * Generate 256 new random values
     */
    private generate(): void {
        this.cc = (this.cc + 1) >>> 0;
        this.bb = (this.bb + this.cc) >>> 0;

        for (let i = 0; i < SIZE; i++) {
            const x = this.memory[i];

            // Rotate accumulator based on position
            switch (i & 3) {
                case 0:
                    this.aa ^= this.aa << 13;
                    break;
                case 1:
                    this.aa ^= this.aa >>> 6;
                    break;
                case 2:
                    this.aa ^= this.aa << 2;
                    break;
                case 3:
                    this.aa ^= this.aa >>> 16;
                    break;
            }
            this.aa = this.aa >>> 0;

            this.aa = (this.memory[(i + 128) & MASK] + this.aa) >>> 0;

            const y =
                ((this.memory[(x >>> 2) & MASK] + this.aa + this.bb) >>> 0) >>> 0;

            this.memory[i] = y;
            this.bb = ((this.memory[(y >>> 10) & MASK] + x) >>> 0) >>> 0;
            this.results[i] = this.bb;
        }
    }

    /**
     * Get the next random value from the generator
     * @returns A 32-bit unsigned integer
     */
    next(): number {
        if (this.count === 0) {
            this.generate();
            this.count = SIZE;
        }
        this.count--;
        return this.results[this.count];
    }

    /**
     * Get the next random value and return only the lower 8 bits
     * Used for encrypting packet opcodes in RS protocol
     * @returns A value between 0-255
     */
    nextByte(): number {
        return this.next() & 0xff;
    }

    /**
     * Peek at the next value without advancing the generator
     * @returns The next value that would be returned by next()
     */
    peek(): number {
        if (this.count === 0) {
            return this.results[SIZE - 1];
        }
        return this.results[this.count - 1];
    }
}

/**
 * Paired ISAAC ciphers for encoding and decoding
 *
 * In the RS protocol, client and server each have an encode/decode pair
 * with related seeds (server decode = client encode + 50)
 */
export class IsaacPair {
    /** Cipher for encoding outgoing packets */
    public encode: Isaac;
    /** Cipher for decoding incoming packets */
    public decode: Isaac;

    /**
     * Create an ISAAC pair for the client side
     *
     * The client uses:
     * - Original seeds for encoding (what server will decode)
     * - Seeds + 50 for decoding (what server encoded)
     *
     * @param seeds Array of 4 seed values from the login process
     */
    constructor(seeds: [number, number, number, number]) {
        // Encode cipher uses the original seeds
        this.encode = new Isaac(seeds);

        // Decode cipher uses seeds + 50 (to decode what server encoded)
        const decodeSeeds: [number, number, number, number] = [
            (seeds[0] + 50) >>> 0,
            (seeds[1] + 50) >>> 0,
            (seeds[2] + 50) >>> 0,
            (seeds[3] + 50) >>> 0,
        ];
        this.decode = new Isaac(decodeSeeds);
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
    static forServer(seeds: [number, number, number, number]): IsaacPair {
        // We swap encode/decode compared to client
        const pair = new IsaacPair(seeds);

        // Swap the ciphers
        const temp = pair.encode;
        pair.encode = pair.decode;
        pair.decode = temp;

        return pair;
    }

    /**
     * Encode a packet opcode for sending
     * @param opcode The raw packet opcode (0-255)
     * @returns The encoded opcode
     */
    encodeOpcode(opcode: number): number {
        return (opcode + this.encode.nextByte()) & 0xff;
    }

    /**
     * Decode a received packet opcode
     * @param encoded The encoded packet opcode
     * @returns The decoded opcode (0-255)
     */
    decodeOpcode(encoded: number): number {
        return (encoded - this.decode.nextByte()) & 0xff;
    }
}

export default Isaac;
