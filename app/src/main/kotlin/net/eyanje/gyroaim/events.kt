package net.eyanje.gyroaim

import java.net.DatagramPacket
import java.net.DatagramSocket

object EventCodes {
    val EV_SYN: UShort = 0x00.toUShort()
    val EV_KEY: UShort = 0x01.toUShort()
    val EV_REL: UShort = 0x02.toUShort()
    val EV_ABS: UShort = 0x0.toUShort()

    val BTN_0: UShort = 0x100.toUShort()

    val REL_X: UShort = 0x00.toUShort()
    val REL_Y: UShort = 0x01.toUShort()
    val REL_Z: UShort = 0x02.toUShort()
    val REL_RX: UShort = 0x03.toUShort()
    val REL_RY: UShort = 0x04.toUShort()
    val REL_RZ: UShort = 0x05.toUShort()

    val ABS_MT_SLOT: UShort = 0x2f.toUShort()
    val ABS_MT_TOUCH_MAJOR: UShort = 0x30.toUShort()
    val ABS_MT_TOUCH_MINOR: UShort = 0x31.toUShort()
    val ABS_MT_WIDTH_MAJOR: UShort = 0x32.toUShort()
    val ABS_MT_WIDTH_MINOR: UShort = 0x33.toUShort()
    val ABS_MT_ORIENTATION: UShort = 0x34.toUShort()
    val ABS_MT_POSITION_X: UShort = 0x35.toUShort()
    val ABS_MT_POSITION_Y: UShort = 0x36.toUShort()
    val ABS_MT_TOOL_TYPE: UShort = 0x37.toUShort()
    val ABS_MT_BLOB_ID: UShort = 0x38.toUShort()
    val ABS_MT_TRACKING_ID: UShort = 0x39.toUShort()
    val ABS_MT_PRESSURE: UShort = 0x3a.toUShort()
    val ABS_MT_DISTANCE: UShort = 0x3b.toUShort()
    val ABS_MT_TOOL_X: UShort = 0x3c.toUShort()
    val ABS_MT_TOOL_Y: UShort = 0x3d.toUShort()
}


class Event(type: UShort, code: UShort, value: Int) {
    val type: UShort = type
    val code: UShort = code
    val value: Int = value

    fun toByteArray() = byteArrayOf(
        ((type.toInt() ushr 0) and 0xFF).toByte(),
        ((type.toInt() ushr 8) and 0xFF).toByte(),
        ((code.toInt() ushr 0) and 0xFF).toByte(),
        ((code.toInt() ushr 8) and 0xFF).toByte(),
        ((value ushr 0) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
    )
}

// Functions to send events

fun sendEvent(socket: DatagramSocket, event: Event) {
    val bytes = event.toByteArray()
    socket.send(DatagramPacket(bytes, bytes.size))
}

fun sendAll(socket: DatagramSocket, events: Sequence<Event>) {
    for (event in events) {
        sendEvent(socket, event)
    }
}

/**
 * Send all events in the sequence, then SYN
 */
fun sendAllWithSyn(socket: DatagramSocket, events: Sequence<Event>) {
    sendAll(socket, events)
    sendEvent(socket, Event(EventCodes.EV_SYN, 0.toUShort(), 0))
}

