package com.meshtop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.ConnectionSettings
import com.meshtop.data.MonitorUiState
import com.meshtop.ui.theme.*
import kotlinx.coroutines.launch

private val TAB_TITLES = listOf("", "Msgs", "Gates", "Nodes", "Pkts", "TR", "Relay")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MonitorUiState,
    settings: ConnectionSettings,
    showSettings: Boolean,
    hideGateways: Boolean,
    hideMyNodes: Boolean,
    getRelayName: (Int) -> String,
    onToggleSettings: () -> Unit,
    onHideSettings: () -> Unit,
    onSaveSettings: (ConnectionSettings) -> Unit,
    onReconnect: () -> Unit,
    onClearStorage: () -> Unit,
    onHideGatewaysChange: (Boolean) -> Unit,
    onHideMyNodesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = showSettings) { onHideSettings() }

    if (showSettings) {
        SettingsScreen(
            settings = settings,
            onSave = onSaveSettings,
            onReconnect = onReconnect,
            onClearStorage = onClearStorage,
            onDismiss = onHideSettings,
            modifier = modifier,
        )
        return
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TAB_TITLES.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Stats header (always visible, includes filter toggles)
        StatsHeader(
            state = state,
            hideGateways = hideGateways,
            hideMyNodes = hideMyNodes,
            onHideGatewaysChange = onHideGatewaysChange,
            onHideMyNodesChange = onHideMyNodesChange,
        )

        // Tab row
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = SurfaceCard,
            contentColor = Color(0xFFE0E0E0),
            edgePadding = 0.dp,
            divider = { HorizontalDivider(color = Color(0xFF333355)) },
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = FirstHearerCyan,
                    )
                }
            },
        ) {
            TAB_TITLES.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                val color by animateColorAsState(
                    targetValue = if (selected) FirstHearerCyan else TextDim,
                    label = "tabColor"
                )
                Tab(
                    selected = selected,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.height(36.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == 0) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = "Summary",
                                tint = color,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                text = title,
                                fontFamily = Mono,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                color = color,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        // Tab pages (no swipe - use tab bar to navigate)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) { page ->
            when (page) {
                0 -> SummaryScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes)
                1 -> MessagesScreen(state = state, getRelayName = getRelayName, hideGateways = hideGateways, hideMyNodes = hideMyNodes)
                2 -> GatewaysScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes)
                3 -> MyNodesScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes)
                4 -> PacketsScreen(state = state, getRelayName = getRelayName, hideGateways = hideGateways, hideMyNodes = hideMyNodes)
                5 -> TracerouteScreen(state = state)
                6 -> RelayNodesScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes)
            }
        }
    }
}
