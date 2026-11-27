package com.toolateforteddy.kidremote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toolateforteddy.kidremote.PairState
import com.toolateforteddy.kidremote.RemoteViewModel
import com.toolateforteddy.kidremote.Show
import com.toolateforteddy.kidremote.TvStatus
import com.toolateforteddy.kidremote.atv.RemoteSession
import com.toolateforteddy.kidremote.extractLink
import kotlinx.coroutines.launch

@Composable
fun GrownUpScreen(
    vm: RemoteViewModel,
    status: TvStatus,
    shows: List<Show>,
    notice: String?,
    onDone: () -> Unit,
) {
    // Look for TVs only while this screen is open.
    DisposableEffect(Unit) {
        vm.finder.start()
        onDispose { vm.finder.stop() }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grown-up settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = onDone) { Text("Done") }
            }

            SectionTitle("TV")
            TvSection(vm, status)

            if (status is TvStatus.Ready) {
                SectionTitle("Try the remote")
                TestButtons(vm)
            }

            SectionTitle("Shows")
            if (shows.isEmpty()) {
                Hint("No shows yet. Add one below.")
            }
            var editing by rememberSaveable { mutableStateOf<String?>(null) }
            shows.forEach { show ->
                ShowRow(
                    show = show,
                    first = show == shows.first(),
                    onEdit = { editing = show.id },
                    onUp = { vm.moveUp(show.id) },
                    onDelete = {
                        vm.deleteShow(show.id)
                        if (editing == show.id) editing = null
                    },
                )
            }

            val editShow = shows.firstOrNull { it.id == editing }
            SectionTitle(if (editShow != null) "Change ${editShow.name}" else "Add a show")
            ShowForm(vm, editShow, onFinished = { editing = null })

            Spacer(Modifier.height(48.dp))
        }
        if (notice != null) {
            Notice(notice, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
        }
    }
}

@Composable
private fun TvSection(vm: RemoteViewModel, status: TvStatus) {
    val pairState by vm.tv.pairState.collectAsStateWithLifecycle()
    val found by vm.finder.found.collectAsStateWithLifecycle()
    val host = vm.store.tvHost

    when (val p = pairState) {
        is PairState.Starting -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  Asking the TV at ${p.host} to show a code…")
            }
            return
        }
        is PairState.WaitingForCode -> {
            CodeEntry(p, onSubmit = vm.tv::submitCode, onCancel = vm.tv::cancelPairing)
            return
        }
        is PairState.Failed -> {
            Warning("Pairing with ${p.host} didn't work: ${p.reason}")
            Spacer(Modifier.height(8.dp))
        }
        PairState.Idle -> Unit
    }

    if (host != null) {
        val name = vm.store.tvName?.let { "$it ($host)" } ?: host
        val line = when (status) {
            is TvStatus.Ready -> if (status.screenOn) "Connected to $name." else "Connected to $name. The TV is in standby."
            TvStatus.Connecting -> "Connecting to $name…"
            TvStatus.NotPaired -> "The TV at $host doesn't know this phone any more. Pair again below."
            is TvStatus.Offline -> "Not connected to $name. ${status.reason}"
            TvStatus.NoTv -> "No TV chosen."
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status)
            Text("  $line", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilledTonalButton(onClick = { vm.tv.beginPairing(host) }) { Text("Pair again") }
            TextButton(onClick = vm.tv::forgetTv) { Text("Forget this TV", color = Palette.Danger) }
        }
        Spacer(Modifier.height(12.dp))
    }

    val others = found.filter { it.host != host }
    if (others.isNotEmpty() || host == null) {
        Hint(if (host == null) "Pick your TV to pair this phone with it. You only need to do this once." else "Other TVs on this Wi-Fi:")
        if (others.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("  Looking for TVs on this Wi-Fi…", color = Palette.Muted)
            }
        }
        others.forEach { tv ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Palette.Tile).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tv.name, fontWeight = FontWeight.SemiBold)
                    Text(tv.host, color = Palette.Muted, fontSize = 14.sp)
                }
                Button(onClick = { vm.tv.beginPairing(tv.host) }) { Text("Pair") }
            }
        }
    }

    var manual by rememberSaveable { mutableStateOf("") }
    Text("Or type the TV's address (TV Settings > Network & Internet)", color = Palette.Muted, fontSize = 15.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            manual,
            { manual = it },
            placeholder = { Text("192.168.1.20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.tv.beginPairing(manual) }),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = { vm.tv.beginPairing(manual) }, enabled = manual.isNotBlank()) { Text("Pair") }
    }
}

@Composable
private fun CodeEntry(state: PairState.WaitingForCode, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Text("Type the code the TV is showing.", fontWeight = FontWeight.SemiBold)
    Hint("It's 6 letters and numbers, for example 3F9A0C.")
    OutlinedTextField(
        code,
        { code = it.filter { c -> c.isLetterOrDigit() }.take(6).uppercase() },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onSubmit(code) }),
        modifier = Modifier.padding(top = 8.dp).widthIn(max = 280.dp),
    )
    state.error?.let { Text(it, color = Palette.Danger, modifier = Modifier.padding(top = 6.dp)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp)) {
        Button(onClick = { onSubmit(code) }, enabled = code.length == 6 && !state.checking) { Text("Pair") }
        TextButton(onClick = onCancel) { Text("Cancel") }
        if (state.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestButtons(vm: RemoteViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Home" to RemoteSession.KEY_HOME,
            "Back" to RemoteSession.KEY_BACK,
            "OK" to RemoteSession.KEY_DPAD_CENTER,
            "Play/Pause" to RemoteSession.KEY_PLAY_PAUSE,
            "Vol −" to RemoteSession.KEY_VOLUME_DOWN,
            "Vol +" to RemoteSession.KEY_VOLUME_UP,
        ).forEach { (label, key) ->
            FilledTonalButton(onClick = { vm.key(key) }) { Text(label) }
        }
    }
}

@Composable
private fun ShowRow(show: Show, first: Boolean, onEdit: () -> Unit, onUp: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Tile)
            .clickable(onClick = onEdit)
            .padding(8.dp),
    ) {
        Thumb(show.image, Modifier.size(width = 72.dp, height = 45.dp))
        Text(show.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        SmallButton("↑", enabled = !first, onClick = onUp)
        SmallButton("✕", color = Palette.Danger, onClick = onDelete)
    }
}

@Composable
private fun SmallButton(label: String, enabled: Boolean = true, color: androidx.compose.ui.graphics.Color = Palette.Text, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.TileRaised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) color else Palette.Gear, fontSize = 18.sp)
    }
}

@Composable
private fun Thumb(src: String, modifier: Modifier) {
    val picture = rememberPicture(src)
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Palette.TileRaised)) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ShowForm(vm: RemoteViewModel, editing: Show?, onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    val key = editing?.id ?: "new"
    var link by rememberSaveable(key) { mutableStateOf(editing?.link.orEmpty()) }
    var name by rememberSaveable(key) { mutableStateOf(editing?.name.orEmpty()) }
    var image by rememberSaveable(key) { mutableStateOf(editing?.image.orEmpty()) }
    var looking by rememberSaveable(key) { mutableStateOf(false) }
    var message by rememberSaveable(key) { mutableStateOf<String?>(null) }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            vm.savePicked(uri)?.let { image = it } ?: run { message = "Couldn't use that picture." }
        }
    }

    Hint("In the Prime Video, Netflix or YouTube app on this phone, open the show, tap Share, copy the link and paste it here.")
    OutlinedTextField(
        link,
        { link = extractLink(it) },
        label = { Text("Link") },
        placeholder = { Text("https://…") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)) {
        FilledTonalButton(
            enabled = link.startsWith("http") && !looking,
            onClick = {
                looking = true
                message = null
                scope.launch {
                    val (foundName, foundImage) = vm.lookup(link)
                    looking = false
                    if (foundName != null && name.isBlank()) name = foundName
                    if (foundImage != null) image = foundImage
                    if (foundName == null && foundImage == null) message = "That page didn't share a name or picture. Type a name and pick a picture yourself."
                }
            },
        ) { Text("Get name and picture") }
        if (looking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
    OutlinedTextField(
        name, { name = it },
        label = { Text("Name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    Box(
        Modifier.padding(top = 12.dp).widthIn(max = 320.dp).fillMaxWidth().aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(12.dp)).background(Palette.TileRaised)
            .clickable { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        contentAlignment = Alignment.Center,
    ) {
        val picture = rememberPicture(image)
        if (picture != null) {
            Image(picture, contentDescription = "Show picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("Tap to choose a picture\nfrom this phone", color = Palette.Muted, fontSize = 15.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        TextButton(onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text("Choose picture")
        }
        if (image.isNotEmpty()) TextButton(onClick = { image = "" }) { Text("Remove picture") }
    }

    message?.let { Text(it, color = Palette.Warn, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }

    val valid = name.isNotBlank() && link.contains("://")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Button(
            enabled = valid,
            onClick = {
                vm.saveShow(editing?.id, name, link, image)
                link = ""; name = ""; image = ""; message = null
                onFinished()
            },
            colors = ButtonDefaults.buttonColors(),
        ) { Text(if (editing != null) "Save" else "Add show") }
        FilledTonalButton(enabled = link.contains("://"), onClick = { vm.tryLink(link) }) { Text("Try on TV") }
        if (editing != null) TextButton(onClick = onFinished) { Text("Cancel") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Palette.Muted, fontSize = 17.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Palette.Muted, fontSize = 15.sp)
}

@Composable
private fun Warning(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Warn.copy(alpha = 0.15f)).padding(12.dp)) {
        Text(text, fontSize = 15.sp)
    }
}
