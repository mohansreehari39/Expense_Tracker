package et.android.kharcha.data

import kotlinx.serialization.Serializable

/**
 * The few request/response shapes the phone still exchanges with the
 * Windows server outside record sync: pairing, heartbeats, log reports and
 * joining a household or activity.
 * Everything else travels as et.core.api.SyncRecords, shared with the
 * server rather than mirrored here. These mirror
 * Implementation/Windows/app/.../server/Dto.kt field-for-field.
 */

@Serializable
data class MemberDto(val id: String, val displayName: String)

/** The joining phone's deviceId travels in the X-Device-Id header. */
@Serializable
data class AddMemberRequest(val displayName: String, val email: String? = null, val phone: String? = null)

@Serializable
data class TripParticipantDto(val id: String, val displayName: String)

/** The joining phone's deviceId travels in the X-Device-Id header. */
@Serializable
data class AddTripParticipantRequest(val displayName: String)

@Serializable
data class RegisterDeviceRequest(val id: String, val label: String, val pairingSecret: String)

@Serializable
data class PairDeviceResponse(val id: String, val label: String, val pairingKey: String, val pairedAt: Long, val lastSeenAt: Long)

@Serializable
data class HeartbeatDeviceRequest(val pairingKey: String, val label: String)

@Serializable
data class ClientLogRequest(val label: String, val level: String, val message: String)
