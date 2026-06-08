package id.azkura.auth.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val color: String = "#00E5FF",
    val order: Int = 0,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String = "#00E5FF",
    @ColumnInfo(name = "order") val order: Int = 0,
) {
    fun toDomain(): Folder = Folder(
        id = id,
        name = name,
        color = color,
        order = order,
    )

    companion object {
        fun fromDomain(folder: Folder): FolderEntity = FolderEntity(
            id = folder.id,
            name = folder.name,
            color = folder.color,
            order = folder.order,
        )
    }
}

fun Folder.toEntity(): FolderEntity = FolderEntity.fromDomain(this)
fun FolderEntity.toFolder(): Folder = toDomain()
