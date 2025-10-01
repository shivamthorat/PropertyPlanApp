package com.example.propertyplan.model

import kotlinx.serialization.Serializable

@Serializable
data class PlacedItem(
    var type: ItemType,
    var x: Float,
    var y: Float,
    /** degrees, 0 = to the right */
    var rotation: Float = 0f,
    /** visual size in world px (your ppm already applied when created) */
    var sizeW: Float,
    var sizeH: Float,
    /** optional for stairs */
    var steps: Int? = null
)
