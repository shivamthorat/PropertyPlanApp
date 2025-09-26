package com.example.propertyplan.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.propertyplan.model.PlanData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun sharePlanJson(context: Context, data: PlanData) {
    val json = Json { prettyPrint = true }.encodeToString(data)
    val file = File(context.cacheDir, "property-plan.json")
    file.writeText(json)

    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Plan JSON"))
}
