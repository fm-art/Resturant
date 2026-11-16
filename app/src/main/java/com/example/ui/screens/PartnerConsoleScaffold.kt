package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.model.PartnerTab
import com.example.model.StoreOrder
import com.example.model.ToastKind
import com.example.ui.StoreUiState
import com.example.ui.StoreViewModel
import com.example.ui.theme.BgDark
import com.example.ui.theme.BgInput
import com.example.ui.theme.BgSurface
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerSoft
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.InfoSky
import com.example.ui.theme.InfoSoft
import com.example.ui.theme.SunnyAmberInk
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.TextMain
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningSoft
import com.example.util.EarningsCalculator

@Composable
fun PartnerConsoleScaffold(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (state.selectedTab != PartnerTab.ORDERS) {
            viewModel.selectTab(PartnerTab.ORDERS)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTime = now
                viewModel.showToast("Press BACK again to exit", ToastKind.INFO)
            }
        }
    }

    val pendingInventoryCount = remember(state.products) {
        state.products.count { !it.adminApproved || it.inventoryFlagged }
    }

    val navItems = remember(state.activeOrders.size) {
        listOf(
            Triple(PartnerTab.ORDERS, "Orders", Icons.Default.Inventory2),
            Triple(PartnerTab.MENU, "Products", Icons.Default.ShoppingBasket),
            Triple(PartnerTab.INVENTORY, "Inventory", Icons.AutoMirrored.Filled.FactCheck),
            Triple(PartnerTab.REPORTS, "Wallet", Icons.Default.AccountBalanceWallet),
            Triple(PartnerTab.PROFILE, "Store", Icons.Default.Settings)
        )
    }

    if (state.showAdminCommissionHub) {
        AdminCommissionScreen(
            currentStoreId = state.uid,
            currentStoreName = state.storeProfile.name,
            commissionRules = state.commissionRules,
            products = state.products,
            availableCategories = state.adminSettings.customCategories,
            allStores = state.allStores,
            storeReports = state.storeReports,
            ledgerEntries = state.allLedgerEntries.values.sortedByDescending { it.createdAt },
            onBack = { viewModel.closeAdminCommissionHub() },
            onSaveGlobal = { pct, enabled, allowLoss ->
                viewModel.saveGlobalCommissionSettings(pct, enabled, allowLoss)
            },
            onSaveStoreRule = { storeId, storeName, pct, enabled ->
                viewModel.saveStoreCommissionRule(storeId, storeName, pct, enabled)
            },
            onDeleteStoreRule = { storeId -> viewModel.deleteStoreCommissionRule(storeId) },
            onSaveCategoryRule = { catId, catName, pct, enabled ->
                viewModel.saveCategoryCommissionRule(catId, catName, pct, enabled)
            },
            onDeleteCategoryRule = { catId -> viewModel.deleteCategoryCommissionRule(catId) },
            onSaveProductRule = { prodId, storeId, prodName, pct, enabled ->
                viewModel.saveProductCommissionRule(prodId, storeId, prodName, pct, enabled)
            },
            onDeleteProductRule = { prodId -> viewModel.deleteProductCommissionRule(prodId) }
        )
        return
    }

    var reportsSubTab by remember { mutableIntStateOf(0) }

    if (state.showEarningsDashboard) {
        EarningsDashboardScreen(
            storeProfile = state.storeProfile,
            adminSettings = state.adminSettings,
            orderLedgerEntries = state.orderLedgerEntries,
            payouts = state.payouts,
            onRequestPayout = { viewModel.requestPayout() },
            onOpenWalletPayouts = {
                viewModel.closeEarningsDashboard()
                reportsSubTab = 1
                viewModel.selectTab(PartnerTab.REPORTS)
            },
            onBack = { viewModel.closeEarningsDashboard() }
        )
        return
    }

    if (state.showSecurityShield) {
        SecurityShieldScreen(
            storeProfile = state.storeProfile,
            securityConfig = state.securityConfig,
            securityPosture = state.securityPosture,
            orders = state.allOrders,
            riskAssessments = state.orderRiskAssessments,
            onBack = { viewModel.closeSecurityShield() },
            onUpdateConfig = { transform -> viewModel.updateSecurityConfig(transform) },
            onTogglePanicSafeMode = { viewModel.togglePanicSafeMode() },
            onMarkOrderSafe = { orderId -> viewModel.markOrderVerifiedSafe(orderId) },
            onReportSpamOrder = { order, assessment -> viewModel.reportAndRejectSpamOrder(order, assessment) }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = BgDark,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                PartnerTopBarAndAlerts(
                    state = state,
                    onRingingClick = { viewModel.selectTab(PartnerTab.ORDERS) },
                    onToggleDuty = { viewModel.toggleStoreDuty() },
                    onToggleSound = { viewModel.toggleSound() },
                    onOpenEarningsDashboard = { viewModel.openEarningsDashboard() },
                    onOpenSecurityShield = { viewModel.openSecurityShield() },
                    onOpenAdminCommission = { viewModel.openAdminCommissionHub() },
                    onLogout = {
                        scope.launch {
                            withContext(NonCancellable) {
                                runCatching {
                                    CredentialManager.create(context)
                                        .clearCredentialState(ClearCredentialStateRequest())
                                }
                            }
                            viewModel.logout()
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    Column {
                        HorizontalDivider(color = BorderSlate)
                        NavigationBar(
                            containerColor = BgSurface,
                            tonalElevation = 0.dp
                        ) {
                            navItems.forEach { (tab, label, icon) ->
                                val selected = state.selectedTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        if (tab == PartnerTab.ORDERS && state.activeOrders.isNotEmpty()) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = EmeraldPrimary, contentColor = Color.White) {
                                                        Text(state.activeOrders.size.toString())
                                                    }
                                                }
                                            ) {
                                                Icon(icon, contentDescription = label)
                                            }
                                        } else if (tab == PartnerTab.INVENTORY && pendingInventoryCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = SunnyYellow, contentColor = TextMain) {
                                                        Text(pendingInventoryCount.toString())
                                                    }
                                                }
                                            ) {
                                                Icon(icon, contentDescription = label)
                                            }
                                        } else {
                                            Icon(icon, contentDescription = label)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldSoft,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = BgSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { (tab, label, icon) ->
                            val selected = state.selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    if (tab == PartnerTab.ORDERS && state.activeOrders.isNotEmpty()) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = EmeraldPrimary, contentColor = Color.White) {
                                                    Text(state.activeOrders.size.toString())
                                                }
                                            }
                                        ) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else if (tab == PartnerTab.INVENTORY && pendingInventoryCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = SunnyYellow, contentColor = TextMain) {
                                                    Text(pendingInventoryCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = label)
                                    }
                                },
                                label = {
                                    Text(label, fontWeight = FontWeight.Bold)
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldPrimary,
                                    selectedTextColor = EmeraldPrimary,
                                    indicatorColor = EmeraldSoft,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (state.selectedTab) {
                        PartnerTab.ORDERS -> OrdersTabScreen(
                            activeOrders = state.activeOrders,
                            storeProfile = state.storeProfile,
                            deliveryRiders = state.deliveryRiders,
                            customers = state.customers,
                            orderRiskAssessments = state.orderRiskAssessments,
                            onUpdateStatus = { order: StoreOrder, status: String ->
                                viewModel.setOrderStatus(order, status)
                            },
                            onRejectOrder = { orderId, reason ->
                                viewModel.rejectOrder(orderId, reason)
                            },
                            onReportSpam = { order, assessment ->
                                viewModel.reportAndRejectSpamOrder(order, assessment)
                            },
                            onMarkOrderSafe = { orderId ->
                                viewModel.markOrderVerifiedSafe(orderId)
                            },
                            onToast = { msg, kind -> viewModel.showToast(msg, kind) }
                        )

                        PartnerTab.MENU -> ProductsTabScreen(
                            storeId = state.uid,
                            products = state.products,
                            searchQuery = state.productSearchQuery,
                            adminSettings = state.adminSettings,
                            commissionRules = state.commissionRules,
                            salesStats = state.productSalesStats,
                            onSearchChange = { viewModel.updateProductSearch(it) },
                            onToggleStock = { viewModel.toggleProductStock(it) },
                            onSaveProduct = { id, name, cost, sell, commPct, ruleSource, mrp, cat, onSale, saleTag, kw, desc, img, avail, onDone ->
                                viewModel.saveProduct(
                                    id, name, cost, sell, commPct, ruleSource, mrp, cat,
                                    onSale, saleTag, kw, desc, img, avail, onDone
                                )
                            },
                            onDeleteProduct = { viewModel.deleteProduct(it) },
                            onQuickSeed = { viewModel.quickSeedProducts() },
                            onToast = { msg, kind -> viewModel.showToast(msg, kind) }
                        )

                        PartnerTab.INVENTORY -> InventoryScreen(
                            products = state.products,
                            onToggleApprove = { viewModel.toggleApproveInventory(it) },
                            onToggleFlag = { item, reason -> viewModel.toggleFlagInventory(item, reason) },
                            onUpdateStockQty = { item, newQty -> viewModel.updateStockQty(item, newQty) },
                            onToggleStock = { viewModel.toggleProductStock(it) }
                        )

                        PartnerTab.REPORTS -> Column(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                color = BgSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        0 to "Earnings Dashboard",
                                        1 to "Payouts & CSV"
                                    ).forEach { (idx, title) ->
                                        val selected = reportsSubTab == idx
                                        Surface(
                                            color = if (selected) FreshGreen else BgInput,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) FreshGreen else BorderSlate
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { reportsSubTab = idx }
                                                .testTag("reports_subtab_$idx")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 15.dp)
                                            ) {
                                                Text(
                                                    text = title,
                                                    color = if (selected) Color.White else TextMain,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = BorderSlate)

                            Box(modifier = Modifier.weight(1f)) {
                                if (reportsSubTab == 0) {
                                    EarningsDashboardScreen(
                                        storeProfile = state.storeProfile,
                                        adminSettings = state.adminSettings,
                                        orderLedgerEntries = state.orderLedgerEntries,
                                        payouts = state.payouts,
                                        onRequestPayout = { viewModel.requestPayout() },
                                        onOpenWalletPayouts = { reportsSubTab = 1 },
                                        onBack = null
                                    )
                                } else {
                                    WalletTabScreen(
                                        storeProfile = state.storeProfile,
                                        adminSettings = state.adminSettings,
                                        deliveredCount = state.deliveredCount,
                                        grossRevenue = state.grossRevenue,
                                        totalChutkiCommission = state.totalChutkiCommission,
                                        netStoreShare = state.netStoreShare,
                                        totalEstimatedProfit = state.totalEstimatedProfit,
                                        totalSettled = state.totalSettled,
                                        pendingPayoutBalance = state.pendingPayoutBalance,
                                        payouts = state.payouts,
                                        allOrders = state.allOrders,
                                        orderLedgerEntries = state.orderLedgerEntries,
                                        onRequestPayout = { viewModel.requestPayout() },
                                        onToast = { msg, kind -> viewModel.showToast(msg, kind) }
                                    )
                                }
                            }
                        }

                        PartnerTab.PROFILE -> ProfileTabScreen(
                            storeProfile = state.storeProfile,
                            adminSettings = state.adminSettings,
                            commissionRules = state.commissionRules,
                            feedbacks = state.feedbacks,
                            securityPosture = state.securityPosture,
                            securityConfig = state.securityConfig,
                            onOpenSecurityShield = { viewModel.openSecurityShield() },
                            onOpenAdminCommission = { viewModel.openAdminCommissionHub() },
                            onSaveProfile = { name, upi, cuisine, phone, discPct, discMin, addr, hrs, img, lat, lng ->
                                viewModel.saveStoreProfile(
                                    name, upi, cuisine, phone, discPct, discMin, addr, hrs, img, lat, lng
                                )
                            },
                            onSubmitFeedback = { subj, msg, onDone ->
                                viewModel.submitFeedback(subj, msg, onDone)
                            },
                            onToast = { msg, kind -> viewModel.showToast(msg, kind) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerTopBarAndAlerts(
    state: StoreUiState,
    onRingingClick: () -> Unit,
    onToggleDuty: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenEarningsDashboard: () -> Unit,
    onOpenSecurityShield: () -> Unit,
    onOpenAdminCommission: () -> Unit,
    onLogout: () -> Unit
) {
    Surface(
        color = BgSurface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Suspended Banner
            if (state.storeProfile.suspended) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerRed.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Store suspended by Admin — order processing locked.",
                        color = DangerSoft,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Store-Specific Admin Note
            if (state.storeProfile.adminNote.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SunnyYellow.copy(alpha = 0.24f))
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Admin Note: ${state.storeProfile.adminNote}",
                        color = WarningSoft,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Global Broadcast Banner
            if (state.adminSettings.storeAnnouncement.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InfoSky.copy(alpha = 0.10f))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = InfoSoft,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.adminSettings.storeAnnouncement,
                        color = InfoSoft,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Main Header Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(EmeraldSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Storefront,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = state.storeProfile.name.ifBlank { "Gossaigaon Bazar" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMain,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    state.storeProfile.suspended -> "Account Suspended"
                                    state.storeProfile.approved -> "Verified Store Partner"
                                    else -> "Pending Admin Verification"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    state.storeProfile.suspended -> DangerSoft
                                    state.storeProfile.approved -> FreshGreen
                                    else -> WarningSoft
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Log Out",
                            tint = TextMuted
                        )
                    }
                }

                // Compact horizontal action strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.ringingOrderCount > 0) {
                        val pulse = rememberInfiniteTransition(label = "ring_pulse")
                        val scale by pulse.animateFloat(
                            initialValue = 0.96f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(550),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "ring_scale"
                        )
                        Surface(
                            color = DangerRed,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .scale(scale)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onRingingClick() }
                                .testTag("ringing_alert_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${state.ringingOrderCount} NEW ORDER${if (state.ringingOrderCount > 1) "S" else ""}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Duty Pill
                    Surface(
                        color = if (state.storeProfile.isOpen) EmeraldSoft else BgInput,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            if (state.storeProfile.isOpen) EmeraldPrimary.copy(alpha = 0.45f) else BorderSlate
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onToggleDuty() }
                            .testTag("duty_toggle_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.storeProfile.isOpen) EmeraldPrimary else DangerRed)
                            )
                            Text(
                                text = if (state.storeProfile.isOpen) "Store Open" else "Store Closed",
                                color = if (state.storeProfile.isOpen) Color(0xFF14532D) else TextMain,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sound Toggle Pill
                    Surface(
                        color = BgInput,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onToggleSound() }
                            .testTag("sound_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (state.soundEnabled) {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                },
                                contentDescription = "Toggle order sound",
                                tint = if (state.soundEnabled) EmeraldPrimary else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (state.soundEnabled) "Sound On" else "Muted",
                                color = TextMain,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Security Shield Quick Pill
                    val hasRiskAlerts = state.securityPosture.flaggedOrderCount > 0 || state.securityConfig.panicSafeModeActive
                    Surface(
                        color = if (hasRiskAlerts) DangerRed.copy(alpha = 0.14f) else EmeraldSoft,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hasRiskAlerts) DangerRed.copy(alpha = 0.55f) else EmeraldPrimary.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenSecurityShield() }
                            .testTag("open_security_shield_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Open Security Shield",
                                tint = if (hasRiskAlerts) DangerSoft else EmeraldPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = when {
                                    state.securityConfig.panicSafeModeActive -> "Panic Lock"
                                    state.securityPosture.flaggedOrderCount > 0 -> "${state.securityPosture.flaggedOrderCount} Risk Alert"
                                    else -> "Shield ${state.securityPosture.overallScore}%"
                                },
                                color = if (hasRiskAlerts) DangerSoft else Color(0xFF14532D),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Earnings Dashboard Quick Pill
                    Surface(
                        color = EmeraldSoft,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenEarningsDashboard() }
                            .testTag("open_earnings_dashboard_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Open Earnings Dashboard",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Earnings ${EarningsCalculator.formatRupees(state.netStoreShare)}",
                                color = Color(0xFF14532D),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Admin Commission Management Pill
                    val resolvedStoreComm = remember(state.uid, state.commissionRules) {
                        EarningsCalculator.resolveCommission(null, state.uid, null, state.commissionRules)
                    }
                    Surface(
                        color = SunnyYellow.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, SunnyYellow),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenAdminCommission() }
                            .testTag("admin_commission_hub_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Commission Management",
                                tint = SunnyAmberInk,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Comm ${EarningsCalculator.formatPercent(resolvedStoreComm.percent)}",
                                color = SunnyAmberInk,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderSlate)
        }
    }
}
