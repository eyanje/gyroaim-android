
Should be able to reverse left/right

Button: a button that can be pressed by the left hand to shoot or so
Background: swiping on this area sends a "swipe" event.

Disconnect button in the top right corner.

The main screen has connection settings and a connect button. 

Or: swipe from right to left to connect
Swipe from the very left edge (button side) to disconnect. Pressing the back
button also disconnects, though it's not easy to do.
Of course, a connect button still exists.

Left-handed reverses this effect.

Connection can be created and destroyed. Ideally, the connection is created
before publishing an Intent, but, because of the way swiping works, we might
have to do the connection after changing layouts, returning back to the
settings.

We should broadcast events whenever we are connected. When exiting the app or
pressing the disconnect button, we disconnect. The disconnect button changes to
the connect button, which allows the user to initiate a new connection.

The user's last connection is saved and is filled in by default when they try to
reconnect.


Types of events:
 * Gyroscope
 * Button down/up
 * Touch down/up
 * Touch move (swipe)

A server should try to understand these events as best as it can. Perhaps button
down maps to left click, or maybe right click, or maybe even a key. Swipes may
be ignored or may be interpreted as move movements.



