package com.example.propertyplan.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.propertyplan.model.*
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

class PlanViewModel : ViewModel() {

    var floors by mutableStateOf(mutableListOf<Floor>())
        private set
    var currentFloorIndex by mutableStateOf(-1)
    var selectedRoomId by mutableStateOf<String?>(null)

    // Canvas state
    var mode by mutableStateOf(UIMode.Draw) // Draw or Move
    var showGrid by mutableStateOf(true)
    var snapGrid by mutableStateOf(false)
    var angleSnap by mutableStateOf(true)

    // In-app floor clipboard
    private var clipboardFloor: Floor? = null

    init {
        addFloor("Floor 1")
        mode = UIMode.Draw
    }

    fun currentFloor(): Floor? = floors.getOrNull(currentFloorIndex)

    fun addFloor(name: String = "Floor ${floors.size + 1}") {
        val f = Floor(
            id = uid(),
            name = name,
            params = FloorParams(
                floorId = name.replace("\\s+".toRegex(), "-"),
                year = 2025,
                useType = "Residential",
                subUseType = "Apartment",
                isRenter = false,
                constructionType = "Concrete",
                ppm = 50
            ),
            rooms = mutableListOf()
        )
        floors = (floors + f).toMutableList()
        currentFloorIndex = floors.lastIndex
        selectedRoomId = null
    }

    fun removeCurrentFloor() {
        if (currentFloorIndex < 0) return
        val new = floors.toMutableList()
        new.removeAt(currentFloorIndex)
        floors = new
        currentFloorIndex = (currentFloorIndex).coerceAtMost(floors.lastIndex)
        selectedRoomId = null
    }

    fun duplicateCurrentFloor() {
        val f = currentFloor() ?: return
        val copy = cloneFloor(f)
        copy.name = "${f.name}-copy"
        copy.params.floorId = copy.name
        copy.rooms.forEach { r -> r.x += 30f; r.y += 30f; r.id = uid() }
        floors = (floors + copy).toMutableList()
        currentFloorIndex = floors.lastIndex
    }

    fun copyFloor() { clipboardFloor = currentFloor()?.let { cloneFloor(it) } }

    fun pasteFloor() {
        val clip = clipboardFloor ?: return
        val copy = cloneFloor(clip).apply {
            id = uid()
            name = "${clip.name}-pasted"
            params.floorId = name
            rooms.forEach { r -> r.id = uid(); r.x += 40f; r.y += 40f }
        }
        floors = (floors + copy).toMutableList()
        currentFloorIndex = floors.lastIndex
    }

    fun saveFloorParams(
        floorId: String? = null,
        year: Int? = null,
        useType: String? = null,
        subUseType: String? = null,
        isRenter: Boolean? = null,
        constructionType: String? = null,
        ppm: Int? = null
    ) {
        val f = currentFloor() ?: return
        floorId?.let { f.params.floorId = it }
        year?.let { f.params.year = it }
        useType?.let { f.params.useType = it }
        subUseType?.let { f.params.subUseType = it }
        isRenter?.let { f.params.isRenter = it }
        constructionType?.let { f.params.constructionType = it }
        ppm?.let { f.params.ppm = it }
        f.name = f.params.floorId
        floors = floors.toMutableList()
    }

    fun addRoomFromTemplate(name: String, w: Float, h: Float, isUtility: Boolean, cx: Float, cy: Float) {
        val f = currentFloor() ?: return
        val room = Room(
            id = uid(),
            name = name,
            x = cx - w / 2f,
            y = cy - h / 2f,
            w = w,
            h = h,
            angle = 0f,
            isUtility = isUtility,
            color = if (isUtility) 0xFFE53935.toInt() else 0xFF22C55E.toInt()
        )
        f.rooms.add(room)
        selectedRoomId = room.id
        floors = floors.toMutableList()
    }

    fun clearRooms() {
        currentFloor()?.let { it.rooms.clear() }
        selectedRoomId = null
        floors = floors.toMutableList()
    }

    fun duplicateSelectedRoom() {
        val f = currentFloor() ?: return
        val r = f.rooms.find { it.id == selectedRoomId } ?: return
        val c = r.copy(id = uid(), x = r.x + 20f, y = r.y + 20f)
        f.rooms.add(c)
        selectedRoomId = c.id
        floors = floors.toMutableList()
    }

    fun deleteSelectedRoom() {
        val f = currentFloor() ?: return
        val idx = f.rooms.indexOfFirst { it.id == selectedRoomId }
        if (idx >= 0) {
            f.rooms.removeAt(idx)
            selectedRoomId = null
            floors = floors.toMutableList()
        }
    }

    fun updateSelectedRoom(update: (Room) -> Unit) {
        val f = currentFloor() ?: return
        val r = f.rooms.find { it.id == selectedRoomId } ?: return
        update(r)
        floors = floors.toMutableList()
    }

    fun selectRoom(id: String?) { selectedRoomId = id }

    fun totalAreaM2(floor: Floor): Float {
        val ppm = max(1, floor.params.ppm)
        return floor.rooms.sumOf { ((it.w / ppm) * (it.h / ppm)).toDouble() }.toFloat()
    }

    fun asJson(): String {
        return Json { prettyPrint = true }.encodeToString(
            PlanData(floors = floors)
        )
    }

    private fun cloneFloor(f: Floor): Floor =
        Json.decodeFromString(Json.encodeToString(Floor.serializer(), f))

    private fun uid(): String = (Math.random().toString().substring(2, 9))

    // in PlanViewModel.kt
    var draftRect by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        private set

    fun startDraft(x: Float, y: Float) { draftRect = androidx.compose.ui.geometry.Rect(x, y, x, y) }
    fun updateDraft(x: Float, y: Float) {
        draftRect?.let { d -> draftRect = androidx.compose.ui.geometry.Rect(min(d.left, x), min(d.top, y), max(d.right, x), max(d.bottom, y)) }
    }
    fun finishDraft(): androidx.compose.ui.geometry.Rect? = draftRect.also { draftRect = null }

    var viewport by mutableStateOf(Triple(1f, 0f, 0f)) // (scale, panX, panY)
        private set
    fun setViewport(scale: Float, panX: Float, panY: Float) { viewport = Triple(scale, panX, panY) }

    // Optional autosave hook (UI can observe/plug into this)
    var onAutosave: ((String) -> Unit)? = null
        private set

    /** Set a callback to receive pretty JSON every time an edit is committed. */
    fun setAutosaveCallback(cb: ((String) -> Unit)?) { onAutosave = cb }

    /** Called by CanvasBoard at the end of drag/resize/draw. Safe no-op if no callback set. */
    fun onEditCommitted() {
        // Reassign to trigger Compose observers if needed
        floors = floors.toMutableList()
        // Emit serialized state to whoever is listening
        onAutosave?.invoke(asJson())
    }

    /** Toggle the background grid (used by the Grid chip in the top status bar). */
    fun toggleGrid() { showGrid = !showGrid }

}

enum class UIMode { Draw, Move }
