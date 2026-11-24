package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.AntigravityStudioTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanDim
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.viewmodel.IdeViewModel

private const val ROUTE_AGENT = "agent"
private const val ROUTE_WORKSPACE = "workspace"
private const val ROUTE_TERMINAL = "terminal"
private const val ROUTE_SETTINGS = "settings"

private data class IdeNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val IDE_DESTINATIONS = listOf(
    IdeNavDestination(
        route = ROUTE_AGENT,
        labelRes = R.string.nav_agent,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tab_agent"
    ),
    IdeNavDestination(
        route = ROUTE_WORKSPACE,
        labelRes = R.string.nav_workspace,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "nav_tab_workspace"
    ),
    IdeNavDestination(
        route = ROUTE_TERMINAL,
        labelRes = R.string.nav_terminal,
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal,
        testTag = "nav_tab_terminal"
    ),
    IdeNavDestination(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            AntigravityStudioTheme {
                AntigravityStudioApp()
            }
        }
    }
}

@Composable
private fun AntigravityStudioApp() {
    val context = LocalContext.current
    val ideViewModel: IdeViewModel = viewModel(
        factory = IdeViewModel.Factory(context.applicationContext)
    )
    val uiState by ideViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_AGENT

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        val useNavigationRail = maxWidth >= 700.dp

        if (useNavigationRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                NavigationRail(
                    containerColor = ObsidianSurface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    IDE_DESTINATIONS.forEach { destination ->
                        val selected = currentRoute == destination.route
                        val label = stringResource(destination.labelRes)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToDestination(navController, destination.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = label
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyanDim,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag(destination.testTag)
                        )
                    }
                }
                VerticalDivider(color = ObsidianBorder, thickness = 1.dp)
                IdeNavHost(
                    navController = navController,
                    viewModel = ideViewModel,
                    uiState = uiState,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                containerColor = ObsidianBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    androidx.compose.foundation.layout.Column {
                        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)
                        NavigationBar(
                            containerColor = ObsidianSurface,
                            tonalElevation = 0.dp
                        ) {
                            IDE_DESTINATIONS.forEach { destination ->
                                val selected = currentRoute == destination.route
                                val label = stringResource(destination.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToDestination(navController, destination.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ElectricCyan,
                                        selectedTextColor = ElectricCyan,
                                        indicatorColor = ElectricCyanDim,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag(destination.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                IdeNavHost(
                    navController = navController,
                    viewModel = ideViewModel,
                    uiState = uiState,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun IdeNavHost(
    navController: NavHostController,
    viewModel: IdeViewModel,
    uiState: com.example.model.IdeUiState,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_AGENT,
        modifier = modifier
    ) {
        composable(ROUTE_AGENT) {
            AgentScreen(
                uiState = uiState,
                onSendPrompt = viewModel::sendUserPrompt,
                onApproveTool = viewModel::approveToolCall,
                onRejectTool = viewModel::rejectToolCall,
                onClearChat = viewModel::clearChatHistory,
                onNavigateToSettings = {
                    navigateToDestination(navController, ROUTE_SETTINGS)
                }
            )
        }

        composable(ROUTE_WORKSPACE) {
            WorkspaceScreen(
                uiState = uiState,
                onPickSafFolder = viewModel::onSafFolderPicked,
                onUseInternalWorkspace = viewModel::switchToInternalWorkspace,
                onRefreshFiles = viewModel::refreshWorkspaceFiles,
                onOpenFile = viewModel::openFileInEditor,
                onCloseEditor = viewModel::closeEditor,
                onEditorContentChange = viewModel::updateEditorContent,
                onSaveFile = viewModel::saveCurrentFile,
                onCreateFile = viewModel::createNewFile,
                onRunInTermux = viewModel::runSelectedFileInTermux,
                onDismissNotice = viewModel::clearWorkspaceNotice
            )
        }

        composable(ROUTE_TERMINAL) {
            TerminalScreen(
                uiState = uiState,
                onRefreshStatus = viewModel::refreshTermuxStatus,
                onOpenTermux = viewModel::openTermuxApp,
                onRunCommand = viewModel::runTermuxCommand,
                onClearLogs = viewModel::clearTerminalLogs
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                uiState = uiState,
                onAddOrUpdateKey = viewModel::addOrUpdateApiKey,
                onSelectKey = viewModel::selectActiveApiKey,
                onDeleteKey = viewModel::deleteApiKey,
                onTestActiveKey = viewModel::testActiveApiKey,
                onSelectModel = viewModel::updateActiveModel,
                onSaveConfig = viewModel::saveAgentConfiguration,
                onDismissNotice = viewModel::clearSettingsNotice
            )
        }
    }
}

private fun navigateToDestination(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
