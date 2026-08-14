package com.thresholdinc.insidher.ui

object Routes {
    const val WALKTHROUGH = "walkthrough"
    const val ONBOARDING = "onboarding"
    const val THREADS = "threads"
    const val THREAD = "thread/{threadId}"
    fun thread(id: String) = "thread/$id"
}
