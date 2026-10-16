package androidx.room

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Retention(AnnotationRetention.BINARY) annotation class Entity(val tableName: String = "")
@Retention(AnnotationRetention.BINARY) annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Retention(AnnotationRetention.BINARY) annotation class Dao
@Retention(AnnotationRetention.BINARY) annotation class Query(val value: String)
@Retention(AnnotationRetention.BINARY) annotation class Insert
@Retention(AnnotationRetention.BINARY) annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)

abstract class RoomDatabase {
    open class Builder<T : RoomDatabase> {
        fun fallbackToDestructiveMigration(): Builder<T> = this
        fun build(): T = error("stub")
    }
}

object Room {
    fun <T : RoomDatabase> databaseBuilder(context: android.content.Context, klass: Class<T>, name: String): RoomDatabase.Builder<T> =
        RoomDatabase.Builder()
}

@Suppress("unused") private fun keepFlow(f: Flow<Int>) = f
