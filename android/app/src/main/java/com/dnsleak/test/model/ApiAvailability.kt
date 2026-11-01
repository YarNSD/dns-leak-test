package com.dnsleak.test.model

enum class ApiAvailabilityState {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    BLOCKED
}

data class ApiAvailabilityResult(
    val state: ApiAvailabilityState = ApiAvailabilityState.NOT_CHECKED,
    val pingMs: Long = 0,
    val details: String = ""
)
