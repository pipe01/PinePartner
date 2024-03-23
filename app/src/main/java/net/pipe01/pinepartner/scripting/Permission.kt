package net.pipe01.pinepartner.scripting

enum class Permission(val title: String) {
    USE_JAVA("Use Java"),
    RECEIVE_NOTIFICATIONS("Receive notifications"),
    HTTP("Send HTTP requests"),
    VOLUME_CONTROL("Control the phone's volume"),
    MEDIA_CONTROL("Control media playback"),
    LOCATION("Access location services"),
}
