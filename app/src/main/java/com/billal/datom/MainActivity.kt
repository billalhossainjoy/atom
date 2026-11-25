package com.billal.datom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.billal.datom.appwrite.AppwriteConfig
import com.billal.datom.appwrite.AppwriteSync
import com.billal.datom.appwrite.NetworkMonitor
import com.billal.datom.media.MediaImageStore
import com.billal.datom.sms.SmsMessage
import com.billal.datom.sms.SmsStore
import com.billal.datom.ui.theme.DAtomTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DAtomTheme {
                DAtomApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun DAtomApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var hasSmsPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasSmsPermission = grants[Manifest.permission.READ_SMS] == true
        if (hasSmsPermission) {
            SmsStore.loadInbox(context)
            scope.launch { AppwriteSync.syncAll(context) }
        }
    }

    LaunchedEffect(Unit) {
        NetworkMonitor.start(context)
        NetworkMonitor.online.collectLatest {
            if (AppwriteConfig.isConfigured() && hasSmsPermission) {
                AppwriteSync.syncAll(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasSmsPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
        } else {
            SmsStore.loadInbox(context)
            AppwriteSync.syncAll(context)
        }
    }

    val showNavSuite = AppDestinations.entries.any { it.route == currentRoute }

    val content: @Composable () -> Unit = {
        AppNavHost(navController = navController)
    }

    if (showNavSuite) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach { destination ->
                    item(
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { scaffoldPadding ->
                Box(Modifier.padding(scaffoldPadding)) { content() }
            }
        }
    } else {
        Scaffold(modifier = Modifier.fillMaxSize()) { scaffoldPadding ->
            Box(Modifier.padding(scaffoldPadding)) { content() }
        }
    }
}

enum class AppDestinations(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home),
    FAVORITES("favorites", "Favorites", Icons.Filled.Favorite),
    PROFILE("profile", "Profile", Icons.Filled.Person),
    GALLERY("gallery", "Gallery", Icons.Filled.PhotoLibrary),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME.route,
        modifier = modifier
    ) {
        composable(AppDestinations.HOME.route) {
            HomeScreen(
                onMessageClick = { (id) ->
                    navController.navigate("sms/$id")
                }
            )
        }
        composable(
            route = "sms/{smsId}",
            arguments = listOf(navArgument("smsId") { type = NavType.LongType })
        ) { entry ->
            val smsId = entry.arguments?.getLong("smsId") ?: -1L
            val messages by SmsStore.messages.collectAsState()
            val message = messages.firstOrNull { it.id == smsId }
            if (message != null) {
                SmsDetailScreen(
                    message = message,
                    onBack = { navController.popBackStack() }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text("Message not found")
                }
            }
        }
        composable(AppDestinations.FAVORITES.route) {
            PlaceholderScreen("Favorites")
        }
        composable(AppDestinations.PROFILE.route) {
            PlaceholderScreen("Profile")
        }
        composable(AppDestinations.GALLERY.route) {
            GalleryDestination(navController)
        }
        composable(
            route = "image/{imageId}",
            arguments = listOf(navArgument("imageId") { type = NavType.LongType })
        ) { entry ->
            val imageId = entry.arguments?.getLong("imageId") ?: -1L
            val images by MediaImageStore.images.collectAsState()
            val image = images.firstOrNull { it.id == imageId }
            if (image != null) {
                ImageDetailScreen(
                    image = image,
                    onBack = { navController.popBackStack() }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text("Image not found")
                }
            }
        }
        composable(AppDestinations.SETTINGS.route) {
            SettingsScreen()
        }
    }
}

@Composable
fun GalleryDestination(navController: NavHostController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            MediaImageStore.startObserving(context)
            MediaImageStore.load(context)
        }
    }
    GalleryScreen(onImageClick = { navController.navigate("image/${it.id}") })
}

@Composable
fun HomeScreen(
    onMessageClick: (SmsMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by SmsStore.messages.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Messages", style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (messages.isEmpty()) {
            Text(
                "No SMS yet. Grant SMS permission or send yourself a message.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.id }) { message ->
                    SmsCard(message = message) { onMessageClick(message) }
                }
            }
        }
    }
}

@Composable
fun SmsCard(message: SmsMessage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message.sender, style = MaterialTheme.typography.titleSmall)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(message.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SmsDetailScreen(
    message: SmsMessage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(message.sender, style = MaterialTheme.typography.headlineSmall)
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(message.date)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(message.body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text("Coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    DAtomTheme {
        HomeScreen(onMessageClick = {})
    }
}
