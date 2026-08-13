package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val GENDER_OPTIONS = listOf("Male", "Female", "Other", "Prefer not to say")

/**
 * Shown once, before anything else — there's no login, so this profile is
 * this device's permanent identity everywhere it's used (see
 * [et.android.kharcha.data.LocalRepository.ensureMyMembership]). Not tied
 * to any server; the app is fully usable offline from here — connecting
 * to a Windows instance is a separate, optional step from the home screen.
 */
@Composable
fun SignupScreen(onSignedUp: (name: String, age: Int?, gender: String?, phone: String?, email: String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Welcome to Kharcha", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Set up your profile once — it's used everywhere you're a member or participant, on this device only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter(Char::isDigit) },
                    label = { Text("Age") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("Gender", style = MaterialTheme.typography.labelLarge)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GENDER_OPTIONS) { option ->
                        FilterChip(selected = gender == option, onClick = { gender = if (gender == option) null else option }, label = { Text(option) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Phone/email are mandatory because they're what
                // re-identifies this profile as the same person if the app
                // is ever reinstalled — see et.core.domain.AddMember on the
                // Windows side. A bare name match alone isn't reliable
                // enough to safely merge history across a rejoin. Every
                // field on this screen is required — there's no later "edit
                // profile" yet (V2), so an incomplete profile has no way to
                // be filled in afterward.
                Text(
                    "Phone and email are required — they're how a reinstalled app recognizes you as the same person when rejoining a household, instead of splitting your history across two profiles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                val ageValid = (age.toIntOrNull() ?: 0) > 0
                val phoneValid = phone.trim().isNotBlank()
                val emailValid = email.trim().let { it.isNotBlank() && it.contains("@") }
                Button(
                    enabled = name.isNotBlank() && ageValid && gender != null && phoneValid && emailValid,
                    onClick = {
                        onSignedUp(name.trim(), age.toIntOrNull(), gender, phone.trim(), email.trim())
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Get Started") }
            }
        }
    }
}
