package com.closet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.closet.features.wardrobe.*

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
                onBackClick = { navController.popBackStack() },
                onEditClick = { itemId ->
                    navController.navigate(EditClothingDestination(itemId))
                }
            )
        }

        composable<AddClothingDestination> {
            ClothingFormScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<EditClothingDestination> {
            ClothingFormScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
