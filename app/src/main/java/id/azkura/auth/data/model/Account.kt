package id.azkura.auth.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val issuer: String = "",
    val account: String = "",
    val secret: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val folderId: String? = null,
    val order: Int = 0,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val issuer: String = "",
    val account: String = "",
    val secret: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val folderId: String? = null,
    @ColumnInfo(name = "order") val order: Int = 0,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): Account = Account(
        id = id,
        issuer = issuer,
        account = account,
        secret = secret,
        algorithm = algorithm,
        digits = digits,
        period = period,
        folderId = folderId,
        order = order,
        notes = notes,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(account: Account): AccountEntity = AccountEntity(
            id = account.id,
            issuer = account.issuer,
            account = account.account,
            secret = account.secret,
            algorithm = account.algorithm,
            digits = account.digits,
            period = account.period,
            folderId = account.folderId,
            order = account.order,
            notes = account.notes,
            createdAt = account.createdAt,
        )
    }
}

fun Account.toEntity(): AccountEntity = AccountEntity.fromDomain(this)
fun AccountEntity.toAccount(): Account = toDomain()
