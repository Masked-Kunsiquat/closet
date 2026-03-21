package com.closet.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.closet.R
import com.closet.core.ui.R as CoreUiR
import com.closet.features.outfits.JournalRoute
import com.closet.features.outfits.OutfitsRoute
import com.closet.features.outfits.journalScreen
import com.closet.features.outfits.outfitBuilderScreen
import com.closet.features.outfits.outfitsScreen
import com.closet.features.outfits.wardrobePickerScreen
import com.closet.features.wardrobe.AddClothingDestination
import com.closet.features.wardrobe.BrandManagementDestination
import com.closet.features.wardrobe.BrandManagementScreen
import com.closet.features.wardrobe.ClothingDetailDestination
import com.closet.features.wardrobe.ClothingDetailScreen
import com.closet.features.wardrobe.ClothingFormScreen
import com.closet.features.wardrobe.ClosetDestination
import com.closet.features.wardrobe.ClosetScreen
import com.closet.features.wardrobe.EditClothingDestination
import kotlin.reflect.KClass

// ─── Bottom nav items ─────────────────────────────────────────────────────────

private data class TopLevelRoute(
    val destination: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

private val topLevelRoutes = listOf(
    TopLevelRoute(ClosetDestination, ClosetDestination::class, R.string.nav_closet, CoreUiR.drawable.ic_icon_coat_hanger),
    TopLevelRoute(OutfitsRoute, OutfitsRoute::class, R.string.nav_outfits, CoreUiR.drawable.ic_icon_t_shirt),
    TopLevelRoute(JournalRoute(), JournalRoute::class, R.string.nav_journal, CoreUiR.drawable.ic_icon_calendar_dots),
)

private fun NavDestination?.isTopLevel() =
    topLevelRoutes.any { this?.hasRoute(it.routeClass) == true }

// ─── Nav graph ────────────────────────────────────────────────────────────────

/**
 * Root [NavHost] for the app, wiring all feature destinations into a single navigation graph.
 * Owns the root [Scaffold] so the bottom [NavigationBar] can be conditionally shown based on
 * the current destination — visible only on the three top-level tabs, hidden on detail screens.
 *
 * Tab switches use [saveState]/[restoreState] so each tab preserves its own back stack state.
 */
@Composable
fun ClosetNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentDestination.isTopLevel()) {
                ClosetBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { destination ->
                        navController.navigate(destination) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ClosetDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable<ClosetDestination> {
                ClosetScreen(
                    onAddItemClick = { navController.navigate(AddClothingDestination) },
                    onItemClick = { itemId -> navController.navigate(ClothingDetailDestination(itemId)) }
                )
            }

            composable<ClothingDetailDestination> {
                ClothingDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { itemId -> navController.navigate(EditClothingDestination(itemId)) },
                    onNavigateToJournal = { date ->
                        navController.navigate(JournalRoute(initialDate = date)) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }

            composable<AddClothingDestination> {
                ClothingFormScreen(
                    onBackClick = { navController.popBackStack() },
                    onManageBrands = { navController.navigate(BrandManagementDestination) }
                )
            }

            composable<EditClothingDestination> {
                ClothingFormScreen(
                    onBackClick = { navController.popBackStack() },
                    onManageBrands = { navController.navigate(BrandManagementDestination) }
                )
            }

            composable<BrandManagementDestination> {
                BrandManagementScreen(onBack = { navController.popBackStack() })
            }

            outfitsScreen(navController)
            outfitBuilderScreen(navController)
            wardrobePickerScreen(navController)
            journalScreen()
        }
    }
}

// ─── Bottom bar ───────────────────────────────────────────────────────────────

@Composable
private fun ClosetBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Any) -> Unit
) {
    NavigationBar {
        topLevelRoutes.forEach { topLevel ->
            val selected = currentDestination?.hasRoute(topLevel.routeClass) == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(topLevel.destination) },
                icon = {
                    Icon(
                        painter = painterResource(topLevel.iconRes),
                        contentDescription = stringResource(topLevel.labelRes)
                    )
                },
                label = { Text(stringResource(topLevel.labelRes)) }
            )
        }
    }
}
