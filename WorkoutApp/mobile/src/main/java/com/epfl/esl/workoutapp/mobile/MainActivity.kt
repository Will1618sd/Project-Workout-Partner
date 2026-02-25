package com.epfl.esl.workoutapp.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.epfl.esl.workoutapp.mobile.ui.theme.WorkoutAppTheme
import com.epfl.esl.workoutapp.mobile.service.WearController
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import com.epfl.esl.workoutapp.R
import com.google.android.gms.wearable.MessageClient

class MainActivity : ComponentActivity() {

    private lateinit var dataClient: DataClient
    private lateinit var messageClient: MessageClient
    private var username by mutableStateOf("")
    private var imageUri by mutableStateOf<Uri?>(null)
    private var uriString by mutableStateOf("")

    private var userKey by mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataClient = Wearable.getDataClient(this)
        messageClient = Wearable.getMessageClient(this)

        enableEdgeToEdge()
        setContent {
            WorkoutAppTheme {
                val navController = rememberNavController()

                var shouldShowBottomMenu by remember { mutableStateOf(false) }

                val context = LocalContext.current
                val wearController = remember { WearController(context) }

                Surface {
                        Scaffold(
                            topBar = {
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentRoute = navBackStackEntry?.destination?.route

                                TopAppBar(
                                    title = {
                                        Text(text = stringResource(id = R.string.app_name))
                                    },
                                    actions = {
                                        if (currentRoute != "login") {
                                            IconButton(onClick = { navController.navigate("settings") }) {
                                                Icon(
                                                    Icons.Filled.Settings,
                                                    contentDescription = getString(R.string.settings_icon)
                                                )
                                            }
                                        }
                                    }
                                )
                            },

                            bottomBar = {
                                if (shouldShowBottomMenu) {
                                    NavigationBar {
                                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                                        val currentRoute = navBackStackEntry?.destination?.route

                                        NavigationBarItem(
                                            selected = currentRoute == "homePage",
                                            onClick = {
                                                navController.navigate("homePage")
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Home,
                                                    contentDescription = getString(
                                                        R.string.home_page_content_description
                                                    )
                                                )
                                            },
                                            label = { Text(getString(R.string.home_page_navigation_label)) }
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute?.startsWith("newActivity") ?: false,
                                            onClick = {
                                                navController.navigate("newActivity")
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = getString(
                                                        R.string.new_activity_content_description
                                                    )
                                                )
                                            },
                                            label = {
                                                Text(getString(R.string.new_activity_navigation_label))
                                            }
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute == "profilePage",
                                            onClick = {
                                                navController.navigate("profilePage")
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = getString(
                                                        R.string.profile_page_content_description
                                                    )
                                                )
                                            },
                                            label = {
                                                Text(getString(R.string.profile_page_navigation_label))
                                            }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->

                            AppNavGraph(
                                navController = navController,
                                dataClient = dataClient,
                                messageClient = messageClient,
                                wearController = wearController,
                                userKey = userKey,
                                onUserKeyChange = { userKey = it },
                                onBottomBarVisible = { shouldShowBottomMenu = it },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                }
            }
        }
    }
}