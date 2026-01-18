package com.pcexplorer.core.data.protocol

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbPacketTest {

    // Constructor tests

    @Test
    fun `UsbPacket constructor sets command`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)

        assertEquals(UsbProtocol.Commands.HANDSHAKE, packet.command)
    }

    @Test
    fun `UsbPacket default flags is NONE`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.LIST_DIR)

        assertEquals(UsbProtocol.Flags.NONE, packet.flags)
    }

    @Test
    fun `UsbPacket default payload is empty`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.GET_DRIVES)

        assertTrue(packet.payload.isEmpty())
    }

    @Test
    fun `UsbPacket constructor with all parameters`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val packet = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            flags = UsbProtocol.Flags.COMPRESSED,
            payload = payload
        )

        assertEquals(UsbProtocol.Commands.WRITE_FILE, packet.command)
        assertEquals(UsbProtocol.Flags.COMPRESSED, packet.flags)
        assertArrayEquals(payload, packet.payload)
    }

    // toBytes tests

    @Test
    fun `toBytes creates packet with magic bytes`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)

        val bytes = packet.toBytes()

        val magic = String(bytes.copyOfRange(0, 4))
        assertEquals("PCEX", magic)
    }

    @Test
    fun `toBytes includes command byte`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.LIST_DIR)

        val bytes = packet.toBytes()

        assertEquals(UsbProtocol.Commands.LIST_DIR, bytes[4])
    }

    @Test
    fun `toBytes includes flags byte`() {
        val packet = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            flags = UsbProtocol.Flags.FINAL
        )

        val bytes = packet.toBytes()

        assertEquals(UsbProtocol.Flags.FINAL, bytes[5])
    }

    @Test
    fun `toBytes includes payload length`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val packet = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            payload = payload
        )

        val bytes = packet.toBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(6) // Skip magic(4) + command(1) + flags(1)

        assertEquals(5, buffer.getInt())
    }

    @Test
    fun `toBytes includes payload`() {
        val payload = byteArrayOf(10, 20, 30)
        val packet = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            payload = payload
        )

        val bytes = packet.toBytes()

        // Payload starts at position 10 (after header minus checksum)
        assertEquals(10.toByte(), bytes[10])
        assertEquals(20.toByte(), bytes[11])
        assertEquals(30.toByte(), bytes[12])
    }

    @Test
    fun `toBytes creates packet of correct size with empty payload`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)

        val bytes = packet.toBytes()

        assertEquals(UsbProtocol.HEADER_SIZE, bytes.size)
    }

    @Test
    fun `toBytes creates packet of correct size with payload`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val packet = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            payload = payload
        )

        val bytes = packet.toBytes()

        assertEquals(UsbProtocol.HEADER_SIZE + payload.size, bytes.size)
    }

    // fromBytes tests

    @Test
    fun `fromBytes returns failure for packet too small`() {
        val data = ByteArray(5) // Less than HEADER_SIZE

        val result = UsbPacket.fromBytes(data)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ProtocolException)
        assertTrue(result.exceptionOrNull()?.message?.contains("too small") == true)
    }

    @Test
    fun `fromBytes returns failure for invalid magic`() {
        val data = ByteArray(UsbProtocol.HEADER_SIZE)
        data[0] = 'X'.code.toByte()
        data[1] = 'Y'.code.toByte()
        data[2] = 'Z'.code.toByte()
        data[3] = 'W'.code.toByte()

        val result = UsbPacket.fromBytes(data)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid magic") == true)
    }

    @Test
    fun `fromBytes returns failure for incomplete payload`() {
        // Create a packet claiming 100 bytes of payload but only provide header
        val buffer = ByteBuffer.allocate(UsbProtocol.HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("PCEX".toByteArray())
        buffer.put(UsbProtocol.Commands.HANDSHAKE)
        buffer.put(UsbProtocol.Flags.NONE)
        buffer.putInt(100) // Claims 100 bytes of payload
        buffer.putInt(0) // Dummy checksum

        val result = UsbPacket.fromBytes(buffer.array())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Incomplete payload") == true)
    }

    @Test
    fun `toBytes and fromBytes are reversible`() {
        val originalPacket = UsbPacket(
            command = UsbProtocol.Commands.LIST_DIR,
            flags = UsbProtocol.Flags.COMPRESSED,
            payload = byteArrayOf(1, 2, 3, 4, 5)
        )

        val bytes = originalPacket.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        val parsedPacket = result.getOrNull()!!
        assertEquals(originalPacket.command, parsedPacket.command)
        assertEquals(originalPacket.flags, parsedPacket.flags)
        assertArrayEquals(originalPacket.payload, parsedPacket.payload)
    }

    @Test
    fun `fromBytes verifies checksum`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)
        val bytes = packet.toBytes()

        // Corrupt a byte
        bytes[5] = 0xFF.toByte()

        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Checksum") == true)
    }

    @Test
    fun `round-trip with large payload`() {
        val payload = ByteArray(1000) { it.toByte() }
        val originalPacket = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            flags = UsbProtocol.Flags.CONTINUATION,
            payload = payload
        )

        val bytes = originalPacket.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        assertArrayEquals(payload, result.getOrNull()?.payload)
    }

    @Test
    fun `round-trip with empty payload`() {
        val originalPacket = UsbPacket(
            command = UsbProtocol.Commands.DISCONNECT
        )

        val bytes = originalPacket.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.payload?.isEmpty() == true)
    }

    // equals tests

    @Test
    fun `equals returns true for same packet`() {
        val packet = UsbPacket(
            command = UsbProtocol.Commands.HANDSHAKE,
            flags = UsbProtocol.Flags.NONE,
            payload = byteArrayOf(1, 2, 3)
        )

        assertTrue(packet == packet)
    }

    @Test
    fun `equals returns true for identical packets`() {
        val packet1 = UsbPacket(
            command = UsbProtocol.Commands.LIST_DIR,
            flags = UsbProtocol.Flags.COMPRESSED,
            payload = byteArrayOf(1, 2, 3)
        )
        val packet2 = UsbPacket(
            command = UsbProtocol.Commands.LIST_DIR,
            flags = UsbProtocol.Flags.COMPRESSED,
            payload = byteArrayOf(1, 2, 3)
        )

        assertEquals(packet1, packet2)
    }

    @Test
    fun `equals returns false for different commands`() {
        val packet1 = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)
        val packet2 = UsbPacket(command = UsbProtocol.Commands.DISCONNECT)

        assertNotEquals(packet1, packet2)
    }

    @Test
    fun `equals returns false for different flags`() {
        val packet1 = UsbPacket(
            command = UsbProtocol.Commands.HANDSHAKE,
            flags = UsbProtocol.Flags.NONE
        )
        val packet2 = UsbPacket(
            command = UsbProtocol.Commands.HANDSHAKE,
            flags = UsbProtocol.Flags.COMPRESSED
        )

        assertNotEquals(packet1, packet2)
    }

    @Test
    fun `equals returns false for different payloads`() {
        val packet1 = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            payload = byteArrayOf(1, 2, 3)
        )
        val packet2 = UsbPacket(
            command = UsbProtocol.Commands.WRITE_FILE,
            payload = byteArrayOf(4, 5, 6)
        )

        assertNotEquals(packet1, packet2)
    }

    @Test
    fun `equals returns false for null`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)

        assertFalse(packet.equals(null))
    }

    @Test
    fun `equals returns false for different type`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)

        assertFalse(packet.equals("string"))
    }

    // hashCode tests

    @Test
    fun `hashCode is consistent for same packet`() {
        val packet = UsbPacket(
            command = UsbProtocol.Commands.LIST_DIR,
            payload = byteArrayOf(1, 2, 3)
        )

        assertEquals(packet.hashCode(), packet.hashCode())
    }

    @Test
    fun `hashCode is same for equal packets`() {
        val packet1 = UsbPacket(
            command = UsbProtocol.Commands.LIST_DIR,
            flags = UsbProtocol.Flags.FINAL,
            payload = byteArrayOf(1, 2, 3)
        )
        val packet2 = UsbPacket(
            command = UsbProtocol.Commands.LIST_DIR,
            flags = UsbProtocol.Flags.FINAL,
            payload = byteArrayOf(1, 2, 3)
        )

        assertEquals(packet1.hashCode(), packet2.hashCode())
    }

    // ProtocolException tests

    @Test
    fun `ProtocolException contains message`() {
        val exception = ProtocolException("Test error")

        assertEquals("Test error", exception.message)
    }

    @Test
    fun `ProtocolException is an Exception`() {
        val exception = ProtocolException("Error")

        assertTrue(exception is Exception)
    }

    // Various command packets

    @Test
    fun `can create and serialize handshake packet`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.HANDSHAKE)
        val bytes = packet.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        assertEquals(UsbProtocol.Commands.HANDSHAKE, result.getOrNull()?.command)
    }

    @Test
    fun `can create and serialize list dir packet with path payload`() {
        val pathPayload = "/documents".toByteArray()
        val packet = UsbPacket(
            command = UsbProtocol.Commands.LIST_DIR,
            payload = pathPayload
        )
        val bytes = packet.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        assertEquals(UsbProtocol.Commands.LIST_DIR, result.getOrNull()?.command)
        assertArrayEquals(pathPayload, result.getOrNull()?.payload)
    }

    @Test
    fun `can create response OK packet`() {
        val packet = UsbPacket(command = UsbProtocol.Commands.RESPONSE_OK)
        val bytes = packet.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        assertEquals(UsbProtocol.Commands.RESPONSE_OK, result.getOrNull()?.command)
    }

    @Test
    fun `can create response error packet with error payload`() {
        val errorPayload = byteArrayOf(0x03, 0x00, 0x00, 0x00) // Error code 3
        val packet = UsbPacket(
            command = UsbProtocol.Commands.RESPONSE_ERROR,
            payload = errorPayload
        )
        val bytes = packet.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        assertEquals(UsbProtocol.Commands.RESPONSE_ERROR, result.getOrNull()?.command)
    }

    @Test
    fun `can create file chunk packet with continuation flag`() {
        val chunkData = ByteArray(4096) { it.toByte() }
        val packet = UsbPacket(
            command = UsbProtocol.Commands.RESPONSE_FILE_CHUNK,
            flags = UsbProtocol.Flags.CONTINUATION,
            payload = chunkData
        )
        val bytes = packet.toBytes()
        val result = UsbPacket.fromBytes(bytes)

        assertTrue(result.isSuccess)
        assertEquals(UsbProtocol.Commands.RESPONSE_FILE_CHUNK, result.getOrNull()?.command)
        assertEquals(UsbProtocol.Flags.CONTINUATION, result.getOrNull()?.flags)
        assertArrayEquals(chunkData, result.getOrNull()?.payload)
    }
}
