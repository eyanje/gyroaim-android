package net.eyanje.gyroaim

import android.view.MotionEvent
import java.io.Closeable
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.math.roundToInt

import net.eyanje.gyroaim.Event
import net.eyanje.gyroaim.EventCodes
import net.eyanje.gyroaim.sendAllWithSyn

// Function to convert a Float to Int
// For gyroscope use
fun floatToInt(value: Float) : Int {
    return (value * 1000).roundToInt()
}

// Functions to generate events

/**
 * Returns a sequence of events representing a single gyroscope movement
 */
fun gyroscopeEvents(x: Float, y: Float, z: Float) : Sequence<Event> {
    // Convert event values to int
    val intValues = sequenceOf(x, y, z)
        .map({ floatToInt(it) })
        .toList()

    return sequenceOf(
        Event(EventCodes.EV_REL, EventCodes.REL_X, intValues[0]),
        Event(EventCodes.EV_REL, EventCodes.REL_Y, intValues[1]),
        Event(EventCodes.EV_REL, EventCodes.REL_Z, intValues[2]),
    )
}

/**
 * Returns a sequence of events representing a single button event.
 */
fun buttonEvents(down: Boolean, index: Int = 0) : Sequence<Event> {
    val value = if (down) 1 else 0
    return sequenceOf(Event(
        EventCodes.EV_KEY,
        (EventCodes.BTN_0 + index.toUShort()).toUShort(),
        value
    ))
}

/**
 * Returns a sequence of events representing a single scroll event.
 */
fun scrollEvents(dx: Float, dy: Float) : Sequence<Event> {
    val intValues = sequenceOf(dx, dy)
        .map({ it.roundToInt() })
        .toList()

    return sequenceOf(
        Event(EventCodes.EV_REL, EventCodes.REL_RX, intValues[0]),
        Event(EventCodes.EV_REL, EventCodes.REL_RY, intValues[1]),
    )
}

/**
 * Create type B multitouch events.
 *
 * https://www.kernel.org/doc/html/v4.19/input/multi-touch-protocol.html
 */
class MultitouchEventGenerator {
    /**
     * Slots[i] is an id, if one is registered, or null, if the touch has
     * ended.
     */
    var slots: MutableList<Int?> = mutableListOf()

    fun ensureSize(size: Int) {
        while (slots.size < size) {
            slots.add(null)
        }
    }

    /**
     * Add the ID into the first empty slot, creating a new slot if none exists.
     */
    fun newSlot(id: Int, minId: Int = 0) : Int {
        ensureSize(minId + 1)
        // Find the next slot without an ID.
        for (i in minId..slots.size) {
            if (slots[i] == null) {
                // Write the ID into the slot
                slots[i] = id;
                // Return the slot number
                return i
            }
        }
        // Create a new slot and add the ID to the end
        slots.add(id)
        return slots.size - 1
    }

    /**
     * Returns the slot with the given ID. If no slot has the given ID, create a
     * new slot and return it.
     */
    fun getSlot(id: Int, minId: Int = 0) : Int {
        ensureSize(minId + 1)

        for (i in minId..slots.size) {
            if (slots[i] == id) {
                return i;
            }
        }
        // If no slot is found, make a new slot
        return newSlot(id, minId = minId)
    }

    /**
     * Clears all slots with the given ID.
     */
    fun clearSlot(id: Int) {
        for (i in 0..slots.size) {
            if (slots[i] == id) {
                slots[i] = null
            }
        }
    }

    fun multitouchEvents(event: MotionEvent) = sequence {
        // Only report the action index
        
        val index = event.getActionIndex()
        val id = event.getPointerId(index);

        // Compute the slot for this event
        // For ACTION_POINTER_DOWN, the pointer is secondary, so we don't allow
        // it to occupy slot 0.
        val slot = when (event.getActionMasked()) {
            MotionEvent.ACTION_POINTER_DOWN -> getSlot(id, minId = 1)
            else -> getSlot(id)
        }

        // Set the slot of proceeeding events
        yield(Event(EventCodes.EV_ABS, EventCodes.ABS_MT_SLOT, slot))

        // For down events, register a new ID
        when (event.getActionMasked()) {
            MotionEvent.ACTION_DOWN -> {
                yield(Event(EventCodes.EV_ABS, EventCodes.ABS_MT_TRACKING_ID, id))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                yield(Event(EventCodes.EV_ABS, EventCodes.ABS_MT_TRACKING_ID, id))
            }
        }

        // Provide size, orientation, location, and pressure.
        // Tool type is not supported well, so we don't report it.
        yield(Event(
            EventCodes.EV_ABS,
            EventCodes.ABS_MT_TOUCH_MAJOR,
            event.getTouchMajor(index).roundToInt()))
        yield(Event(
            EventCodes.EV_ABS,
            EventCodes.ABS_MT_TOUCH_MINOR,
            event.getTouchMinor(index).roundToInt()))
        yield(Event(
            EventCodes.EV_ABS,
            EventCodes.ABS_MT_ORIENTATION,
            event.getOrientation(index).roundToInt()))
        yield(Event(
            EventCodes.EV_ABS,
            EventCodes.ABS_MT_POSITION_X,
            event.getX(index).roundToInt()))
        yield(Event(
            EventCodes.EV_ABS,
            EventCodes.ABS_MT_POSITION_Y,
            event.getY(index).roundToInt()))
        yield(Event(
            EventCodes.EV_ABS,
            EventCodes.ABS_MT_PRESSURE,
            event.getPressure(index).roundToInt()))

        // Provide up events and cancellations last, 
        when (event.getActionMasked()) {
            MotionEvent.ACTION_POINTER_UP -> {
                yield(Event(EventCodes.EV_ABS, EventCodes.ABS_MT_TRACKING_ID, -1))
            }
            MotionEvent.ACTION_UP -> {
                yield(Event(EventCodes.EV_ABS, EventCodes.ABS_MT_TRACKING_ID, -1))
            }
            MotionEvent.ACTION_CANCEL -> {
                // Cancel everything

                // Delete IDs for every slot
                for (i in 0..slots.size) {
                    if (slots[i] != null) {
                        yield(Event(EventCodes.EV_ABS, EventCodes.ABS_MT_SLOT, i))
                        yield(Event(EventCodes.EV_ABS, EventCodes.ABS_MT_TRACKING_ID, -1))
                    }
                }

                // Remove all slots
                slots.clear()
            }
        }
    }
}


/**
 * High-level connection class that can send gyroscope, button, and multitouch
 * events.
 */
class Connection : Closeable {
    val socket = DatagramSocket()
    val mtGen = MultitouchEventGenerator()

    fun connect(host: String, portString: String) {
        // Run setup in a separate task to prevent the UI rendering from blocking.
        // Even if there's an error, or we haven't even set any settings, we want to 

        // Parse the port as an integer
        val port = try {
            portString.toInt()
        } catch (e: NumberFormatException) {
            throw e
        }

        // Get the IP address for the specified host
        val address = try {
            InetAddress.getByName(host)
        } catch (e: UnknownHostException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        }

        // Create a network socket.
        // Though the socket should survive recreation
        // Well, I guess it doesn't really have to survive, but it makes sense to
        // keep it alive.
        socket.connect(address, port) 
    }
    
    override fun close() {
        socket.close()
    }

    fun sendGyroscopeEvent(x: Float, y: Float, z: Float) {
        sendAllWithSyn(socket, gyroscopeEvents(x, y, z))
    }

    fun sendButtonEvent(down: Boolean, index: Int = 0) {
        sendAllWithSyn(socket, buttonEvents(down, index))
    }

    fun sendMultitouchEvent(event: MotionEvent) {
        sendAllWithSyn(socket, mtGen.multitouchEvents(event))
    }

    fun sendScrollEvent(dx: Float, dy: Float) {
        sendAllWithSyn(socket, scrollEvents(dx, dy))
    }
}


class ReusableConnection : Closeable {
    var connection: Connection? = null

    fun connect(host: String, portString: String) {
        val newConnection = Connection()
        newConnection.connect(host, portString)
        connection = newConnection
    }
    
    override fun close() {
        val oldConnection = connection
        connection = null
        oldConnection?.close()
    }

    fun isConnected() : Boolean = connection != null

    fun sendGyroscopeEvent(x: Float, y: Float, z: Float) {
        connection?.sendGyroscopeEvent(x, y, z)
    }

    fun sendButtonEvent(down: Boolean, index: Int = 0) {
        connection?.sendButtonEvent(down, index)
    }

    fun sendMultitouchEvent(event: MotionEvent) {
        connection?.sendMultitouchEvent(event)
    }

    fun sendScrollEvent(dx: Float, dy: Float) {
        connection?.sendScrollEvent(dx, dy)
    }
}
