package net.eyanje.gyroaim

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.IOException

import net.eyanje.gyroaim.GyroaimPanel
import net.eyanje.gyroaim.ReusableConnection

class MainActivity : ComponentActivity(), SensorEventListener {
    val sensorDelay = SensorManager.SENSOR_DELAY_GAME
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private val gyroaimViewModel: GyroaimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            GyroaimPanel()
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do nothing
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Propogate it through the connection
        gyroaimViewModel.sendGyroscopeEvent(
            event.values[0],
            event.values[1],
            event.values[2],
        )
    }

    override fun onResume() {
        super.onResume()
        gyroscope?.also { gyroscope ->
            sensorManager.registerListener(this, gyroscope, sensorDelay)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }


}

