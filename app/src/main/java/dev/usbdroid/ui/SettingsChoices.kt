package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import dev.usbdroid.R

@Composable internal fun languageChoices() = listOf("system" to stringResource(R.string.language_system), "ru" to "Русский", "en" to "English")

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SettingsChoice(title: String, icon: ImageVector, value: String, choices: List<Pair<String, String>>, enabled: Boolean, segmented: Boolean = false, change: (String) -> Unit) {
 var expanded by remember { mutableStateOf(false) }
 if(!segmented) {
  val rotation by animateFloatAsState(if(expanded) 180f else 0f, label = "language-chevron")
  Column {
   ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), headlineContent = { Text(title) }, supportingContent = { Text(choices.firstOrNull { it.first == value }?.second.orEmpty()) }, leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation)) }, modifier = Modifier.clickable(enabled = enabled) { expanded = !expanded })
   AnimatedVisibility(expanded, enter = cardExpand, exit = cardCollapse) { Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
    choices.forEach { (key, label) -> ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), headlineContent = { Text(label) }, leadingContent = { RadioButton(value == key, null) }, modifier = Modifier.clickable(enabled = enabled) { expanded = false; if(value != key) change(key) }) }
   } }
  }
  return
 }
 Column {
  ListItem(headlineContent = { Text(title) }, leadingContent = { Icon(icon, null) })
  Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
   SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
    choices.forEachIndexed { index, (key, label) -> SegmentedButton(selected = value == key, onClick = { if(value != key) change(key) }, enabled = enabled, shape = SegmentedButtonDefaults.itemShape(index, choices.size), icon = {}, modifier = Modifier.recordThemeTouch()) { Text(if(key == "system") stringResource(R.string.ui_92) else label) } }
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SetupLanguageScreen(language: String, saving: Boolean, advancing: Boolean, choose: (String) -> Unit, next: () -> Unit, close: (() -> Unit)?) {
 if(close != null) BackHandler(onBack = close)
 Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }, navigationIcon = { if(close != null) IconButton(close) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.create_back)) } }) }, bottomBar = {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) { BusyButton(stringResource(R.string.setup_next), advancing, next, Modifier.fillMaxWidth(), enabled = !saving) } }
 }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
   SetupHero(-1, Icons.Rounded.Translate, busy = saving || advancing)
   Text(stringResource(R.string.setup_language_title), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
   Text(stringResource(R.string.setup_language_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) { Column {
    languageChoices().forEach { (code, label) -> ListItem(headlineContent = { Text(label) }, leadingContent = { RadioButton(language == code, null) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.clickable(enabled = !saving) { if(language != code) choose(code) }) }
   } }
  }
 }
}
