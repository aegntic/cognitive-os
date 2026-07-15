package com.thresholdinc.insidher.contracts

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Request to register a device's public key.
 *
 * The public key is a base64-encoded SPKI ECDSA P-256 key.
 */
@Serializable
data class DeviceRegistrationRequest(
    val publicKey: String,
    val deviceName: String? = null,
) {
    init {
        require(publicKey.isNotBlank()) { "publicKey must not be blank" }
    }
}

/**
 * Response from a successful device registration.
 */
@Serializable
data class DeviceRegistrationResponse(
    val deviceKeyId: String,
    val publicKey: String,
    val deviceName: String?,
    val registeredAt: Instant,
)

/**
 * A registered device key for cryptographic request signing.
 */
@Serializable
data class DeviceKey(
    val id: String,
    val publicKey: String,
    val deviceName: String?,
    val registeredAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(publicKey.isNotBlank()) { "publicKey must not be blank" }
    }
}
