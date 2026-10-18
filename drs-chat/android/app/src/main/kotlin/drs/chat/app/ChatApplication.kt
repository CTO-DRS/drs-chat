package drs.chat.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ChatApplication — Hilt-annotated Application. The composition root for v1.
 *
 * Hilt generates the dependency graph at compile time from the @Module classes
 * in di/. The Application instance lives for the whole process; Hilt uses it
 * as the singleton container.
 *
 * Onboarding flow: MainActivity launches → ChatViewModel checks for an
 * existing identity in DrDatabase. If none, navigates to OnboardingScreen
 * which calls CryptoEngine.generate() → persists identity → user can add
 * contacts via QR/share-link.
 */
@HiltAndroidApp
class ChatApplication : Application()
