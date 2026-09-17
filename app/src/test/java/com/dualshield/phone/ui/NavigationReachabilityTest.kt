package com.dualshield.phone.ui

import com.dualshield.phone.ui.navigation.Routes
import com.dualshield.phone.ui.navigation.TOP_LEVEL_DESTINATIONS
import com.dualshield.phone.ui.navigation.isTopLevelRoute
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every screen this app has is reachable, and reachable from somewhere sensible.
 *
 * A screen nobody can find is the same as a screen that does not exist, and this app had
 * four of them: Blocked numbers, Allowed numbers, the India blocklist and Recovery Call
 * Protection could only be opened by going into Shield and then into a SIM. The routes were
 * fine. Nothing linked to them.
 */
class NavigationReachabilityTest {

    private fun uiRoot() = listOf(
        File("src/main/java/com/dualshield/phone/ui"),
        File("app/src/main/java/com/dualshield/phone/ui"),
    ).first { it.isDirectory }

    private fun navHost() = uiRoot().walkTopDown().first { it.name == "DualShieldNavHost.kt" }

    @Test
    fun `there are exactly three tabs and Shield is not one of them`() {
        // The product's central claim is that it is a phone which happens to be protected.
        // A Shield tab would make it a security console that happens to make calls.
        assertEquals(3, TOP_LEVEL_DESTINATIONS.size)
        assertEquals(
            listOf(Routes.PHONE, Routes.MESSAGES, Routes.CONTACTS),
            TOP_LEVEL_DESTINATIONS.map { it.route },
        )
        assertFalse(isTopLevelRoute(Routes.SHIELD))
        assertTrue(TOP_LEVEL_DESTINATIONS.none { it.label.contains("Shield", ignoreCase = true) })
    }

    @Test
    fun `every route the app declares has a destination in the graph`() {
        // The graph names routes by their constant, not by the string they hold, so this
        // looks for `Routes.NAME`. An earlier version of this test compared the string
        // values and reported eighteen routes missing that were all perfectly wired.
        val graph = navHost().readText()
        val missing = Routes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.name }
            .filterNot { name -> graph.contains("Routes.$name") }

        assertTrue("Expected route constants", Routes::class.java.declaredFields.isNotEmpty())
        assertEquals("Routes with no composable in the graph", emptyList<String>(), missing)
    }

    @Test
    fun `the Shield screens are reachable from Settings`() {
        // §85 asks for them there. They existed two levels down, behind a SIM.
        val settings = uiRoot().walkTopDown().first { it.name == "SettingsScreen.kt" }.readText()
        listOf(
            "Shield status",
            "Blocked numbers",
            "Allowed numbers",
            "India blocklist",
            "Blocked call logs",
            "Recovery Call Protection",
        ).forEach { row ->
            assertTrue("Settings should offer '$row'", settings.contains("\"$row\""))
        }
    }

    @Test
    fun `Settings wires each of those rows to a real route`() {
        val graph = navHost().readText()
        val settingsBlock = graph.substring(
            graph.indexOf("SettingsScreen("),
            graph.indexOf("composable(Routes.CALL_RECORDING)"),
        )
        listOf("BLOCKED_NUMBERS", "INDIA_PROTECTION", "RECOVERY_PROTECTION", "VAULT", "SHIELD")
            .forEach { route ->
                assertTrue(
                    "Settings must navigate to Routes.$route",
                    settingsBlock.contains("Routes.$route") ||
                        (route == "ALLOWLIST" && settingsBlock.contains("Routes.allowlist")),
                )
            }
        assertTrue(
            "and to the allowlist",
            settingsBlock.contains("Routes.allowlist"),
        )
    }

    @Test
    fun `the Shield screen leads with which line is protected`() {
        // §87. Someone opening Shield is asking one question, and a list of rule counts
        // above the answer makes them scroll for it.
        val shield = uiRoot().walkTopDown().first { it.name == "ShieldScreen.kt" }.readText()
        val simCards = shield.indexOf("items(items = state.sims")
        val blocking = shield.indexOf("\"blocking-heading\"")
        assertTrue("Shield must list the SIMs", simCards > 0)
        assertTrue("and the rule lists", blocking > 0)
        assertTrue("SIM state must come before the rule lists", simCards < blocking)
    }
}
