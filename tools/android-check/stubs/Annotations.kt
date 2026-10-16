package androidx.annotation

@Retention(AnnotationRetention.BINARY)
annotation class RequiresApi(val value: Int)

@Retention(AnnotationRetention.BINARY)
annotation class StringRes

@Retention(AnnotationRetention.BINARY)
annotation class OptIn(vararg val markerClass: kotlin.reflect.KClass<out Annotation>)
