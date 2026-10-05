package dev.usbdroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable fun CardAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accented: Boolean = false) {
 OutlinedButton(onClick, modifier.heightIn(min = 40.dp), enabled = enabled, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), border = BorderStroke(1.dp, if(accented && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
  Icon(icon, null, Modifier.size(18.dp))
  Spacer(Modifier.width(6.dp))
  Text(label, style = MaterialTheme.typography.labelMedium)
 }
}
