/**
 * ByteBuffer - A utility class for reading and writing binary data
 * Used for RS protocol packet handling
 */
export class ByteBuffer {
    private data: DataView;
    private buffer: ArrayBuffer;
    private _position: number = 0;
    private _bitPosition: number = 0;
    private _inBitMode: boolean = false;

    constructor(sizeOrData: number | Uint8Array | ArrayBuffer) {
        if (typeof sizeOrData === 'number') {
            this.buffer = new ArrayBuffer(sizeOrData);
            this.data = new DataView(this.buffer);
        } else if (sizeOrData instanceof Uint8Array) {
            this.buffer = sizeOrData.buffer.slice(
                sizeOrData.byteOffset,
                sizeOrData.byteOffset + sizeOrData.byteLength
            );
            this.data = new DataView(this.buffer);
        } else {
            this.buffer = sizeOrData;
            this.data = new DataView(this.buffer);
        }
    }

    // ============ Properties ============

    get position(): number {
        return this._position;
    }

    set position(value: number) {
        this._position = value;
    }

    get length(): number {
        return this.buffer.byteLength;
    }

    get remaining(): number {
        return this.length - this._position;
    }

    get hasRemaining(): boolean {
        return this.remaining > 0;
    }

    // ============ Reading Methods ============

    readByte(): number {
        const value = this.data.getInt8(this._position);
        this._position += 1;
        return value;
    }

    readUByte(): number {
        const value = this.data.getUint8(this._position);
        this._position += 1;
        return value;
    }

    readShort(): number {
        const value = this.data.getInt16(this._position, false);
        this._position += 2;
        return value;
    }

    readUShort(): number {
        const value = this.data.getUint16(this._position, false);
        this._position += 2;
        return value;
    }

    readShortLE(): number {
        const value = this.data.getInt16(this._position, true);
        this._position += 2;
        return value;
    }

    readUShortLE(): number {
        const value = this.data.getUint16(this._position, true);
        this._position += 2;
        return value;
    }

    readInt24(): number {
        const b1 = this.readUByte();
        const b2 = this.readUByte();
        const b3 = this.readUByte();
        return (b1 << 16) | (b2 << 8) | b3;
    }

    readInt(): number {
        const value = this.data.getInt32(this._position, false);
        this._position += 4;
        return value;
    }

    readUInt(): number {
        const value = this.data.getUint32(this._position, false);
        this._position += 4;
        return value;
    }

    readIntLE(): number {
        const value = this.data.getInt32(this._position, true);
        this._position += 4;
        return value;
    }

    readLong(): bigint {
        const high = BigInt(this.data.getUint32(this._position, false));
        const low = BigInt(this.data.getUint32(this._position + 4, false));
        this._position += 8;
        return (high << 32n) | low;
    }

    readString(): string {
        let result = '';
        let char: number;
        while ((char = this.readUByte()) !== 0) {
            result += String.fromCharCode(char);
        }
        return result;
    }

    readStringJagex(): string {
        const marker = this.readUByte();
        if (marker !== 0) {
            throw new Error(`Invalid string marker: ${marker}`);
        }
        return this.readString();
    }

    readBytes(length: number): Uint8Array {
        const bytes = new Uint8Array(this.buffer, this._position, length);
        this._position += length;
        return new Uint8Array(bytes); // Return a copy
    }

    readSmart(): number {
        const peek = this.data.getUint8(this._position);
        if (peek < 128) {
            return this.readUByte();
        } else {
            return this.readUShort() - 32768;
        }
    }

    readSmartSigned(): number {
        const peek = this.data.getUint8(this._position);
        if (peek < 128) {
            return this.readUByte() - 64;
        } else {
            return this.readUShort() - 49152;
        }
    }

    readBigSmart(): number {
        const peek = this.data.getInt8(this._position);
        if (peek < 0) {
            return this.readInt() & 0x7fffffff;
        } else {
            const value = this.readUShort();
            return value === 32767 ? -1 : value;
        }
    }

    // ============ Special RS Reading Methods ============

    readByteA(): number {
        return (this.readUByte() - 128) & 0xff;
    }

    readByteC(): number {
        return -this.readByte();
    }

    readByteS(): number {
        return (128 - this.readUByte()) & 0xff;
    }

    readShortA(): number {
        const b1 = this.readUByte();
        const b2 = (this.readUByte() - 128) & 0xff;
        return (b1 << 8) | b2;
    }

    readShortLEA(): number {
        const b1 = (this.readUByte() - 128) & 0xff;
        const b2 = this.readUByte();
        return (b2 << 8) | b1;
    }

    readIntV1(): number {
        const b1 = this.readUByte();
        const b2 = this.readUByte();
        const b3 = this.readUByte();
        const b4 = this.readUByte();
        return (b3 << 24) | (b4 << 16) | (b1 << 8) | b2;
    }

    readIntV2(): number {
        const b1 = this.readUByte();
        const b2 = this.readUByte();
        const b3 = this.readUByte();
        const b4 = this.readUByte();
        return (b2 << 24) | (b1 << 16) | (b4 << 8) | b3;
    }

    // ============ Writing Methods ============

    writeByte(value: number): this {
        this.ensureCapacity(1);
        this.data.setInt8(this._position, value);
        this._position += 1;
        return this;
    }

    writeUByte(value: number): this {
        this.ensureCapacity(1);
        this.data.setUint8(this._position, value);
        this._position += 1;
        return this;
    }

    writeShort(value: number): this {
        this.ensureCapacity(2);
        this.data.setInt16(this._position, value, false);
        this._position += 2;
        return this;
    }

    writeUShort(value: number): this {
        this.ensureCapacity(2);
        this.data.setUint16(this._position, value, false);
        this._position += 2;
        return this;
    }

    writeShortLE(value: number): this {
        this.ensureCapacity(2);
        this.data.setInt16(this._position, value, true);
        this._position += 2;
        return this;
    }

    writeInt24(value: number): this {
        this.ensureCapacity(3);
        this.writeUByte((value >> 16) & 0xff);
        this.writeUByte((value >> 8) & 0xff);
        this.writeUByte(value & 0xff);
        return this;
    }

    writeInt(value: number): this {
        this.ensureCapacity(4);
        this.data.setInt32(this._position, value, false);
        this._position += 4;
        return this;
    }

    writeIntLE(value: number): this {
        this.ensureCapacity(4);
        this.data.setInt32(this._position, value, true);
        this._position += 4;
        return this;
    }

    writeLong(value: bigint): this {
        this.ensureCapacity(8);
        const high = Number((value >> 32n) & 0xffffffffn);
        const low = Number(value & 0xffffffffn);
        this.data.setUint32(this._position, high, false);
        this.data.setUint32(this._position + 4, low, false);
        this._position += 8;
        return this;
    }

    writeString(value: string): this {
        for (let i = 0; i < value.length; i++) {
            this.writeUByte(value.charCodeAt(i));
        }
        this.writeUByte(0); // Null terminator
        return this;
    }

    writeStringJagex(value: string): this {
        this.writeUByte(0); // Jagex string marker
        return this.writeString(value);
    }

    writeBytes(bytes: Uint8Array | number[]): this {
        this.ensureCapacity(bytes.length);
        for (let i = 0; i < bytes.length; i++) {
            this.data.setUint8(this._position + i, bytes[i]);
        }
        this._position += bytes.length;
        return this;
    }

    writeSmart(value: number): this {
        if (value < 128) {
            this.writeUByte(value);
        } else {
            this.writeUShort(value + 32768);
        }
        return this;
    }

    // ============ Special RS Writing Methods ============

    writeByteA(value: number): this {
        return this.writeUByte((value + 128) & 0xff);
    }

    writeByteC(value: number): this {
        return this.writeByte(-value);
    }

    writeByteS(value: number): this {
        return this.writeUByte((128 - value) & 0xff);
    }

    writeShortA(value: number): this {
        this.writeUByte((value >> 8) & 0xff);
        this.writeUByte((value + 128) & 0xff);
        return this;
    }

    writeShortLEA(value: number): this {
        this.writeUByte((value + 128) & 0xff);
        this.writeUByte((value >> 8) & 0xff);
        return this;
    }

    writeIntV1(value: number): this {
        this.writeUByte((value >> 8) & 0xff);
        this.writeUByte(value & 0xff);
        this.writeUByte((value >> 24) & 0xff);
        this.writeUByte((value >> 16) & 0xff);
        return this;
    }

    writeIntV2(value: number): this {
        this.writeUByte((value >> 16) & 0xff);
        this.writeUByte((value >> 24) & 0xff);
        this.writeUByte(value & 0xff);
        this.writeUByte((value >> 8) & 0xff);
        return this;
    }

    // ============ Bit Access Methods ============

    startBitAccess(): this {
        this._bitPosition = this._position * 8;
        this._inBitMode = true;
        return this;
    }

    endBitAccess(): this {
        this._position = Math.ceil(this._bitPosition / 8);
        this._inBitMode = false;
        return this;
    }

    readBits(count: number): number {
        if (!this._inBitMode) {
            throw new Error('Not in bit access mode');
        }

        let bytePos = this._bitPosition >> 3;
        let bitOffset = 8 - (this._bitPosition & 7);
        let value = 0;

        this._bitPosition += count;

        for (; count > bitOffset; bitOffset = 8) {
            value += (this.data.getUint8(bytePos++) & ((1 << bitOffset) - 1)) << (count - bitOffset);
            count -= bitOffset;
        }

        if (count === bitOffset) {
            value += this.data.getUint8(bytePos) & ((1 << bitOffset) - 1);
        } else {
            value += (this.data.getUint8(bytePos) >> (bitOffset - count)) & ((1 << count) - 1);
        }

        return value;
    }

    writeBits(count: number, value: number): this {
        if (!this._inBitMode) {
            throw new Error('Not in bit access mode');
        }

        let bytePos = this._bitPosition >> 3;
        let bitOffset = 8 - (this._bitPosition & 7);

        this._bitPosition += count;

        // Ensure we have enough capacity
        const neededBytes = Math.ceil(this._bitPosition / 8);
        this.ensureCapacity(neededBytes - this._position);

        for (; count > bitOffset; bitOffset = 8) {
            let current = this.data.getUint8(bytePos);
            current &= ~((1 << bitOffset) - 1);
            current |= (value >> (count - bitOffset)) & ((1 << bitOffset) - 1);
            this.data.setUint8(bytePos++, current);
            count -= bitOffset;
        }

        let current = this.data.getUint8(bytePos);
        if (count === bitOffset) {
            current &= ~((1 << bitOffset) - 1);
            current |= value & ((1 << bitOffset) - 1);
        } else {
            current &= ~(((1 << count) - 1) << (bitOffset - count));
            current |= (value & ((1 << count) - 1)) << (bitOffset - count);
        }
        this.data.setUint8(bytePos, current);

        return this;
    }

    // ============ Utility Methods ============

    toUint8Array(): Uint8Array {
        return new Uint8Array(this.buffer, 0, this._position);
    }

    toArrayBuffer(): ArrayBuffer {
        return this.buffer.slice(0, this._position);
    }

    slice(start?: number, end?: number): ByteBuffer {
        const s = start ?? 0;
        const e = end ?? this._position;
        return new ByteBuffer(new Uint8Array(this.buffer.slice(s, e)));
    }

    reset(): this {
        this._position = 0;
        this._bitPosition = 0;
        this._inBitMode = false;
        return this;
    }

    skip(bytes: number): this {
        this._position += bytes;
        return this;
    }

    private ensureCapacity(additionalBytes: number): void {
        const required = this._position + additionalBytes;
        if (required > this.buffer.byteLength) {
            // Grow buffer
            const newSize = Math.max(required, this.buffer.byteLength * 2);
            const newBuffer = new ArrayBuffer(newSize);
            new Uint8Array(newBuffer).set(new Uint8Array(this.buffer));
            this.buffer = newBuffer;
            this.data = new DataView(this.buffer);
        }
    }

    // ============ Static Factory Methods ============

    static allocate(size: number): ByteBuffer {
        return new ByteBuffer(size);
    }

    static wrap(data: Uint8Array | ArrayBuffer): ByteBuffer {
        return new ByteBuffer(data);
    }

    static fromHex(hex: string): ByteBuffer {
        const cleanHex = hex.replace(/\s/g, '');
        const bytes = new Uint8Array(cleanHex.length / 2);
        for (let i = 0; i < bytes.length; i++) {
            bytes[i] = parseInt(cleanHex.substr(i * 2, 2), 16);
        }
        return new ByteBuffer(bytes);
    }

    toHex(): string {
        const bytes = this.toUint8Array();
        return Array.from(bytes)
            .map((b) => b.toString(16).padStart(2, '0'))
            .join(' ');
    }
}

export default ByteBuffer;
