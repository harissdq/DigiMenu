package com.digimenu.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.digimenu.manager.ui.screens.LoginScreen
import com.digimenu.manager.ui.screens.MenuScreen
import com.digimenu.manager.ui.screens.OrdersScreen
import com.digimenu.manager.ui.screens.QrCodesScreen
import com.digimenu.manager.ui.theme.DigiMenuTheme
import com.digimenu.manager.ui.viewmodel.ManagerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigiMenuTheme {
                ManagerRoot()
            }
        }
    }
}

private enum class ManagerTab(val label: String, val icon: ImageVector) {
    Menu("Menu", Icons.Filled.Restaurant),
    QrCodes("QR Codes", Icons.Filled.QrCode2),
    Orders("Orders", Icons.Filled.ShoppingCart),
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DigiMenu Manager") },
                actions = {
                    TextButton(onClick = viewModel::logout) { Text("Logout") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                ManagerTab.entries.forEachIndexed { index, tab ->
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
            when (ManagerTab.entries[tabIndex]) {
                ManagerTab.Menu -> MenuScreen()
                ManagerTab.QrCodes -> QrCodesScreen()
                ManagerTab.Orders -> OrdersScreen()
            }
        }
    }
}
