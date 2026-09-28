package com.example

import com.example.data.model.ActiveFocusSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockingServiceTest {

    @Test
    fun testBlockedAppDetectionLogic() {
        val blockedList = listOf("com.instagram.android", "com.google.android.youtube", "com.zhiliaoapp.musically")

        val activeSession = ActiveFocusSession(
            isActive = true,
            isPaused = false,
            isInBreak = false,
            blockedPackageNames = blockedList
        )

        // When YouTube is opened
        val foregroundApp = "com.google.android.youtube"
        val shouldBlock = activeSession.isActive &&
                !activeSession.isPaused &&
                activeSession.blockedPackageNames.contains(foregroundApp)

        assertTrue("Should detect and block YouTube", shouldBlock)

        // When non-blocked app is opened
        val allowedApp = "com.google.android.apps.docs"
        val shouldBlockAllowed = activeSession.isActive &&
                !activeSession.isPaused &&
                activeSession.blockedPackageNames.contains(allowedApp)

        assertFalse("Should not block allowed apps", shouldBlockAllowed)
    }

    @Test
    fun testBreakModeBlockingSuspension() {
        val blockedList = listOf("com.instagram.android")

        // In break with allowAppsDuringBreak = true
        val sessionInPermissiveBreak = ActiveFocusSession(
            isActive = true,
            isInBreak = true,
            allowAppsDuringBreak = true,
            blockedPackageNames = blockedList
        )

        val shouldBlockInPermissiveBreak = sessionInPermissiveBreak.isActive &&
                (!sessionInPermissiveBreak.isInBreak || !sessionInPermissiveBreak.allowAppsDuringBreak) &&
                sessionInPermissiveBreak.blockedPackageNames.contains("com.instagram.android")

        assertFalse("Should suspend blocking during break when allowed", shouldBlockInPermissiveBreak)

        // In break with allowAppsDuringBreak = false
        val sessionInStrictBreak = ActiveFocusSession(
            isActive = true,
            isInBreak = true,
            allowAppsDuringBreak = false,
            blockedPackageNames = blockedList
        )

        val shouldBlockInStrictBreak = sessionInStrictBreak.isActive &&
                (!sessionInStrictBreak.isInBreak || !sessionInStrictBreak.allowAppsDuringBreak) &&
                sessionInStrictBreak.blockedPackageNames.contains("com.instagram.android")

        assertTrue("Should enforce blocking during break when apps not allowed", shouldBlockInStrictBreak)
    }

    @Test
    fun testSystemExemptionLogic() {
        fun isSystemExempt(pkg: String, myPackage: String): Boolean {
            if (pkg == myPackage) return true
            if (pkg.contains("systemui", ignoreCase = true)) return true
            if (pkg.contains("settings", ignoreCase = true)) return true
            if (pkg.contains("dialer", ignoreCase = true) || pkg.contains("telecom", ignoreCase = true)) return true
            if (pkg.contains("emergency", ignoreCase = true)) return true
            return false
        }

        val myPkg = "com.b4ddax.focuslock"
        assertTrue(isSystemExempt(myPkg, myPkg))
        assertTrue(isSystemExempt("com.android.settings", myPkg))
        assertTrue(isSystemExempt("com.google.android.dialer", myPkg))
        assertTrue(isSystemExempt("com.android.systemui", myPkg))
        assertTrue(isSystemExempt("com.android.emergency", myPkg))

        assertFalse(isSystemExempt("com.instagram.android", myPkg))
        assertFalse(isSystemExempt("com.snapchat.android", myPkg))
        assertFalse(isSystemExempt("com.twitter.android", myPkg))
    }
}
