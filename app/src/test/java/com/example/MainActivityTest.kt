package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @Test
    fun testActivityStarts() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        assert(activity != null)
    }
}
