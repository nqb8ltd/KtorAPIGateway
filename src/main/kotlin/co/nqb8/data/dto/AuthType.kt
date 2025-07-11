package co.nqb8.data.dto

import kotlinx.serialization.Serializable

@Serializable
enum class AuthType {
    JWT, KEY, NONE
}