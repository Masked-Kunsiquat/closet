package com.closet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.closet.features.wardrobe.ClosetDestination
import com.closet.features.wardrobe.ClosetScreen
import com.closet.features.wardrobe.ClothingDetailDestination

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
                onItemClick = { itemId ->
                    navController.navigate(ClothingDetailDestination(itemId))
                }
            )
        }
        
        composable<ClothingDetailDestination> { 
            // TODO: Implement ClothingDetailScreen
        }
    }
}
