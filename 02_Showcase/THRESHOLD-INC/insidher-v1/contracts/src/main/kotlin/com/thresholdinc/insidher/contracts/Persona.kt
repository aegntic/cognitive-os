package com.thresholdinc.insidher.contracts

import kotlinx.serialization.Serializable

/**
 * Persona configuration for an agent: tone, vocabulary, offerings, boundaries, availability.
 *
 * All fields except [name] and [tone] are optional (may be empty/null).
 */
@Serializable
data class PersonaProfile(
    /** Display name. Must be non-blank. */
    val name: String,
    /** Tone description. Must be non-blank. */
    val tone: String,
    /** Vocabulary words/phrases used by this persona. Entries must be non-blank. */
    val vocabulary: List<String> = emptyList(),
    /** Service offerings. Entries must be non-blank. */
    val offerings: List<String> = emptyList(),
    /** Wording for deposit requests. If present, must be non-blank. */
    val depositWording: String? = null,
    /** Boundary statements. Entries must be non-blank when present. */
    val boundaries: List<String>? = null,
    /** Availability policy for this persona. */
    val availabilityPolicy: AvailabilityPolicy = AvailabilityPolicy(),
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(tone.isNotBlank()) { "tone must not be blank" }
        require(vocabulary.all { it.isNotBlank() }) { "vocabulary entries must not be blank" }
        require(offerings.all { it.isNotBlank() }) { "offerings entries must not be blank" }
        require(depositWording?.isNotBlank() != false) { "depositWording must be non-blank if present" }
        require(boundaries?.all { it.isNotBlank() } != false) { "boundaries entries must not be blank" }
    }
}

/** Alias for [PersonaProfile]. */
typealias PersonaConfig = PersonaProfile
