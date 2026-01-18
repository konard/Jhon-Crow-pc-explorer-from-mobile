package com.pcexplorer.core.data.protocol

import org.junit.Assert.*
import org.junit.Test

class UsbProtocolTest {

    // Constants tests

    @Test
    fun `MAGIC is PCEX`() {
        assertEquals("PCEX", UsbProtocol.MAGIC)
    }

    @Test
    fun `HEADER_SIZE is correct`() {
        // Magic(4) + Command(1) + Flags(1) + Length(4) + Checksum(4) = 14
        assertEquals(14, UsbProtocol.HEADER_SIZE)
    }

    @Test
    fun `MAX_PACKET_SIZE is 64KB`() {
        assertEquals(64 * 1024, UsbProtocol.MAX_PACKET_SIZE)
    }

    @Test
    fun `DEFAULT_BUFFER_SIZE is 4096`() {
        assertEquals(4096, UsbProtocol.DEFAULT_BUFFER_SIZE)
    }

    @Test
    fun `TIMEOUT_MS is 5000`() {
        assertEquals(5000, UsbProtocol.TIMEOUT_MS)
    }

    // Commands tests

    @Test
    fun `Commands HANDSHAKE is 0x01`() {
        assertEquals(0x01.toByte(), UsbProtocol.Commands.HANDSHAKE)
    }

    @Test
    fun `Commands LIST_DIR is 0x02`() {
        assertEquals(0x02.toByte(), UsbProtocol.Commands.LIST_DIR)
    }

    @Test
    fun `Commands GET_FILE_INFO is 0x03`() {
        assertEquals(0x03.toByte(), UsbProtocol.Commands.GET_FILE_INFO)
    }

    @Test
    fun `Commands READ_FILE is 0x04`() {
        assertEquals(0x04.toByte(), UsbProtocol.Commands.READ_FILE)
    }

    @Test
    fun `Commands WRITE_FILE is 0x05`() {
        assertEquals(0x05.toByte(), UsbProtocol.Commands.WRITE_FILE)
    }

    @Test
    fun `Commands CREATE_DIR is 0x06`() {
        assertEquals(0x06.toByte(), UsbProtocol.Commands.CREATE_DIR)
    }

    @Test
    fun `Commands DELETE is 0x07`() {
        assertEquals(0x07.toByte(), UsbProtocol.Commands.DELETE)
    }

    @Test
    fun `Commands RENAME is 0x08`() {
        assertEquals(0x08.toByte(), UsbProtocol.Commands.RENAME)
    }

    @Test
    fun `Commands SEARCH is 0x09`() {
        assertEquals(0x09.toByte(), UsbProtocol.Commands.SEARCH)
    }

    @Test
    fun `Commands GET_DRIVES is 0x0A`() {
        assertEquals(0x0A.toByte(), UsbProtocol.Commands.GET_DRIVES)
    }

    @Test
    fun `Commands GET_STORAGE_INFO is 0x0B`() {
        assertEquals(0x0B.toByte(), UsbProtocol.Commands.GET_STORAGE_INFO)
    }

    @Test
    fun `Commands DISCONNECT is 0xFF`() {
        assertEquals(0xFF.toByte(), UsbProtocol.Commands.DISCONNECT)
    }

    @Test
    fun `Commands RESPONSE_OK is 0x80`() {
        assertEquals(0x80.toByte(), UsbProtocol.Commands.RESPONSE_OK)
    }

    @Test
    fun `Commands RESPONSE_ERROR is 0x81`() {
        assertEquals(0x81.toByte(), UsbProtocol.Commands.RESPONSE_ERROR)
    }

    @Test
    fun `Commands RESPONSE_DATA is 0x82`() {
        assertEquals(0x82.toByte(), UsbProtocol.Commands.RESPONSE_DATA)
    }

    @Test
    fun `Commands RESPONSE_FILE_CHUNK is 0x83`() {
        assertEquals(0x83.toByte(), UsbProtocol.Commands.RESPONSE_FILE_CHUNK)
    }

    @Test
    fun `Commands RESPONSE_END is 0x84`() {
        assertEquals(0x84.toByte(), UsbProtocol.Commands.RESPONSE_END)
    }

    // Flags tests

    @Test
    fun `Flags NONE is 0x00`() {
        assertEquals(0x00.toByte(), UsbProtocol.Flags.NONE)
    }

    @Test
    fun `Flags COMPRESSED is 0x01`() {
        assertEquals(0x01.toByte(), UsbProtocol.Flags.COMPRESSED)
    }

    @Test
    fun `Flags ENCRYPTED is 0x02`() {
        assertEquals(0x02.toByte(), UsbProtocol.Flags.ENCRYPTED)
    }

    @Test
    fun `Flags CONTINUATION is 0x04`() {
        assertEquals(0x04.toByte(), UsbProtocol.Flags.CONTINUATION)
    }

    @Test
    fun `Flags FINAL is 0x08`() {
        assertEquals(0x08.toByte(), UsbProtocol.Flags.FINAL)
    }

    @Test
    fun `Flags can be combined`() {
        val combined = (UsbProtocol.Flags.COMPRESSED.toInt() or UsbProtocol.Flags.FINAL.toInt()).toByte()
        assertEquals(0x09.toByte(), combined)
    }

    // Errors tests

    @Test
    fun `Errors SUCCESS is 0`() {
        assertEquals(0, UsbProtocol.Errors.SUCCESS)
    }

    @Test
    fun `Errors UNKNOWN_COMMAND is 1`() {
        assertEquals(1, UsbProtocol.Errors.UNKNOWN_COMMAND)
    }

    @Test
    fun `Errors INVALID_PATH is 2`() {
        assertEquals(2, UsbProtocol.Errors.INVALID_PATH)
    }

    @Test
    fun `Errors FILE_NOT_FOUND is 3`() {
        assertEquals(3, UsbProtocol.Errors.FILE_NOT_FOUND)
    }

    @Test
    fun `Errors PERMISSION_DENIED is 4`() {
        assertEquals(4, UsbProtocol.Errors.PERMISSION_DENIED)
    }

    @Test
    fun `Errors ALREADY_EXISTS is 5`() {
        assertEquals(5, UsbProtocol.Errors.ALREADY_EXISTS)
    }

    @Test
    fun `Errors NOT_EMPTY is 6`() {
        assertEquals(6, UsbProtocol.Errors.NOT_EMPTY)
    }

    @Test
    fun `Errors NO_SPACE is 7`() {
        assertEquals(7, UsbProtocol.Errors.NO_SPACE)
    }

    @Test
    fun `Errors IO_ERROR is 8`() {
        assertEquals(8, UsbProtocol.Errors.IO_ERROR)
    }

    @Test
    fun `Errors TIMEOUT is 9`() {
        assertEquals(9, UsbProtocol.Errors.TIMEOUT)
    }

    @Test
    fun `Errors PROTOCOL_ERROR is 10`() {
        assertEquals(10, UsbProtocol.Errors.PROTOCOL_ERROR)
    }
}
