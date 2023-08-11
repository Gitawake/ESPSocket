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
import java.net.SocketTimeoutException

data class UiState(
    var connectState: String,
    var messageState: String
)

val uiState = UiState("连接断开", "")

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
                    ESPRemoteControl(viewModel = MyViewModel())
                }
            }
        }
    }
}

class SocketClient {
    private var serverIP = "192.168.31.237"
    private var serverPort = 1212 // 服务器端口号
    private var socket: Socket? = null

    fun connectToServer(): String {
        var isSuccessfullyConnected = ""

        try {
            socket = Socket(serverIP, serverPort)
            socket?.let { socket ->
                val timeoutMillis = 5000 // 5秒的超时等待时间
                socket.soTimeout = timeoutMillis

                println("Socket 初始化...")
                try {
                    println("往服务端发送消息...")
                    val outputStream = socket.getOutputStream()
                    val writer = PrintWriter(outputStream, true)
                    writer.println("Hello!")
                    println("往服务端发送消息...OK")

                    println("等待服务端返回消息...")
                    val inputStream = socket.getInputStream()
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val response = reader.readLine()
                    println("等待服务端返回消息...OK")
                    // 处理服务器的响应
                    println("服务器返回的消息: $response")

                    // 如果收到了预期的服务器响应，说明连接正常
                    if (response == "OK&Hello!") {
                        isSuccessfullyConnected = "已连接..."
                        println("已成功连接服务端...")
                    } else {
                        isSuccessfullyConnected = "连接失败...!"
                        println("连接服务端失败...!")
                    }
                } catch (e: SocketTimeoutException) {
                    e.printStackTrace()
                    isSuccessfullyConnected = "连接超时...!"
                    println("连接超时...!")
                } catch (e: Exception) {
                    e.printStackTrace()
                    isSuccessfullyConnected = "连接失败...!"
                    println("连接服务端失败...!")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            isSuccessfullyConnected = "连接失败...!"
            println("连接服务端失败...!")
        }

        return isSuccessfullyConnected
    }


    fun sendHeartBeat(message: String): String {
        var heartBeatMessage = ""
        socket?.let { socket ->
            val timeoutMillis = 5000 // 5秒的超时等待时间
            socket.soTimeout = timeoutMillis
            try {
                println("往服务端发送心跳...")
                val outputStream = socket.getOutputStream()
                val writer = PrintWriter(outputStream, true)
                writer.println(message)
                println("往服务端发送心跳...OK")

                println("等待服务端返回心跳...")
                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = reader.readLine()
                println("等待服务端返回心跳...OK")
                // 处理服务器的响应
                heartBeatMessage = response
            } catch (e: SocketTimeoutException) {
                e.printStackTrace()
                heartBeatMessage = "心跳超时...!"
                println("心跳超时...!")
            }catch (e: Exception) {
                heartBeatMessage = "心跳失败...!"
                println("心跳失败...!")
                e.printStackTrace()
            }
        }
        return heartBeatMessage
    }

    fun sendMessage(message: String): String {
        var inMessage = ""
        socket?.let { socket ->
            val timeoutMillis = 5000 // 5秒的超时等待时间
            socket.soTimeout = timeoutMillis
            try {
                println("message:$message")
                println("往服务端发送消息...")
                val outputStream = socket.getOutputStream()
                val writer = PrintWriter(outputStream, true)
                writer.println(message)
                println("往服务端发送消息...OK")

                println("等待服务端返回消息...")
                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = reader.readLine()
                println("等待服务端返回消息...OK")
                // 处理服务器的响应
                println("服务器返回的消息: $response")
                inMessage = response
            } catch (e: SocketTimeoutException) {
                e.printStackTrace()
                inMessage = "连接超时...!"
                println("连接超时...!")
            }catch (e: Exception) {
                inMessage = "连接失败...!"
                println("连接失败...!")
                e.printStackTrace()
            }
        }
        return inMessage
    }

    fun disconnect(): String {
        val inState = "连接断开"
        socket?.close()
        println(inState)
        return inState
    }
}

val socketClient = SocketClient()

class MyViewModel : ViewModel() {
    var state by mutableStateOf(uiState)

    private val heartBeatInterval = 3000L // 3秒，心跳间隔时间
    private var heartBeatJob: Job? = null

    fun connectToServerAndUpdateState() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = socketClient.connectToServer() // 替换为实际的异步任务
            if (result == "已连接...") {
                startHeartBeat()
            }
            state = state.copy(connectState = result)// 更新 connectState 字段
        }
    }

    fun sendMessageAndUpdateState(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = socketClient.sendMessage(message) // 替换为实际的异步任务
            state = state.copy(messageState = result)// 更新 messageState 字段
        }
    }

    fun disconnectAndUpdateState() {
        viewModelScope.launch(Dispatchers.IO) {
            heartBeatJob?.cancel()
            val result = socketClient.disconnect() // 替换为实际的异步任务
            state = state.copy(connectState = result)// 更新 connectState 字段
        }
    }


    fun startHeartBeat() {
        if (heartBeatJob?.isActive != true) {
            heartBeatJob = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    val currentTime = System.currentTimeMillis()
                    val result = socketClient.sendHeartBeat("heartbeat $currentTime")
                    if (result != "OK&heartbeat $currentTime") {
                        heartBeatJob?.cancel()
                        state = state.copy(connectState = "心跳失败")// 更新 connectState 字段
                    }
                    delay(heartBeatInterval)
                }
            }
        }
    }
}


@Composable
fun ESPRemoteControl(modifier: Modifier = Modifier, viewModel: MyViewModel) {

    val state = viewModel.state

    Column {
        Text(
            text = state.connectState + state.messageState,
            modifier = modifier
        )
        Row {
            Button(
                onClick = {
                    if (state.connectState != "已连接...") {
                        viewModel.connectToServerAndUpdateState()
                    } else {
                        viewModel.disconnectAndUpdateState()
                    }
                    println("连接按钮被按下...")

                }
            ) {
                Text(
                    text = if (state.connectState != "已连接...") {
                        "连接"
                    } else {
                        "断开"
                    }
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
fun ButtonX(text: String, message: String, viewModel: MyViewModel) {
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
        ESPRemoteControl(viewModel = MyViewModel())
    }
}







