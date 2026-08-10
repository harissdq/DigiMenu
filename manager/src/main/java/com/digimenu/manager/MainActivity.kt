package com.digimenu.manager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.manager.ui.screens.AdminScreen
import com.digimenu.manager.ui.screens.LoginScreen
import com.digimenu.manager.ui.screens.MenuScreen
import com.digimenu.manager.ui.screens.OrdersScreen
import com.digimenu.manager.ui.screens.QrCodesScreen
import com.digimenu.manager.ui.screens.TablesScreen
import com.digimenu.manager.ui.theme.DigiMenuTheme
import com.digimenu.manager.ui.viewmodel.ManagerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            DigiMenuTheme {
                ManagerRoot()
            }
        }
    }

    /** Order notifications play a sound, so ask for the permission on Android 13+. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

private enum class ManagerTab(val label: String, val icon: ImageVector) {
    Menu("Menu", Icons.Filled.Restaurant),
    QrCodes("QR Codes", Icons.Filled.QrCode2),
    Orders("Orders", Icons.Filled.ShoppingCart),
    Tables("Tables", Icons.Filled.TableRestaurant),
    Admin("Admin", Icons.Filled.Settings),
}

@Composable
private fun ManagerRoot(viewModel: ManagerViewModel = hiltViewModel()) {
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    if (!loggedIn) {
        LoginScreen(viewModel = viewModel)
    } else {
        ManagerScaffold(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerScaffold(viewModel: ManagerViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val restaurantName by viewModel.restaurantName.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

    val tabs = buildList {
        add(ManagerTab.Menu)
        add(ManagerTab.QrCodes)
        add(ManagerTab.Orders)
        if (isAdmin) add(ManagerTab.Admin)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(restaurantName ?: "DigiMenu Manager") },
                actions = {
                    TextButton(onClick = viewModel::logout) { Text("Logout") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tabs.getOrElse(tabIndex) { ManagerTab.Menu }) {
                ManagerTab.Menu -> MenuScreen()
                ManagerTab.QrCodes -> QrCodesScreen()
                ManagerTab.Orders -> OrdersScreen()
                ManagerTab.Tables -> TablesScreen()
                ManagerTab.Admin -> AdminScreen()
            }
        }
    }
}
