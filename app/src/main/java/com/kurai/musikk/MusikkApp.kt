package com.kurai.musikk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.kurai.musikk.ui.components.MusikkBottomNav
import com.kurai.musikk.ui.navigation.*
import com.kurai.musikk.ui.screens.splash.SplashScreen
import com.kurai.musikk.ui.screens.onboarding.OnboardingScreen
import com.kurai.musikk.ui.screens.permission.NotificationPermissionScreen
import com.kurai.musikk.ui.screens.stems.StemsScreen
import com.kurai.musikk.ui.screens.practice.PracticeScreen
import com.kurai.musikk.ui.screens.repertoire.RepertoireScreen
import com.kurai.musikk.ui.screens.profile.ProfileScreen

@Composable
fun MusikkApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val auth = remember { FirebaseAuth.getInstance() }

    val showBottomBar = currentRoute in mainNavRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                MusikkBottomNav(
                    items = bottomNavItems,
                    selectedRoute = currentRoute ?: Routes.STEMS,
                    onItemSelected = { item ->
                        if (item.route == currentRoute) return@MusikkBottomNav

                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(onSplashDone = { isSignedIn ->
                    val destination = if (isSignedIn) Routes.STEMS else Routes.ONBOARDING
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onContinue = {
                    navController.navigate(Routes.PERMISSION) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Routes.PERMISSION) {
                NotificationPermissionScreen(onDone = {
                    navController.navigate(Routes.STEMS) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(
                route = Routes.STEMS,
            ) {
                StemsScreen(onNavigateToRepertoire = {
                    navController.navigate(Routes.REPERTOIRE) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(Routes.PRACTICE) {
                PracticeScreen()
            }
            composable(
                route = Routes.REPERTOIRE,
            ) {
                RepertoireScreen(
                    onNavigateToStems = {
                        navController.navigate(Routes.STEMS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToPractice = {
                        navController.navigate(Routes.PRACTICE) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onLogout = {
                    auth.signOut()
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                })
            }
        }
    }
}