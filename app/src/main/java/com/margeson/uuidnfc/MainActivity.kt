package com.margeson.uuidnfc

import android.content.Intent
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.margeson.uuidnfc.ui.theme.UUIDNFCTheme
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val nfcState = mutableStateOf<NfcState>(NfcState.Idle)
    private var writeTextValue: String = ""

    private lateinit var nfcController: NfcController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val adapter = NfcAdapter.getDefaultAdapter(this)
            ?: throw RuntimeException("NFC not supported on this device")

        nfcController = NfcController(
            activity = this,
            nfcAdapter = adapter,
            onResult = { value, mode ->
                nfcState.value = NfcState.Success(
                    message = value,
                    mode = mode
                )
                nfcController.stop()
            },
            onError = { msg ->
                nfcState.value = NfcState.Error(msg)
                nfcController.stop()
            }
        )

        setContent {
            UUIDNFCTheme {
                MainView(
                    nfcState = nfcState.value,
                    onReadClick = {
                        nfcState.value = NfcState.Scanning(NfcState.Mode.READ)
                        nfcController.start()
                    },
                    onWriteClick = {
                        nfcState.value = NfcState.Scanning(NfcState.Mode.WRITE)
                        nfcController.start()
                    },
                    onWriteTextChange = { text ->
                        writeTextValue = text
                    },
                    onCancelScan = {
                        nfcState.value = NfcState.Idle
                        nfcController.stop()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val state = nfcState.value

        if (state is NfcState.Scanning) {
            nfcController.handleIntent(
                intent = intent,
                mode = state.mode,
                payloadToWrite = writeTextValue
            )
        }
    }

    override fun onResume() {
        super.onResume()

        if (nfcState.value is NfcState.Scanning) {
            nfcController.start()
        }
    }

    override fun onPause() {
        super.onPause()
        nfcController.stop()
    }
}

fun generateUUID(field: TextFieldState) {
    field.setTextAndPlaceCursorAtEnd(
        UUID.randomUUID().toString()
    )
}

@Composable
fun CopyTextButton(fromField: TextFieldState) {
    val clipboard = LocalClipboardManager.current

    Icon(
        painter = painterResource(R.drawable.content_copy_24px),
        contentDescription = stringResource(id = R.string.content_copy_description),
        modifier = Modifier.clickable {
            clipboard.setText(
                AnnotatedString(fromField.text.toString())
            )
        }
    )
}

@Composable
fun LabeledOutlinedSection(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.padding(horizontal = 24.dp)) {

        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.outlinedCardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }

        Text(
            text = label,
            modifier = Modifier
                .padding(start = 16.dp)
                .offset(y = (-10).dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun DashedBox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 24.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Convert dp to pixels using Density (required for canvas units)
            val strokeWidth = 2.dp.toPx()
            val dashOnLength = 10.dp.toPx() // Length of the "on" segment
            val dashOffLength = 5.dp.toPx() // Length of the "off" segment
            val cornerRadius = 8.dp.toPx() // Rounded corners

            // Define the dashed path effect
            val dashPathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dashOnLength, dashOffLength),
                phase = 0f // Offset to start the dash (0 = no offset)
            )

            // Draw the dashed rounded rectangle
            drawRoundRect(
                color = Color.Gray,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = dashPathEffect
                ),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tap the NFC tag to the \nback of your device.", color = Color.Gray,
                textAlign = TextAlign.Center
            )

        }
    }
}

@Composable
fun WaitingModal(onCancelScan: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelScan,
        text = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(
                            end = 24.dp
                        )
                        .size(
                            24.dp
                        )
                )
                Text("Waiting for NFC tag...")
            }
        },
        confirmButton = {
            Button(onClick = onCancelScan) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MainView(
    nfcState: NfcState,
    onReadClick: () -> Unit,
    onWriteClick: () -> Unit,
    onWriteTextChange: (String) -> Unit,
    onCancelScan: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isPortrait =
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val snackbarHostState = remember { SnackbarHostState() }
    val writeUUIDTextField = rememberTextFieldState()
    val readUUIDTextField = rememberTextFieldState()

    LaunchedEffect(nfcState) {
        when (nfcState) {
            is NfcState.Success -> {
                when (nfcState.mode) {

                    NfcState.Mode.READ -> {
                        readUUIDTextField.setTextAndPlaceCursorAtEnd(nfcState.message)
                        snackbarHostState.showSnackbar("Read from NFC")
                    }

                    NfcState.Mode.WRITE -> {
                        writeUUIDTextField.setTextAndPlaceCursorAtEnd(nfcState.message)
                        snackbarHostState.showSnackbar("Written to NFC")
                    }
                }
            }

            is NfcState.Error -> {
                snackbarHostState.showSnackbar(nfcState.message)
            }

            else -> Unit
        }
    }

    LaunchedEffect(writeUUIDTextField) {
        snapshotFlow { writeUUIDTextField.text.toString() }
            .collectLatest { text ->
                onWriteTextChange(text)
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isPortrait) {
                Column(modifier = Modifier.weight(.8f)) {
                    DashedBox()
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = if (isPortrait) 76.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, alignment = Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LabeledOutlinedSection(label = "Write") {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SelectionContainer {
                            TextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isPortrait) 76.dp else TextFieldDefaults.MinHeight),
                                state = writeUUIDTextField,
                                enabled = true,
                                suffix = { CopyTextButton(writeUUIDTextField) },

                                )
                        }

                        Row(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { generateUUID(writeUUIDTextField) }) {
                                Text("Generate UUID ")
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = onWriteClick
                            ) {
                                Text("Write NFC")
                            }
                        }

                    }
                }

                LabeledOutlinedSection(label = "Read") {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SelectionContainer {
                            TextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isPortrait) 76.dp else TextFieldDefaults.MinHeight),
                                state = readUUIDTextField,
                                enabled = true,
                                readOnly = true,
                                suffix = { CopyTextButton(readUUIDTextField) },
                            )
                        }

                        Button(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            onClick = onReadClick
                        ) {
                            Text("Read NFC")
                        }
                    }
                }
            }
        }

        /* Global footer (portrait only) */
        if (isPortrait) {
            val annotatedString = buildAnnotatedString {
                append("Made with 💖 by ")
                withLink(
                    LinkAnnotation.Url(
                        "https://marg.es/on",
                        TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
                    )
                ) {
                    append("Jack Margeson")
                }
                append(".")
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = annotatedString)

            }
        }

        /* NFC state modal */
        if (nfcState is NfcState.Scanning) {
            WaitingModal(onCancelScan)
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MainViewPreview() {
    UUIDNFCTheme {
        MainView(
            nfcState = NfcState.Idle,
            onReadClick = {},
            onWriteClick = {},
            onWriteTextChange = {},
            onCancelScan = {},
        )
    }
}