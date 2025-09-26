package com.example.propertyplan.model

import kotlinx.serialization.Serializable

@Serializable
data class FloorParams(
    var floorId: String,
    var year: Int,
    var useType: String,
    var subUseType: String,
    var isRenter: Boolean,
    var constructionType: String,
    /** Pixels per meter */
    var ppm: Int
)



@Serializable
data class Floor(
    var id: String,
    var name: String,
    var params: FloorParams,
    val rooms: MutableList<Room> = mutableListOf()
)

@Serializable
data class PlanData(
    val floors: List<Floor>
)
