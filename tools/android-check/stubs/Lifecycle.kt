package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class ViewModel { protected open fun onCleared() {} }

open class AndroidViewModel(private val application: android.app.Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : android.app.Application> getApplication(): T = application as T
}

val ViewModel.viewModelScope: CoroutineScope get() = CoroutineScope(SupervisorJob() + Dispatchers.Main)

interface LifecycleObserver

fun interface LifecycleEventObserver : LifecycleObserver {
    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event)
}

abstract class Lifecycle {
    enum class Event { ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY }
    abstract fun addObserver(observer: LifecycleObserver)
    abstract fun removeObserver(observer: LifecycleObserver)
}

interface LifecycleOwner { val lifecycle: Lifecycle }
