package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.screens.*
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Root surface container
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberBlack
                ) {
                    TerminalAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun TerminalAppContent(viewModel: MainViewModel) {
    // 1. Observe state streams
    val assetsList by viewModel.assets.collectAsState()
    val transactionsList by viewModel.transactions.collectAsState()
    val userProfile by viewModel.profile.collectAsState()
    val lessonProgressList by viewModel.lessonProgress.collectAsState()
    val marketDataMap by viewModel.marketState.collectAsState()
    val selectedCoinSymbol by viewModel.selectedCoinSymbol.collectAsState()
    val lessonsList by viewModel.currentLessonsList.collectAsState()

    val coinsList = remember(marketDataMap) {
        marketDataMap.values.toList().sortedByDescending { it.volume24h }
    }

    // 2. Navigation tabs state observed from ViewModel
    val activeTab by viewModel.activeTab.collectAsState()

    // 3. Responsive Screen configuration
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTabletLayout = screenWidthDp >= 600

    val navItems = listOf(
        Triple("Market", Icons.Default.TrendingUp, "nav_market"),
        Triple("Vault", Icons.Default.AccountBalanceWallet, "nav_vault"),
        Triple("Trade", Icons.Default.CurrencyExchange, "nav_trade"),
        Triple("AI Assistant", Icons.Default.SmartToy, "nav_ai"),
        Triple("Academy", Icons.Default.School, "nav_academy")
    )

    if (isTabletLayout) {
        // --- Desktop & Tablet Landscape: Side Navigation rail ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            NavigationRail(
                containerColor = HeaderBg,
                contentColor = TextMuted,
                modifier = Modifier.width(96.dp)
            ) {
                // Header Brand Logo
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(NeonCyan.copy(alpha = 0.15f), MaterialTheme.shapes.medium),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        "NX",
                        color = NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))

                // Navigation items loop
                navItems.forEachIndexed { index, (label, icon, tag) ->
                    NavigationRailItem(
                        selected = activeTab == index,
                        onClick = { viewModel.activeTab.value = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = CyberBlack,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag(tag)
                    )
                }
            }

            // Separator lane
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(SlateSeparator)
            )

            // Dynamic Right Pane Content Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(CyberBlack)
            ) {
                when (activeTab) {
                    0 -> DashboardPanel(viewModel, coinsList, selectedCoinSymbol)
                    1 -> WalletPanel(viewModel, assetsList, userProfile, transactionsList)
                    2 -> TradePanel(viewModel, userProfile, coinsList, selectedCoinSymbol)
                    3 -> CoachPanel(viewModel, selectedCoinSymbol, userProfile)
                    4 -> AcademyPanel(viewModel, userProfile, lessonProgressList, lessonsList)
                }
            }
        }
    } else {
        // --- Mobile Portrait: Standard M3 Scaffold ---
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = HeaderBg,
                    tonalElevation = 8.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    navItems.forEachIndexed { index, (label, icon, tag) ->
                        NavigationBarItem(
                            selected = activeTab == index,
                            onClick = { viewModel.activeTab.value = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberBlack,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag(tag)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyberBlack)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                when (activeTab) {
                    0 -> DashboardPanel(viewModel, coinsList, selectedCoinSymbol)
                    1 -> WalletPanel(viewModel, assetsList, userProfile, transactionsList)
                    2 -> TradePanel(viewModel, userProfile, coinsList, selectedCoinSymbol)
                    3 -> CoachPanel(viewModel, selectedCoinSymbol, userProfile)
                    4 -> AcademyPanel(viewModel, userProfile, lessonProgressList, lessonsList)
                }
            }
        }
    }
}
