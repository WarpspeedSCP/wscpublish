package dev.wscp.utils

fun <T> MutableList<T>.pop(): T? = this.removeLastOrNull()
fun <T> MutableList<T>.push(item: T) = this.add(item)
fun <T> MutableList<T>.top(): T? = this.lastOrNull()
