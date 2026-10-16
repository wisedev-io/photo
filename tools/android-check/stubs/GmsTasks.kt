package com.google.android.gms.tasks

fun interface OnSuccessListener<T> { fun onSuccess(result: T) }
fun interface OnFailureListener { fun onFailure(e: Exception) }
fun interface OnCanceledListener { fun onCanceled() }
fun interface OnCompleteListener<T> { fun onComplete(task: Task<T>) }

abstract class Task<T> {
    abstract fun addOnSuccessListener(listener: OnSuccessListener<in T>): Task<T>
    abstract fun addOnFailureListener(listener: OnFailureListener): Task<T>
    abstract fun addOnCanceledListener(listener: OnCanceledListener): Task<T>
    abstract fun addOnCompleteListener(listener: OnCompleteListener<T>): Task<T>
}
