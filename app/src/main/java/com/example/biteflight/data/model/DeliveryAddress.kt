package com.example.biteflight.data.model

data class DeliveryAddress(
    val city: String = "",
    val area: String = "",
    val street: String = "",
    val building: String = "",
    val floor: String = "",
    val apartment: String = "",
    val landmark: String = "",
    val instructions: String = ""
) {
    fun isValid(): Boolean =
        city.isNotBlank() &&
                area.isNotBlank() &&
                street.isNotBlank() &&
                building.isNotBlank()

    fun formatted(): String = listOfNotNull(
        apartment.takeIf { it.isNotBlank() }?.let { "Apartment $it" },
        floor.takeIf { it.isNotBlank() }?.let { "Floor $it" },
        building.takeIf { it.isNotBlank() }?.let { "Building $it" },
        street.takeIf { it.isNotBlank() },
        area.takeIf { it.isNotBlank() },
        city.takeIf { it.isNotBlank() },
        landmark.takeIf { it.isNotBlank() }?.let { "Landmark: $it" },
        instructions.takeIf { it.isNotBlank() }?.let { "Instructions: $it" }
    ).joinToString(", ")
}
