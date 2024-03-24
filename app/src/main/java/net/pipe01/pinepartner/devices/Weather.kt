package net.pipe01.pinepartner.devices

import java.time.LocalDateTime

enum class WeatherIcon(val id: Byte, val jsName: String) {
    SUN(0, "sun"),
    FEW_CLOUDS(1, "fewClouds"),
    CLOUDS(2, "cloudy"),
    HEAVY_CLOUDS(3, "heavyClouds"),
    CLOUDS_RAIN(4, "cloudsRain"),
    RAIN(5, "rain"),
    THUNDERSTORM(6, "thunderstorm"),
    SNOW(7, "snow"),
    MIST(8, "mist"),
}

const val MAX_LOCATION_LEN = 32

data class CurrentWeather(
    val time: LocalDateTime,
    val currentTemperature: Double,
    val minimumTemperature: Double,
    val maximumTemperature: Double,
    val location: String,
    val icon: WeatherIcon,
)
