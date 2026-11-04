package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.ImageEntry
import dev.usbdroid.files.FilesystemTools
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun ImageInfoScreen(image: ImageEntry, state: AppState, model: AppViewModel, close: () -> Unit) {
 val info=state.imageInfo[image.id]
 var selected by rememberSaveable(image.id) { mutableStateOf<Int?>(null) }
 var repair by remember { mutableStateOf(false) }
 val part=info?.partitions?.firstOrNull { it.partition.number==selected } ?: info?.partitions?.firstOrNull()
 val busy=state.imageWorking(image.id)
 val mtp=state.folderShare?.folder?.trimEnd('/')
 val shared=mtp!=null && image.physicalPath?.let { it==mtp || it.startsWith(mtp+"/") }==true
 val detached=!shared && state.localImages.none { it.image==image.physicalPath } && state.usb.luns.none { it.file==image.physicalPath && it.file.isNotBlank() }
 val job=state.jobs.filter { it.kind=="FS_CHECK" && runCatching { val args=JSONObject(it.args); args.optString("image")==image.id && args.optInt("partition")==part?.partition?.number }.getOrDefault(false) }.maxByOrNull { it.createdAt }
 val result=job?.takeIf { it.state=="DONE" }?.let { runCatching { JSONObject(it.result) }.getOrNull() }
 val backupExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri!=null && job!=null) model.exportOriginal(job,uri) }
 LaunchedEffect(image.id,image.modified) { if(image.physicalPath!=null) model.inspectImage(image) }
 BackHandler(onBack=close)
 if(repair && part!=null) ImagePopup({ if(!busy) repair=false },{ Text(stringResource(R.string.fs_repair)) },{
  Text(stringResource(R.string.fs_repair_description,sizeText(image.size*2)))
 },{ BusyButton(stringResource(R.string.fs_repair),busy,{ repair=false; model.checkFilesystem(image,part.partition.number,true) },enabled=detached) },dismissButton={ TextButton({ repair=false },enabled=!busy) { Text(stringResource(R.string.cancel)) } })
 Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.image_information)) },navigationIcon={ IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,null) } },actions={ BusyIconButton(state.working("image-info:${image.id}"),{ model.inspectImage(image) },enabled=image.physicalPath!=null) { Icon(Icons.Rounded.Refresh,null) } }) }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(image.title,style=MaterialTheme.typography.titleLarge)
   Text(sizeText(image.size),style=MaterialTheme.typography.bodyMedium)
   if(info==null) { if(state.working("image-info:${image.id}")) LinearProgressIndicator(Modifier.fillMaxWidth()) else Text(stringResource(R.string.copy_required)) }
   info?.let { data ->
    Text(stringResource(R.string.image_layout)+": "+if(data.layout.table=="NONE") stringResource(R.string.layout_none) else if(data.layout.table=="UNKNOWN") stringResource(R.string.fs_unknown) else data.layout.table,style=MaterialTheme.typography.bodyMedium)
    data.partitions.forEach { item ->
     Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
      Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
       Text(if(item.partition.number==0) stringResource(R.string.fs_volume) else stringResource(R.string.partition_number,item.partition.number),style=MaterialTheme.typography.titleSmall)
       if(item.partition.name.isNotBlank()) Text(item.partition.name)
       Text(stringResource(R.string.filesystem_label)+": "+if(item.filesystem=="UNKNOWN") stringResource(R.string.fs_unknown) else item.filesystem)
       if(item.label.isNotBlank()) Text(stringResource(R.string.fs_label,item.label))
       Text(stringResource(R.string.fs_partition_size,sizeText(item.partition.bytes)))
       Text(stringResource(R.string.fs_free,item.freeBytes?.let { (if(item.estimated) "≈ " else "")+sizeText(it) } ?: stringResource(R.string.fs_free_mount)))
      }
     }
    }
    HorizontalDivider()
    Text(stringResource(R.string.fs_check),style=MaterialTheme.typography.titleMedium)
    if(data.partitions.size>1) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { data.partitions.forEach { item -> FilterChip(part==item,{ selected=item.partition.number },label={ Text(stringResource(R.string.partition_number,item.partition.number)) },enabled=!busy) } }
    if(!detached) Text(stringResource(R.string.fs_detach),style=MaterialTheme.typography.bodySmall)
    if(part?.filesystem=="Btrfs") Text(stringResource(R.string.fs_btrfs_readonly),style=MaterialTheme.typography.bodySmall)
    if(part?.filesystem=="NTFS") Text(stringResource(R.string.fs_ntfs_limited),style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     CardAction(stringResource(R.string.fs_check_button),Icons.Rounded.FactCheck,{ part?.let { model.checkFilesystem(image,it.partition.number,false) } },Modifier.weight(1f),enabled=!busy && detached && part?.filesystem in setOf("FAT","FAT32","exFAT","ext2","ext3","ext4","NTFS","Btrfs"))
     CardAction(stringResource(R.string.fs_repair),Icons.Rounded.Build,{ repair=true },Modifier.weight(1f),enabled=!busy && detached && part?.filesystem?.let(FilesystemTools::repairable)==true)
    }
    job?.takeIf { it.running }?.let { JobProgress(it) }
    result?.let { r ->
     Text(stringResource(if(r.optBoolean("clean")) R.string.fs_clean else R.string.fs_not_clean),color=if(r.optBoolean("clean")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
     Text(stringResource(R.string.fs_checked_at,java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(job?.createdAt ?: 0))),style=MaterialTheme.typography.bodySmall)
     var details by remember(job?.id) { mutableStateOf(false) }
     TextButton({ details=!details }) { Text(stringResource(R.string.error_details)) }
     if(details) Text(r.optString("details"),style=MaterialTheme.typography.bodySmall)
     if(r.optString("backup").isNotBlank()) TextButton({ backupExport.launch(image.title+"-backup.img") }) { Text(stringResource(R.string.fs_export_backup)) }
    }
   }
  }
 }
}
