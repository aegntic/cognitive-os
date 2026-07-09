package com.thresholdinc.luxe.domain

data class AnitaAction(
    val action: String,
    val confidence: Double,
    val reason: String,
    val details: Map<String, Any> = emptyMap(),
    val sovereignty_handoff: Map<String, Any>,
    val reply: String? = null   // MVP: auto-generated reply text — zero setup, instant
)

data class Inquiry(
    val id: String,
    val text: String,
    val from: String,
    val date: String
)

data class Client(
    val id: String,
    val name: String,
    val email: String,
    val preferences: String = "{}",
    val sovereigntyLevel: String = "full",
    val lastContact: String = ""
)

data class MemoryEntry(
    val id: String,
    val clientId: String,
    val type: String, // "inquiry", "decision", "note"
    val content: String,
    val timestamp: String,
    val tags: List<String> = emptyList(),
    val handoffLevel: String = "ai"
)
