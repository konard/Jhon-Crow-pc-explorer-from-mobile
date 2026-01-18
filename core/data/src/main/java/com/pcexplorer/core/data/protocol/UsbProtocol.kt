package com.pcexplorer.core.data.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB communication protocol for PC Explorer.
 *
 * Packet structure:
 * - Magic bytes (4): "PCEX"
 * - Command (1): Command type
 * - Flags (1): Optional flags
 * - Payload length (4): Length of payload in bytes
 * - Payload (variable): Command-specific data
 * - Checksum (4): CRC32 of entire packet
 */
object UsbProtocol {
    const val MAGIC = "PCEX"
    const val HEADER_SIZE = 14 // Magic(4) + Command(1) + Flags(1) + Length(4) + Checksum(4)
    const val MAX_PACKET_SIZE = 64 * 1024 // 64KB max packet
    const val DEFAULT_BUFFER_SIZE = 4096
    const val TIMEOUT_MS = 5000

    // Command types
    object Commands {
        const val HANDSHAKE: Byte = 0x01
        const val LIST_DIR: Byte = 0x02
        const val GET_FILE_INFO: Byte = 0x03
        const val READ_FILE: Byte = 0x04
        const val WRITE_FILE: Byte = 0x05
        const val CREATE_DIR: Byte = 0x06
        const val DELETE: Byte = 0x07
        const val RENAME: Byte = 0x08
        const val SEARCH: Byte = 0x09
        const val GET_DRIVES: Byte = 0x0A
        const val GET_STORAGE_INFO: Byte = 0x0B
        const val DISCONNECT: Byte = 0xFF.toByte()

        // Response commands (server -> client)
        const val RESPONSE_OK: Byte = 0x80.toByte()
        const val RESPONSE_ERROR: Byte = 0x81.toByte()
        const val RESPONSE_DATA: Byte = 0x82.toByte()
        const val RESPONSE_FILE_CHUNK: Byte = 0x83.toByte()
        const val RESPONSE_END: Byte = 0x84.toByte()
    }

    // Flags
    object Flags {
        const val NONE: Byte = 0x00
        const val COMPRESSED: Byte = 0x01
        const val ENCRYPTED: Byte = 0x02
        const val CONTINUATION: Byte = 0x04
        const val FINAL: Byte = 0x08
    }

    // Error codes
    object Errors {
        const val SUCCESS = 0
        const val UNKNOWN_COMMAND = 1
        const val INVALID_PATH = 2
        const val FILE_NOT_FOUND = 3
        const val PERMISSION_DENIED = 4
        const val ALREADY_EXISTS = 5
        const val NOT_EMPTY = 6
        const val NO_SPACE = 7
        const val IO_ERROR = 8
        const val TIMEOUT = 9
        const val PROTOCOL_ERROR = 10
    }
}

/**
 * Represents a protocol packet.
 */
data class UsbPacket(
    val command: Byte,
    val flags: Byte = UsbProtocol.Flags.NONE,
    val payload: ByteArray = ByteArray(0)
) {
    /**
     * Serialize packet to bytes.
     */
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(UsbProtocol.HEADER_SIZE + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)

        // Magic
        buffer.put(UsbProtocol.MAGIC.toByteArray())
        // Command
        buffer.put(command)
        // Flags
        buffer.put(flags)
        // Payload length
        buffer.putInt(payload.size)
        // Payload
        buffer.put(payload)
        // Checksum (CRC32 of everything before checksum)
        val dataForChecksum = buffer.array().copyOfRange(0, buffer.position())
        buffer.putInt(calculateChecksum(dataForChecksum))

        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UsbPacket
        return command == other.command &&
                flags == other.flags &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = command.toInt()
        result = 31 * result + flags.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        /**
         * Parse packet from bytes.
         */
        fun fromBytes(data: ByteArray): Result<UsbPacket> {
            if (data.size < UsbProtocol.HEADER_SIZE) {
                return Result.failure(ProtocolException("Packet too small"))
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Verify magic
            val magic = ByteArray(4)
            buffer.get(magic)
            if (String(magic) != UsbProtocol.MAGIC) {
                return Result.failure(ProtocolException("Invalid magic bytes"))
            }

            val command = buffer.get()
            val flags = buffer.get()
            val payloadLength = buffer.getInt()

            if (data.size < UsbProtocol.HEADER_SIZE + payloadLength) {
                return Result.failure(ProtocolException("Incomplete payload"))
            }

            val payload = ByteArray(payloadLength)
            buffer.get(payload)

            // Verify checksum
            val receivedChecksum = buffer.getInt()
            val dataForChecksum = data.copyOfRange(0, UsbProtocol.HEADER_SIZE - 4 + payloadLength)
            val calculatedChecksum = calculateChecksum(dataForChecksum)

            if (receivedChecksum != calculatedChecksum) {
                return Result.failure(ProtocolException("Checksum mismatch"))
            }

            return Result.success(UsbPacket(command, flags, payload))
        }

        private fun calculateChecksum(data: ByteArray): Int {
            var crc = 0xFFFFFFFF.toInt()
            for (byte in data) {
                crc = crc xor byte.toInt()
                for (i in 0 until 8) {
                    crc = if (crc and 1 != 0) {
                        (crc ushr 1) xor 0xEDB88320.toInt()
                    } else {
                        crc ushr 1
                    }
                }
            }
            return crc.inv()
        }
    }
}

class ProtocolException(message: String) : Exception(message)
