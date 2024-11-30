package net.eyanje.gyroaim

import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.Draggable2DState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import net.eyanje.gyroaim.GyroaimViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GyroaimPanel(
    gyroaimViewModel: GyroaimViewModel = viewModel()
) {
    // Save the IP, port, and other settings through activity death and
    // recreation, but not through quitting.
    // Rotating the screen destroys an activity, so this is important.
    var host by rememberSaveable { mutableStateOf("") }
    var portString by rememberSaveable { mutableStateOf("") }
    var leftHanded by rememberSaveable { mutableStateOf(false) }
    var invertX by rememberSaveable { mutableStateOf(false) }
    var invertY by rememberSaveable { mutableStateOf(false) }

    var atSettingsScreen by rememberSaveable { mutableStateOf(true) }

    var errorMessage: String? by rememberSaveable { mutableStateOf(null) };

    val errorObserver = Observer<Throwable?> { error ->
        if (error != null) {
            when (error) {
                is NumberFormatException -> {
                    errorMessage = "Expected port number"
                }
                is UnknownHostException -> {
                    errorMessage = "Unknown host"
                }
                is SecurityException -> {
                    errorMessage = "Connection blocked by security policy"
                }
                is IOException -> {
                    errorMessage = "Connection error: ${error.getLocalizedMessage()}"
                }
            }
        }
    }

    // Observe the error
    gyroaimViewModel.lastError.observe(LocalLifecycleOwner.current, errorObserver)

    if (atSettingsScreen) {
        GyroaimSettingsPanel(
            host = host,
            portString = portString,

            onHostChange = { host = it },
            onPortChange = { portString = it },

            leftHandedChecked = leftHanded,
            invertXChecked = invertX,
            invertYChecked = invertY,

            onLeftHandedChange = { leftHanded = it },
            onInvertXChange = { invertX = it },
            onInvertYChange = { invertY = it },

            onConnect = {
                // Go to the input screen
                atSettingsScreen = false
                // Connect to endpoint
                gyroaimViewModel.connect(host, portString)
            },
        )
    } else {
        GyroaimInputPanel(
            leftHanded = leftHanded,

            onDisconnect = {
                atSettingsScreen = true
                gyroaimViewModel.disconnect()
            },

            onPrimaryButtonChange = { down ->
                gyroaimViewModel.sendButtonEvent(down, 0)
            },
            onSecondaryButtonChange = { down ->
                gyroaimViewModel.sendButtonEvent(down, 1)
            },
            onScroll = { offset ->
                // Possibly invert X and Y
                // Scroll offsets are naturally inverted, so we re-invert them.
                val x = if (invertX) offset.x else -offset.x
                val y = if (invertY) offset.y else -offset.y
                gyroaimViewModel.sendScrollEvent(x, y)
            },
        )
    }

    if (errorMessage != null) {
        val onCloseDialog = {
            // Return to the settings screen
            atSettingsScreen = true
            // Clear the error to prevent duplicates
            gyroaimViewModel.clearError()
            errorMessage = null
        }
        AlertDialog(
            onDismissRequest = { onCloseDialog() },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { onCloseDialog() },
                ) {
                    Text("Disconnect")
                }
            },
            title = { Text("Error") },
            text = { Text(errorMessage ?: "") },
        )
    }
}

@Composable
fun GyroaimSettingsPanel(
    host: String,
    portString: String,
    leftHandedChecked: Boolean,
    invertXChecked: Boolean,
    invertYChecked: Boolean,

    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onLeftHandedChange: (Boolean) -> Unit,
    onInvertXChange: (Boolean) -> Unit,
    onInvertYChange: (Boolean) -> Unit,

    onConnect: () -> Unit,
) {
    Scaffold { internalPadding ->
        Column(
            modifier = Modifier.padding(internalPadding)
        ) {
            Row {
                TextField(
                    value = host,
                    onValueChange = onHostChange,
                    modifier = Modifier.weight(3f),
                    label = { Text("Host Address") },
                    singleLine = true,
                )
                TextField(
                    value = portString,
                    onValueChange = onPortChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("UDP Port") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    singleLine = true,
                )
            }
            Button(
                onClick = { onConnect() },
                content = { Text("Connect") },
            )
            Text(
                text = "Other settings",
                fontSize = 18.sp,
            )
            Text("Left-handed")
            Switch(
                checked = leftHandedChecked,
                onCheckedChange = onLeftHandedChange,
            )
            Text("Invert X")
            Switch(
                checked = invertXChecked,
                onCheckedChange = onInvertXChange,
            )
            Text("Invert Y")
            Switch(
                checked = invertYChecked,
                onCheckedChange = onInvertYChange,
            )
        }
    }
}

@Composable
fun GyroaimInputPanel(
    leftHanded: Boolean,
    onDisconnect: () -> Unit,

    onPrimaryButtonChange: (Boolean) -> Unit,
    onSecondaryButtonChange: (Boolean) -> Unit,
    onScroll: (Offset) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val disconnectAlignment = if (leftHanded) Alignment.TopEnd else Alignment.TopStart
    val buttonAlignment = if (leftHanded) Alignment.CenterEnd else Alignment.CenterStart

    // Create reader and writer for scroll position
    var scrollPosition by remember { mutableStateOf(Offset.Zero) }
    val scrollObserver = Observer<Offset> {
        val scrollDistance = scrollPosition - it
        // Report scrolling
        onScroll(scrollDistance)
        // Save new position
        scrollPosition = it
    }

    // Create and subscribe to a scroll2DWrapper
    val scroll2DWrapper = remember { Scroll2DWrapper(scrollPosition) }
    scroll2DWrapper.position.observe(LocalLifecycleOwner.current, scrollObserver)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Touch area, at lowest height
        // We make this node a leaf to prevent receiving events from the
        // buttons.
        Box(
            modifier = scroll2DWrapper.apply(coroutineScope, Modifier.fillMaxSize())
        ) {
            Text(
                text = "$scrollPosition",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Disconnect button
        Button(
            onClick = onDisconnect,
            modifier = Modifier.align(disconnectAlignment),
            content = { Text("Disconnect") },
        )

        // Other buttons
        Column(
            Modifier.align(buttonAlignment),
            verticalArrangement = Arrangement.Center,
        ) {
            HoldButton(
                onChange = onPrimaryButtonChange,
                modifier = Modifier.padding(10.dp).size(80.dp),
                shape = CircleShape,
                content = { Text("A") },
            )
            HoldOutlinedButton(
                onChange = onSecondaryButtonChange,
                modifier = Modifier.padding(10.dp).size(80.dp),
                shape = CircleShape,
                content = { Text("B") },
            )
        }
    }

}

@Composable
fun HoldButton(
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ButtonDefaults.shape,
    content: @Composable RowScope.() -> Unit,
) {
    var wasPressed by remember { mutableStateOf(false) }
    var buttonInteractionSource = remember { MutableInteractionSource() }
    val isPressed by buttonInteractionSource.collectIsPressedAsState()

    if (isPressed != wasPressed) {
        onChange(isPressed)
        wasPressed = isPressed
    }

    Button(
        onClick = {},
        modifier = modifier,
        shape = shape,
        interactionSource = buttonInteractionSource,
        content = content,
    )
}

@Composable
fun HoldOutlinedButton(
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ButtonDefaults.shape,
    content: @Composable RowScope.() -> Unit,
) {
    var wasPressed by remember { mutableStateOf(false) }
    var buttonInteractionSource = remember { MutableInteractionSource() }
    val isPressed by buttonInteractionSource.collectIsPressedAsState()

    if (isPressed != wasPressed) {
        onChange(isPressed)
        wasPressed = isPressed
    }

    OutlinedButton(
        onClick = {},
        modifier = modifier,
        shape = shape,
        interactionSource = buttonInteractionSource,
        content = content,
    )
}


@OptIn(ExperimentalFoundationApi::class)
class Scroll2DWrapper(initialPosition: Offset = Offset.Zero) {
    val _position = MutableLiveData(Offset.Zero)
    val position: LiveData<Offset> = _position

    val initialPosition = initialPosition
    var dragOffset = Offset.Zero
    val flingAnimation = Animatable(Offset.Zero, Offset.VectorConverter)

    val draggableState = Draggable2DState { delta ->
        dragOffset += delta
        updatePosition()
        delta
    }

    internal fun updatePosition() {
        _position.postValue(initialPosition + dragOffset + flingAnimation.value)
    }

    /**
     * Apply this Scroll2DWrapper to a modifier
     */
    fun apply(coroutineScope: CoroutineScope, modifier: Modifier) : Modifier {
        return modifier.draggable2D(
            state = draggableState,
            onDragStarted = { startedPosition ->
                coroutineScope.launch {
                    flingAnimation.stop()
                }
            },
            onDragStopped = { velocity ->
                coroutineScope.launch {
                    flingAnimation.animateDecay(
                        initialVelocity = Offset.Zero.copy(
                            velocity.x,
                            velocity.y,
                        ),
                        animationSpec = splineBasedDecay(UnityDensity),
                        block = {
                            updatePosition()
                        }
                    )
                }
            },
        )
    }
}

private val UnityDensity =
    object : Density {
        override val density: Float
            get() = 1f

        override val fontScale: Float
            get() = 1f
    }



