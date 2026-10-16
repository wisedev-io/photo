package androidx.core.content

object ContextCompat {
    fun checkSelfPermission(context: android.content.Context, permission: String): Int = 0
    fun getMainExecutor(context: android.content.Context): java.util.concurrent.Executor = java.util.concurrent.Executor { it.run() }
}
