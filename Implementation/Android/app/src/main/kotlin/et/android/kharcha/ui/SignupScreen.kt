package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.ProfileEntity
import et.android.kharcha.ui.theme.kharcha

private val GENDER_OPTIONS = listOf("Male", "Female", "Other", "Prefer not to say")

/** The profile fields as typed — shared by signup and Me → Edit profile. */
data class ProfileDraft(
    val name: String = "",
    val age: String = "",
    val gender: String? = null,
    val phone: String = "",
    val email: String = "",
) {
    /**
     * Every field is required. Phone and email matter most: they're what
     * re-identifies this person if the app is reinstalled and rejoins a
     * household (see et.core.domain.AddMember on the server), so history
     * isn't split across two profiles.
     */
    val isValid: Boolean
        get() = name.isNotBlank() &&
            (age.toIntOrNull() ?: 0) > 0 &&
            gender != null &&
            phone.isNotBlank() &&
            email.trim().let { it.isNotBlank() && it.contains("@") }

    companion object {
        fun from(profile: ProfileEntity) = ProfileDraft(
            name = profile.name,
            age = profile.age?.toString().orEmpty(),
            gender = profile.gender,
            phone = profile.phone.orEmpty(),
            email = profile.email.orEmpty(),
        )
    }
}

/** Name, age, gender, phone and email fields, used by both signup and editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileForm(draft: ProfileDraft, onChange: (ProfileDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.age,
            onValueChange = { onChange(draft.copy(age = it.filter(Char::isDigit))) },
            label = { Text("Age") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Gender", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GENDER_OPTIONS.forEach { option ->
                FilterChip(
                    selected = draft.gender == option,
                    onClick = { onChange(draft.copy(gender = if (draft.gender == option) null else option)) },
                    label = { Text(option) },
                )
            }
        }
        OutlinedTextField(
            value = draft.phone,
            onValueChange = { onChange(draft.copy(phone = it)) },
            label = { Text("Phone number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.email,
            onValueChange = { onChange(draft.copy(email = it)) },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "All fields are required. Phone and email let a reinstalled app recognize you as the same person when you rejoin a household, so your history isn't split across two profiles.",
            style = MaterialTheme.typography.bodySmall,
            color = kharcha.muted,
        )
    }
}

/**
 * Shown once, before anything else — there's no login, so this profile is
 * this device's permanent identity everywhere it's used (see
 * [et.android.kharcha.data.LocalRepository.ensureMyMembership]). Not tied
 * to any server; the app is fully usable offline from here. It can be
 * changed later from Me → Edit profile.
 */
@Composable
fun SignupScreen(onSignedUp: (name: String, age: Int?, gender: String?, phone: String?, email: String?) -> Unit) {
    var draft by remember { mutableStateOf(ProfileDraft()) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Welcome to Kharcha", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Set up your profile once. It's used in every household and activity you join, and stays on this phone. You can change it later from Me.",
                style = MaterialTheme.typography.bodyMedium,
                color = kharcha.muted,
            )
            ProfileForm(draft, onChange = { draft = it }, modifier = Modifier.padding(top = 4.dp))
            Button(
                enabled = draft.isValid,
                onClick = { onSignedUp(draft.name.trim(), draft.age.toIntOrNull(), draft.gender, draft.phone.trim(), draft.email.trim()) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Get started", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
        }
    }
}
