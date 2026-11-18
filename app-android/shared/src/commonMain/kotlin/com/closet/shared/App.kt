package com.closet.shared

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.closet.core.ui.preferences.PreferencesRepository
import com.closet.core.ui.resources.ic_icon_calendar_dots
import com.closet.core.ui.resources.ic_icon_chart_bar
import com.closet.core.ui.resources.ic_icon_chat_circle
import com.closet.core.ui.resources.ic_icon_coat_hanger
import com.closet.core.ui.resources.ic_icon_t_shirt
import com.closet.core.ui.theme.ClosetAccent
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.chat.ChatRoute
import com.closet.features.chat.chatScreen
import com.closet.features.outfits.JournalRoute
import com.closet.features.outfits.OutfitBuilderDestination
import com.closet.features.outfits.OutfitsRoute
import com.closet.features.outfits.journalScreen
import com.closet.features.outfits.outfitBuilderScreen
import com.closet.features.outfits.outfitsScreen
import com.closet.features.outfits.wardrobePickerScreen
import com.closet.features.recommendations.navigateToRecommendations
import com.closet.features.recommendations.recommendationScreen
import com.closet.features.settings.aiSettingsScreen
import com.closet.features.settings.navigateToAiSettings
import com.closet.features.settings.navigateToSettings
import com.closet.features.settings.settingsScreen
import com.closet.features.stats.StatsRoute
import com.closet.features.stats.statsScreen
import com.closet.features.wardrobe.AddClothingDestination
import com.closet.features.wardrobe.BrandManagementDestination
import com.closet.features.wardrobe.BrandManagementScreen
import com.closet.features.wardrobe.BulkWashDestination
import com.closet.features.wardrobe.ClosetDestination
import com.closet.features.wardrobe.ClosetScreen
import com.closet.features.wardrobe.ClothingDetailDestination
import com.closet.features.wardrobe.ClothingDetailScreen
import com.closet.features.wardrobe.ClothingFormScreen
import com.closet.features.wardrobe.EditClothingDestination
import com.closet.features.wardrobe.bulkWashScreen
import com.closet.shared.resources.Res
import com.closet.shared.resources.nav_chat
import com.closet.shared.resources.nav_closet
import com.closet.shared.resources.nav_journal
import com.closet.shared.resources.nav_outfits
import com.closet.shared.resources.nav_stats
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.closet.core.ui.resources.Res as UiRes

// ─── Bottom nav items ─────────────────────────────────────────────────────────

private data class TopLevelRoute(
    val destination: Any,
    val routeClass: KClass<*>,
    val label: StringResource,
    val icon: DrawableResource,
)

private val topLevelRoutes = listOf(
    TopLevelRoute(ClosetDestination(), ClosetDestination::class, Res.string.nav_closet, UiRes.drawable.ic_icon_coat_hanger),
    TopLevelRoute(OutfitsRoute, OutfitsRoute::class, Res.string.nav_outfits, UiRes.drawable.ic_icon_t_shirt),
    TopLevelRoute(JournalRoute(), JournalRoute::class, Res.string.nav_journal, UiRes.drawable.ic_icon_calendar_dots),
    TopLevelRoute(StatsRoute, StatsRoute::class, Res.string.nav_stats, UiRes.drawable.ic_icon_chart_bar),
    TopLevelRoute(ChatRoute, ChatRoute::class, Res.string.nav_chat, UiRes.drawable.ic_icon_chat_circle),
)

private fun NavDestination?.isTopLevel() =
    topLevelRoutes.any { this?.hasRoute(it.routeClass) == true }

// ─── Root ─────────────────────────────────────────────────────────────────────

/**
 * Shared root of the app: theme, root [Scaffold] with the bottom [NavigationBar] (shown only on
 * the top-level tabs) and the [NavHost] wiring every feature destination.
 *
 * Tab switches use saveState/restoreState so each tab preserves its own back stack state.
 *
 * @param pendingShortcut latest shortcut request, or null; [onShortcutConsumed] is called after it is routed.
 *   Each delivery is a new [ShortcutRequest], so a repeated shortcut is routed again.
 * @param onNavigateToBackup Settings' backup entry; backup is platform-specific and owned by the host.
 * @param platformDestinations extra destinations a platform registers inside the [NavHost].
 */
@Composable
fun App(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    pendingShortcut: StateFlow<ShortcutRequest?> = MutableStateFlow(null),
    onShortcutConsumed: () -> Unit = {},
    onNavigateToBackup: (NavHostController) -> Unit = {},
    platformDestinations: NavGraphBuilder.(NavHostController) -> Unit = {},
) {
    val preferencesRepository: PreferencesRepository = koinInject()
    val accent = preferencesRepository.getAccent()
        .collectAsStateWithLifecycle(initialValue = ClosetAccent.Amber)
    val dynamicColor = preferencesRepository.getDynamicColor()
        .collectAsStateWithLifecycle(initialValue = false)

    ClosetTheme(accent = accent.value, dynamicColor = dynamicColor.value) {
        AppContent(
            modifier = modifier,
            navController = navController,
            pendingShortcut = pendingShortcut,
            onShortcutConsumed = onShortcutConsumed,
            onNavigateToBackup = onNavigateToBackup,
            platformDestinations = platformDestinations,
        )
    }
}

@Composable
private fun AppContent(
    modifier: Modifier,
    navController: NavHostController,
    pendingShortcut: StateFlow<ShortcutRequest?>,
    onShortcutConsumed: () -> Unit,
    onNavigateToBackup: (NavHostController) -> Unit,
    platformDestinations: NavGraphBuilder.(NavHostController) -> Unit,
) {
    // Route to the correct destination when a shortcut arrives, then clear it so
    // rotation / recomposition doesn't re-navigate.
    val shortcut by pendingShortcut.collectAsStateWithLifecycle()

    LaunchedEffect(shortcut) {
        val pending = shortcut ?: return@LaunchedEffect
        navController.navigate(pending.shortcut.destination()) {
            popUpTo(navController.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
        onShortcutConsumed()
    }

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
            startDestination = ClosetDestination(),
            // Edge-to-edge + adjustResize: consume the bars' padding, then pad for the
            // keyboard so every screen shrinks above the IME (no gap under the bottom bar).
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            composable<ClosetDestination> {
                ClosetScreen(
                    onAddItemClick = { navController.navigate(AddClothingDestination()) },
                    onItemClick = { itemId -> navController.navigate(ClothingDetailDestination(itemId)) },
                    onSettingsClick = { navController.navigateToSettings() },
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

            bulkWashScreen(onBack = { navController.popBackStack() })

            outfitsScreen(
                navController = navController,
                onGetSuggestions = { navController.navigateToRecommendations() },
            )
            outfitBuilderScreen(navController)
            wardrobePickerScreen(navController)

            recommendationScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigateToLog = { itemIds ->
                    navController.navigate(OutfitBuilderDestination(preselectedItemIds = itemIds))
                },
                onNavigateToSettings = { navController.navigateToSettings() },
            )
            journalScreen()

            statsScreen(
                onItemClick = { itemId -> navController.navigate(ClothingDetailDestination(itemId)) }
            )

            settingsScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigateToBulkWash = { navController.navigate(BulkWashDestination) },
                onNavigateToAiSettings = { navController.navigateToAiSettings() },
                onNavigateToBackup = { onNavigateToBackup(navController) },
            )

            aiSettingsScreen(
                onNavigateUp = { navController.popBackStack() },
            )

            chatScreen(
                onNavigateToItem = { itemId -> navController.navigate(ClothingDetailDestination(itemId)) },
                onNavigateToRecommendations = { navController.navigateToRecommendations() },
                onNavigateToLog = { itemIds ->
                    navController.navigate(OutfitBuilderDestination(preselectedItemIds = itemIds))
                },
            )

            platformDestinations(navController)
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
                        painter = painterResource(topLevel.icon),
                        contentDescription = stringResource(topLevel.label)
                    )
                },
                label = { Text(stringResource(topLevel.label)) }
            )
        }
    }
}
