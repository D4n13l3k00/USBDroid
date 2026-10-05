package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.usbdroid.BuildConfig
import dev.usbdroid.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AboutScreen(close: () -> Unit, modifier: Modifier = Modifier) {
 val uriHandler = LocalUriHandler.current
 val repository = stringResource(R.string.github_repository_url)
 var licensesExpanded by rememberSaveable { mutableStateOf(false) }
 val chevron by animateFloatAsState(if(licensesExpanded) 180f else 0f, label = "about-licenses")
 BackHandler(onBack = close)
 Scaffold(modifier.fillMaxSize(), topBar = {
  TopAppBar(title = { Text(stringResource(R.string.about_title)) }, navigationIcon = {
   IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.create_back)) }
  })
 }) { padding ->
  Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
   Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
     Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)))
     Text("USBDroid", style = MaterialTheme.typography.headlineLarge)
     Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
      Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
     }
     Text(stringResource(R.string.about_description), Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
     UpdateCard()
     Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
      ListItem(modifier = Modifier.clickable { uriHandler.openUri(repository) }.padding(vertical = 4.dp), headlineContent = { Text(stringResource(R.string.github_repository)) }, supportingContent = { Text("D4n13l3k00/USBDroid") }, leadingContent = { Icon(Icons.Rounded.Code, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
     }
     Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
      Column {
       ListItem(modifier = Modifier.clickable { licensesExpanded = !licensesExpanded }.padding(vertical = 4.dp), headlineContent = { Text(stringResource(R.string.about_license)) }, supportingContent = { Text("GPL-3.0-or-later") }, leadingContent = { Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
       AnimatedVisibility(licensesExpanded, enter = cardExpand, exit = cardCollapse) {
        Text(stringResource(R.string.about_licenses_details), Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
       }
      }
     }
    }
   }
  }
 }
}
