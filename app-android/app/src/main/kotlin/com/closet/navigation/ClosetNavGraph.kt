package com.closet.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.closet.R
import com.closet.features.outfits.OutfitsRoute
import com.closet.features.outfits.navigateToOutfits
import com.closet.features.outfits.outfitBuilderScreen
import com.closet.features.outfits.outfitsScreen
import com.closet.features.outfits.wardrobePickerScreen
import com.closet.features.stats.StatsRoute
import com.closet.features.stats.navigateToStats
import com.closet.features.stats.statsScreen
import com.closet.features.wardrobe.*

/**
 * A top-level navigation destination shown in the bottom navigation bar.
 *
 * @param selectedIcon Icon to show when this tab is active.
 * @param unselectedIcon Icon to show when this tab is inactive.
 * @param labelRes String resource for the tab label.
 * @param isSelected Returns true when the given [NavDestination] matches this route.
 * @param navigate Navigates to this tab's root destination with the provided [NavOptions].
 */
data class TopLevelRoute(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
    val isSelected: (NavDestination?) -> Boolean,
    val navigate: (NavController, NavOptions?) -> Unit
)

private val topLevelRoutes = listOf(
    TopLevelRoute(
        selectedIcon = Icons.Filled.Checkroom,
        unselectedIcon = Icons.Outlined.Checkroom,
        labelRes = R.string.nav_closet,
        isSelected = { dest -> dest?.route == ClosetDestination::class.qualifiedName },
        navigate = { nav, opts -> nav.navigate(ClosetDestination, opts) }
    ),
    TopLevelRoute(
        selectedIcon = Icons.Filled.Style,
        unselectedIcon = Icons.Outlined.Style,
        labelRes = R.string.nav_outfits,
        isSelected = { dest -> dest?.route == OutfitsRoute::class.qualifiedName },
        navigate = { nav, opts -> nav.navigateToOutfits(opts) }
    ),
    TopLevelRoute(
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        labelRes = R.string.nav_stats,
        isSelected = { dest -> dest?.route == StatsRoute::class.qualifiedName },
        navigate = { nav, opts -> nav.navigateToStats(opts) }
    )
)

/**
 * Root [NavHost] for the app, wiring all feature destinations into a single navigation graph.
 * Hosts a [NavigationBar] for the three top-level tabs: Closet, Outfits, and Stats.
 * The bottom bar is hidden on sub-screens (detail, form, builder, picker).
 *
 * @param modifier Modifier applied to the [NavHost].
 * @param navController The [NavHostController] managing back-stack state.
 */
@Composable
fun ClosetNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val topLevelNavOptions = navOptions {
        popUpTo<ClosetDestination> {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            val showBottomBar = topLevelRoutes.any { it.isSelected(currentDestination) }
            if (showBottomBar) {
                NavigationBar {
                    topLevelRoutes.forEach { topLevel ->
                        val selected = topLevel.isSelected(currentDestination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { topLevel.navigate(navController, topLevelNavOptions) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) topLevel.selectedIcon else topLevel.unselectedIcon,
                                    contentDescription = stringResource(topLevel.labelRes)
                                )
                            },
                            label = { Text(stringResource(topLevel.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ClosetDestination,
            modifier = modifier.padding(innerPadding)
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
                    onEdit = { itemId -> navController.navigate(EditClothingDestination(itemId)) }
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
                BrandManagementScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            outfitsScreen(navController)
            outfitBuilderScreen(navController)
            wardrobePickerScreen(navController)

            statsScreen(
                onItemClick = { itemId -> navController.navigate(ClothingDetailDestination(itemId)) }
            )
        }
    }
}
