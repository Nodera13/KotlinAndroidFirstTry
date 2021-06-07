package uni.fmi.bachelors.weatherapp.Models

import java.io.Serializable

data class Wind(
    val speed: Double,
    val deg: Int
) : Serializable