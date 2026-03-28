package com.example.arcomtechapp.util

import androidx.lifecycle.LiveData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Small helper to block until LiveData posts a value in tests.
@Suppress("UNCHECKED_CAST")
fun <T> LiveData<T>.getOrAwaitValue(
    time: Long = 2,
    timeUnit: TimeUnit = TimeUnit.SECONDS,
    ignoreNulls: Boolean = false
): T {
    var data: T? = null
    val latch = CountDownLatch(1)
    val observer = object : androidx.lifecycle.Observer<T> {
        override fun onChanged(value: T) {
            if (ignoreNulls && value == null) {
                return
            }
            data = value
            latch.countDown()
            this@getOrAwaitValue.removeObserver(this)
        }
    }

    this.observeForever(observer)

    // Don't wait forever if the LiveData is not set.
    if (!latch.await(time, timeUnit)) {
        removeObserver(observer)
        throw TimeoutException("LiveData value was never set.")
    }

    return data as T
}
