package com.aarav.chatapplication.presentation.navigation


import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aarav.chatapplication.ui.theme.hankenGrotesk

@Composable
fun BottomNavigation(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route


    val navItems = listOf(NavItem.Chat, NavItem.CallHistory, NavItem.Profile)

    NavigationBar(
        tonalElevation = 4.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.shadow(24.dp)
    ) {
        navItems.forEachIndexed { index, item ->

            val isSelected = currentRoute?.startsWith(item.path) == true
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (currentRoute != item.path) {
                        navController.navigate(item.path) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                        }

                    }
                    Log.i("NAV", "BottomNavigationBar: $currentRoute, dest : ${item.path}")
                },
                label = {
                    Text(
                        item.name,
                        fontFamily = hankenGrotesk
                    )
                },
                icon = {
                    val icon = if (isSelected) item.filledIcon else item.icon

                    Image(
                        painter = painterResource(item.icon),
                        contentDescription = "nav icon",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}