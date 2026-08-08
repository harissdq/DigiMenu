package com.digimenu.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.customer.ui.screens.ConfirmationScreen
import com.digimenu.customer.ui.screens.LeadScreen
import com.digimenu.customer.ui.screens.MenuScreen
import com.digimenu.customer.ui.screens.QrScannerScreen
import com.digimenu.customer.ui.theme.DigiMenuTheme
import com.digimenu.customer.ui.viewmodel.CustomerStage
import com.digimenu.customer.ui.viewmodel.CustomerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigiMenuTheme {
                CustomerRoot()
            }
        }
    }
}

@Composable
private fun CustomerRoot(viewModel: CustomerViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val menu by viewModel.menu.collectAsStateWithLifecycle()

    when (ui.stage) {
        CustomerStage.SCANNER -> QrScannerScreen(
            message = ui.message,
            onQrScanned = viewModel::onQrScanned,
        )
        CustomerStage.LEAD_CAPTURE -> LeadScreen(
            tableLabel = ui.tableLabel,
            lead = ui.lead,
            message = ui.message,
            onNameChange = viewModel::onLeadNameChange,
            onPhoneChange = viewModel::onLeadPhoneChange,
            onContinue = viewModel::saveLead,
        )
        CustomerStage.MENU -> MenuScreen(
            menu = menu,
            cart = ui.cartItems,
            placing = ui.placing,
            message = ui.message,
            onAdd = viewModel::addToCart,
            onRemove = viewModel::removeFromCart,
            onPlaceOrder = viewModel::placeOrder,
        )
        CustomerStage.CONFIRMED -> ConfirmationScreen(
            tableLabel = ui.tableLabel,
            onRestart = viewModel::restart,
        )
    }
}
