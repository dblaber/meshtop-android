package com.meshtop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.ConnectionSettings
import com.meshtop.data.MonitorUiState
import com.meshtop.ui.theme.*
import kotlinx.coroutines.launch

private val TAB_TITLES = listOf("", "Msgs", "Gates", "Nodes", "Pkts", "TR", "Relay")

@Composable
private fun SearchQueryBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = TextDim,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "search nodes, types, gateways...",
                    color = TextDim,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color(0xFFE0E0E0), fontFamily = Mono, fontSize = 12.sp),
                singleLine = true,
                cursorBrush = SolidColor(FirstHearerCyan),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = TextDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
    HorizontalDivider(color = Color(0xFF333355))
}

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
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(pagerState.currentPage) { searchQuery = "" }

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

        // Search bar (hidden on Summary tab)
        if (pagerState.currentPage != 0) {
            SearchQueryBar(query = searchQuery, onQueryChange = { searchQuery = it })
        }

        // Tab pages (no swipe - use tab bar to navigate)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) { page ->
            when (page) {
                0 -> SummaryScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes)
                1 -> MessagesScreen(state = state, getRelayName = getRelayName, hideGateways = hideGateways, hideMyNodes = hideMyNodes, searchQuery = searchQuery)
                2 -> GatewaysScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes, searchQuery = searchQuery)
                3 -> MyNodesScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes, searchQuery = searchQuery)
                4 -> PacketsScreen(state = state, getRelayName = getRelayName, hideGateways = hideGateways, hideMyNodes = hideMyNodes, searchQuery = searchQuery)
                5 -> TracerouteScreen(state = state, searchQuery = searchQuery)
                6 -> RelayNodesScreen(state = state, hideGateways = hideGateways, hideMyNodes = hideMyNodes, searchQuery = searchQuery)
            }
        }
    }
}
