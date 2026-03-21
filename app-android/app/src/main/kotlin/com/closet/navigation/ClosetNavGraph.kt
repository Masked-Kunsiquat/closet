package com.closet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.closet.features.wardrobe.*
import com.closet.features.outfits.outfitsScreen
import com.closet.features.outfits.outfitBuilderScreen
import com.closet.features.outfits.wardrobePickerScreen
import com.closet.features.outfits.OutfitsRoute

@Composable
fun ClosetNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = ClosetDestination,
        modifier = modifier
    ) {
        composable<ClosetDestination> {
            ClosetScreen(
                onAddItemClick = {
                    navController.navigate(AddClothingDestination)
                },
                onItemClick = { itemId ->
                    navController.navigate(ClothingDetailDestination(itemId))
                }
            )
        }
        
        composable<ClothingDetailDestination> { 
            ClothingDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { itemId ->
                    navController.navigate(EditClothingDestination(itemId))
                }
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
    }
}
