package com.devbehindyou.atomicfilemanager.ui.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

/** Fingerprint or face unlock, falling back to the screen lock (PIN, pattern or password). */
private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True when the device has a screen lock or an enrolled biometric that [authenticate] can use. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Shows the system prompt. [onResult] receives `true` only after a successful unlock. */
fun authenticate(
    activity: FragmentActivity,
    title: String,
    onResult: (Boolean) -> Unit,
) {
    val callback =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence,
            ) {
                onResult(false)
            }
        }
    val info =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
}

/** The hosting [FragmentActivity], found through any [ContextWrapper] layers. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Shows [content] only after the user has unlocked the device, when [required]. Leaving the screen
 * and coming back asks again; rotating the device does not.
 * Manual unlock keeps private content uncomposed until authentication succeeds after an explicit tap.
 */
@Composable
fun AuthGate(
    required: Boolean,
    title: String,
    onDenied: () -> Unit,
    manualUnlock: Boolean = false,
    content: @Composable (onLock: (() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    var unlocked by rememberSaveable(required) { mutableStateOf(!required) }
    var failed by remember(required) { mutableStateOf(false) }
    var attempt by remember(required) { mutableIntStateOf(0) }
    val activity = remember(context) { context.findFragmentActivity() }
    val available = remember(context) { canAuthenticate(context) }

    val waitingForTap = manualUnlock && attempt == 0

    LaunchedEffect(attempt, unlocked, required) {
        if (required && !unlocked && !waitingForTap && activity != null && available) {
            authenticate(activity, title) { ok ->
                if (ok) {
                    unlocked = true
                } else {
                    failed = true
                }
            }
        }
    }

    if (unlocked) {
        val lock: (() -> Unit)? =
            if (required && manualUnlock) {
                {
                    attempt = 0
                    failed = false
                    unlocked = false
                }
            } else {
                null
            }
        content(lock)
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Atomic.colors.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(AtomicSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16, Alignment.CenterVertically),
        ) {
            AtomicIconTile(AtomicIcons.Lock, inverted = true)
            AtomicText(if (manualUnlock) "Your private space" else title, AtomicTextRole.DisplayTitle)
            if (manualUnlock) {
                AtomicText(
                    "Authenticate to access private files. Names and counts stay hidden while locked.",
                    AtomicTextRole.Body,
                )
            }
            when {
                !available || activity == null ->
                    AtomicText(
                        "Set up a screen lock, fingerprint or face unlock to open protected files, " +
                            "or turn the lock off in Settings.",
                        AtomicTextRole.Body,
                    )
                failed || waitingForTap -> {
                    if (failed) AtomicText("Unlock to continue. Nothing was opened.", AtomicTextRole.Body)
                    AtomicButton(
                        if (failed) "Try again" else "Unlock",
                        onClick = {
                            failed = false
                            attempt++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> AtomicLoading("Waiting for unlock…")
            }
            AtomicButton(
                "Go back",
                onClick = onDenied,
                variant = AtomicButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
