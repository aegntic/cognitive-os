package com.thresholdinc.insidher.core

/**
 * Module marker for the :core module.
 *
 * This module contains the orchestrator, workers (persona, timing, memory, booking,
 * safety), inference provider, safety policy, and timing policy.
 *
 * It is a pure Kotlin/JVM module with no Android dependencies.
 * It depends on :contracts for shared types.
 */
object CoreModule
