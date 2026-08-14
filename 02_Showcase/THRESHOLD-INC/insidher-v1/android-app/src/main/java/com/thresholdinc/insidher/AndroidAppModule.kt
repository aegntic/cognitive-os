package com.thresholdinc.insidher

/**
 * Module marker for the :android-app module.
 *
 * This module contains the Android application: SMS receiver/sender, device auth
 * (ECDSA P-256 in Android Keystore), UI screens (onboarding, conversation, approval),
 * backend client (OkHttp), and WorkManager polling.
 *
 * It depends on :core and :contracts.
 */
object AndroidAppModule
