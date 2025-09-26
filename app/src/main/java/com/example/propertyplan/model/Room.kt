package com.example.propertyplan.model

import kotlinx.serialization.Serializable

@Serializable
data class Room(
    var id: String,
    var name: String,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var angle: Float = 0f,
    var isUtility: Boolean = false,
    var color: Int
)