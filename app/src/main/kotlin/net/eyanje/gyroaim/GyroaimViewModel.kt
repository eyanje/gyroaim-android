package net.eyanje.gyroaim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import android.view.MotionEvent
import java.io.Closeable
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import net.eyanje.gyroaim.Connection

class GyroaimViewModel : Closeable, ViewModel() {
    var connection = ReusableConnection()
    var _lastError = MutableLiveData<Throwable?>(null)
    var lastError: LiveData<Throwable?> = _lastError

    fun clearError() {
        _lastError.postValue(null)
    }

    private fun triggerError(e: Throwable) {
        connection.close()
        _lastError.postValue(e)
    }

    fun replaceConnection(newConnection: ReusableConnection) {
        connection.close()
        connection = newConnection
    }
    
    /**
     * Close any existing connections and connect to the given host and port.
     */
    fun connect(
        host: String,
        portString: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                connection.connect(host, portString)
            } catch (e: NumberFormatException) {
                triggerError(e)
            } catch (e: UnknownHostException) {
                triggerError(e)
            } catch (e: SecurityException) {
                triggerError(e)
            }
        }
    }

    fun disconnect() {
        connection.close()
    }

    fun sendGyroscopeEvent(
        x: Float,
        y: Float,
        z: Float,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                connection.sendGyroscopeEvent(x, y, z)
            } catch (e: java.io.IOException) {
                triggerError(e)
            }
        }   
    }

    fun sendButtonEvent(
        down: Boolean,
        index: Int = 0,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                connection.sendButtonEvent(down, index)
            } catch (e: java.io.IOException) {
                triggerError(e)
            }
        }
    }

    fun sendScrollEvent(
        dx: Float,
        dy: Float,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                connection.sendScrollEvent(dx, dy)
            } catch (e: java.io.IOException) {
                triggerError(e)
            }
        }
    }

    override fun close() {
        disconnect()
    }

    override fun onCleared() {
        disconnect()
    }
}
