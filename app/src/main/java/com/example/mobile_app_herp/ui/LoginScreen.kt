package com.example.mobile_app_herp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.HerpConfig
import com.example.mobile_app_herp.ui.theme.HerpType
import kotlinx.coroutines.launch

/**
 * The hero here is the workspace address. Every hotel on this platform is its own
 * host — `rodrio-app.v3.ceyinfo.com` — and that address is the one thing staff
 * already recognise from the browser on the front desk PC. So it is not buried in
 * helper text: it assembles in monospace as the slug is typed, the typed part in
 * brass, the rest greyed. Type the wrong workspace and you can see it.
 */
@Composable
fun LoginScreen(onSignedIn: () -> Unit, modifier: Modifier = Modifier) {
    val prefs = Herp.prefs
    val scope = rememberCoroutineScope()

    var slug by rememberSaveable { mutableStateOf(prefs.slug.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(prefs.email.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val cleanSlug = slug.trim().lowercase()
    val canSubmit = cleanSlug.isNotEmpty() && email.isNotBlank() && password.isNotEmpty() && !busy

    fun submit() {
        if (!canSubmit) return
        busy = true
        error = null
        scope.launch {
            runCatching { Herp.client.login(cleanSlug, email.trim(), password) }
                .onSuccess {
                    busy = false
                    password = ""
                    Herp.reset()
                    onSignedIn()
                }
                .onFailure {
                    busy = false
                    error = it.message ?: "Could not sign in"
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Gutter),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            "HOTEL ERP",
            style = HerpType.Eyebrow,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in",
            style = HerpType.Display,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        BrassRule()

        Spacer(Modifier.height(28.dp))

        // ── The hero: the address, assembling as you type ──────────────────────
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "YOUR WORKSPACE ADDRESS",
                    style = HerpType.Stamp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(addressLine(cleanSlug), style = HerpType.Address)
            }
        }

        Spacer(Modifier.height(20.dp))

        Field(
            value = slug,
            onValueChange = { slug = it; error = null },
            label = "Workspace",
            enabled = !busy,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            support = "The first part of that address",
        )

        Spacer(Modifier.height(12.dp))

        Field(
            value = email,
            onValueChange = { email = it; error = null },
            label = "Email",
            enabled = !busy,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )

        Spacer(Modifier.height(12.dp))

        Field(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password",
            enabled = !busy,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            visualTransformation = PasswordVisualTransformation(),
        )

        if (error != null) {
            Spacer(Modifier.height(18.dp))
            Notice(error!!)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = ::submit,
            enabled = canSubmit,
            shape = CardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("SIGN IN", style = HerpType.Action)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

/** `{slug}-app.v3.ceyinfo.com`, with the part you control picked out in brass. */
@Composable
private fun addressLine(slug: String): AnnotatedString {
    val brass = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        withStyle(SpanStyle(color = muted)) { append("https://") }
        if (slug.isEmpty()) {
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Bold)) {
                append("workspace")
            }
        } else {
            withStyle(SpanStyle(color = brass, fontWeight = FontWeight.Bold)) { append(slug) }
        }
        withStyle(SpanStyle(color = muted)) { append("-app.${HerpConfig.DOMAIN_BASE}") }
    }
}

/**
 * One field style for the whole app. Brass focus ring, no filled container — a
 * form on a phone in a bright corridor needs edges, not tinted boxes.
 */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    support: String? = null,
    minLines: Int = 1,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = HerpType.Stamp) },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        supportingText = support?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        singleLine = minLines == 1,
        minLines = minLines,
        enabled = enabled,
        shape = CardShape,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** An error, framed. Says what happened; never apologises. */
@Composable
fun Notice(message: String, label: String = "Error") {
    SpineCard(spine = MaterialTheme.colorScheme.error) {
        Column {
            Stamp(label, MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
