package com.kurai.musikk.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.kurai.musikk.ui.components.NavItem

// Route constants
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PERMISSION = "permission"
    const val STEMS = "stems"
    const val PRACTICE = "practice"
    const val REPERTOIRE = "repertoire"
    const val PROFILE = "profile"
}

// Bottom-nav main destinations
val mainNavRoutes = setOf(Routes.STEMS, Routes.PRACTICE, Routes.REPERTOIRE, Routes.PROFILE)

val bottomNavItems = listOf(
    NavItem(Routes.STEMS,      "Stems",      Icons.Default.GraphicEq),
    NavItem(Routes.PRACTICE,   "Practice",   Icons.Default.Tune),
    NavItem(Routes.REPERTOIRE, "Repertoire", Icons.Default.LibraryMusic),
    NavItem(Routes.PROFILE,    "Profile",    Icons.Default.Person),
)
