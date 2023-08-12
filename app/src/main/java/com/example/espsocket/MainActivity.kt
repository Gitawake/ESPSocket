package com.example.espsocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espsocket.ui.theme.ESPSocketTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.SocketTimeoutException
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    var connectState: String,
    var messageState: String
)

val uiState = UiState("not connected", "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ESPSocketTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ESPRemoteControl(viewModel = SocketViewModel())
                }
            }
        }
    }
}

class SocketClient {
    private var serverIP = "192.168.31.237"
    private var serverPort = 1212 // 服务器端口号

    private val socketMutex = Mutex()
    private var socket: Socket? = null

    private fun connectToServerInternal(): Boolean {
        return try {
            socket = Socket(serverIP, serverPort)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun sendInternal(message: String): String {
        var response = ""
        val timeoutMillis = 5000 // 5秒的超时等待时间
        socket?.let { socket ->
            socket.soTimeout = timeoutMillis
            try {
                val outputStream = socket.getOutputStream()
                val writer = PrintWriter(outputStream, true)
                writer.println(message)

                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))
                response = reader.readLine()
            } catch (e: SocketTimeoutException) {
                response = "message timeout"
                e.printStackTrace()
            } catch (e: Exception) {
                response = "Message failed"
                e.printStackTrace()
            }
        }
        return response
    }

    suspend fun connectToServer(): Boolean {
        return socketMutex.withLock {
            connectToServerInternal()
        }
    }

    suspend fun sendMessage(message: String): String {
        return socketMutex.withLock {
            sendInternal(message)
        }
    }

    suspend fun disconnect() {
        socketMutex.withLock {
            socket?.close()
        }
    }
}



class SocketViewModel : ViewModel() {
    var state by mutableStateOf(uiState)
    private val socketClient = SocketClient()
    private val heartBeatInterval = 3000L // 3秒，心跳间隔时间
    private var heartBeatJob: Job? = null

    private fun formatTimestamp(): String {
        val currentTimeMillis = System.currentTimeMillis()
        val dateTimeFormatter =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US)
        return dateTimeFormatter.format(Date(currentTimeMillis))
    }

    fun connectToServerAndUpdateState() {
        viewModelScope.launch(Dispatchers.IO) {
            val initialization = socketClient.connectToServer()
            if (initialization) {
                val dateTime = formatTimestamp()
                val callback = socketClient.sendMessage("$dateTime -- Holle,Socket!")
                if (callback == "OK&$dateTime -- Holle,Socket!") {
                    state = state.copy(connectState = "Success connect")
                    startHeartBeat()
                }else{
                    state = state.copy(connectState = "connect failed")
                }
            }else{
                state = state.copy(connectState = "connect failed")
            }
        }
    }

    fun sendMessageAndUpdateState(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val callback = socketClient.sendMessage(message) // 替换为实际的异步任务
            state = state.copy(messageState = callback)// 更新 messageState 字段
        }
    }

    fun disconnectAndUpdateState() {
        viewModelScope.launch(Dispatchers.IO) {
            heartBeatJob?.cancel()
            socketClient.disconnect() // 替换为实际的异步任务
            state = state.copy(connectState = "connection closed") // 更新 connectState 字段
        }
    }

    private fun startHeartBeat() {
        if (heartBeatJob?.isActive != true) {
            heartBeatJob = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    val dateTime = formatTimestamp()
                    val callback = socketClient.sendMessage("$dateTime -- heartbeat")
                    if (callback != "OK&$dateTime -- heartbeat") {
                        disconnectAndUpdateState()
                    }
                    delay(heartBeatInterval)
                }
            }
        }
    }
}


@Composable
fun ESPRemoteControl(modifier: Modifier = Modifier, viewModel: SocketViewModel) {

    val state = viewModel.state

    Column {
        Text(
            text = state.connectState + " ->" + state.messageState,
            modifier = modifier
        )
        Row {
            Button(
                onClick = {
                    if (state.connectState != "Success connect")
                        viewModel.connectToServerAndUpdateState()
                    else
                        viewModel.disconnectAndUpdateState()
                }
            ) {
                Text(
                    text = if (state.connectState != "Success connect") "Connect" else "Closed"
                )
            }
        }
        Row {
            Column {
                ButtonX("右前", "1F", viewModel)

                ButtonX("右后", "1A", viewModel)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column {
                ButtonX("左前", "2F", viewModel)

                ButtonX("左后", "2A", viewModel)
            }
        }
    }
}

@Composable
fun ButtonX(text: String, message: String, viewModel: SocketViewModel) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Button(
        onClick = { /* do something */ },
        modifier = Modifier
            .width(120.dp)
            .height(120.dp),
        interactionSource = interactionSource
    ) {
        Text(text)
        LaunchedEffect(isPressed) {
            if (isPressed) {
                viewModel.sendMessageAndUpdateState(message)
            } else {
                viewModel.sendMessageAndUpdateState("-$message")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ESPRemoteControlPreview() {
    ESPSocketTheme {
        ESPRemoteControl(viewModel = SocketViewModel())
    }
}







