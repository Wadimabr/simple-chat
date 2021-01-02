package dev.vaabr.json

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val from: String,
    val message: String
)
