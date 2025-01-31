/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.example.ftpclient

import FileUtils
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ftpclient.databinding.BasicChatActivityBinding
import com.google.android.material.snackbar.Snackbar
import com.naminfo.ftpclient.core.FTPUtil
import com.naminfo.ftpclient.core.SelectFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.linphone.core.Account
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoom.Capabilities
import org.linphone.core.Content
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import java.io.File
import java.io.FileOutputStream


private const val TAG = "==>>BasicChatActivity"

class BasicChatActivity : AppCompatActivity() {
    private lateinit var core: Core
    private var chatRoom: ChatRoom? = null
    private var binding: BasicChatActivityBinding? = null
    private val selectFiles: SelectFiles = SelectFiles(this)
    private lateinit var selectFilesLauncher: ActivityResultLauncher<Intent>
    private var selectedFileUri: Uri? = null
    private var selectedMultipleUri: MutableList<Uri> = mutableListOf()
    private val ftpUtil = FTPUtil()
    private var ftpURL = ""
    var server = ""
    var username = ""
    var password = ""
    var globFileName = ""
    var chatMessage: ChatMessage? = null
    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            findViewById<TextView>(R.id.registration_status).text = message

            if (state == RegistrationState.Failed) {
                core.clearAllAuthInfo()
                core.clearAccounts()
                findViewById<Button>(R.id.connect).isEnabled = true
            } else if (state == RegistrationState.Ok) {
                findViewById<LinearLayout>(R.id.register_layout).visibility = View.GONE
                findViewById<RelativeLayout>(R.id.chat_layout).visibility = View.VISIBLE
            }
        }

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {

            for (content in message.contents) {
                when {
                    content.isText -> {
                        Log.d(
                            TAG,
                            "onMessageReceived:${content.utf8Text} ,type:${content.type},subtype:${content.subtype},path:${content.filePath},name:${content.name}," +
                                    "header:${content.getCustomHeader("attached")}"
                        )
                        var splitMsgBefore = ""
                        var splitMsgAfter = ""
                        if (content.utf8Text?.contains("<-|->") == true) {
                            splitMsgAfter = content.utf8Text?.substringAfter("<-|->").toString()
                            splitMsgBefore = content.utf8Text?.substringBefore("<-|->").toString()
                        } else {
                            splitMsgBefore = content.utf8Text.toString()
                        }
                        addMessageToHistory(message, true, splitMsgBefore, splitMsgAfter)
                    }
                }
            }
            // We will be called in this when a message is received
            // If the chat room wasn't existing, it is automatically created by the library
            // If we already sent a chat message, the chatRoom variable will be the same as the one we already have
            if (this@BasicChatActivity.chatRoom == null) {
//                if (chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())) {
                if (chatRoom.hasCapability(Capabilities.Basic.toInt())) {
                    // Keep the chatRoom object to use it to send messages if it hasn't been created yet
                    this@BasicChatActivity.chatRoom = chatRoom
                    findViewById<EditText>(R.id.remote_address).setText(chatRoom.peerAddress.asStringUriOnly())
                    findViewById<EditText>(R.id.remote_address).isEnabled = false
                }
            }
            snackMessage("message received")
            // We will notify the sender the message has been read by us
            chatRoom.markAsRead()


        }
    }

    private val chatMessageListener = object : ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State?) {
            val messageView = message.userData as? View
            when (state) {
                ChatMessage.State.InProgress -> {
                    messageView?.setBackgroundColor(getColor(R.color.yellow))
                }

                ChatMessage.State.Delivered -> {
                    // The proxy server has acknowledged the message with a 200 OK
                    messageView?.setBackgroundColor(getColor(R.color.orange))
                }

                ChatMessage.State.DeliveredToUser -> {
                    // User as received it
                    messageView?.setBackgroundColor(getColor(R.color.blue))
                }

                ChatMessage.State.Displayed -> {
                    // User as read it (client called chatRoom.markAsRead()
                    messageView?.setBackgroundColor(getColor(R.color.green))
                }

                ChatMessage.State.NotDelivered -> {
                    // User might be invalid or not registered
                    messageView?.setBackgroundColor(getColor(R.color.red))
                }

                ChatMessage.State.FileTransferDone -> {
                    // We finished uploading/downloading the file
                    Log.d(TAG, "onMsgStateChanged: FileTransferDone")
                    snackMessage("FileTransferDone")
                    if (!message.isOutgoing) {
                        findViewById<LinearLayout>(R.id.messages).removeView(messageView)
                        addMessageToHistory(message, true)
                    }
                }

                ChatMessage.State.Idle -> binding?.root?.rootView?.let {
                    Snackbar.make(
                        it,
                        "Idle",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                ChatMessage.State.FileTransferError -> binding?.root?.rootView?.let {
                    Snackbar.make(
                        it,
                        "FileTransferError",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                ChatMessage.State.FileTransferInProgress -> binding?.root?.rootView?.let {
                    Snackbar.make(
                        it,
                        "FileTransferInProgress",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                else -> {
                    binding?.root?.rootView?.let {
                        Snackbar.make(
                            it,
                            "Unkonwn state",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.let {
            server = it.getString("server").toString()
            username = it.getString("username").toString()
            password = it.getString("password").toString()
            Log.d(TAG, "onCreate: $server $username $password")
        }
        binding = BasicChatActivityBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        Log.d(TAG, "onCreate: ${Phone.getDeviceName()}")
        testCase(false)
        val factory = Factory.instance()
        factory.setDebugMode(true, "Hello Linphone")
        core = factory.createCore(null, null, this)

        findViewById<Button>(R.id.connect).setOnClickListener {
            login()
            it.isEnabled = false
        }

        findViewById<Button>(R.id.send_message).setOnClickListener {
            sendMessage()
        }

        selectFilesLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    data?.data?.let { uri ->
                        Log.d(TAG, "Selected single file URI: $uri")
                        selectedFileUri = uri
                    }

                    data?.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            Log.d(TAG, "Selected multiple files URI: $uri")
                            selectedMultipleUri.add(uri) // Add to the list if needed
                        }
                    }
                }
            }

        findViewById<ImageView>(R.id.send_image).setOnClickListener {
            // sendImage()
            sendImageViaFTP()
        }
    }

    private fun testCase(linMode: Boolean = false) {
        if (linMode) {
            val sipServer = "sip.linphone.org"
            val from = "nisath"
            val to = "neltech"
            val linpassword = "*niaeiMY6"
            if (getDeviceName().equals("Xiaomi 22111317I", true)) {
                binding?.apply {
                    username.setText(from)
                    password.setText(linpassword)
                    domain.setText(sipServer)
                    remoteAddress.setText("sip:$to@$sipServer")
                }
            } else {
                binding?.apply {
                    username.setText(to)
                    password.setText(linpassword)
                    domain.setText(sipServer)
                    remoteAddress.setText("sip:$from@$sipServer")
                }
            }
        } else {
            val namSipServer = "192.168.1.32"
            val namFrom = "9445346291"
            val namTo = "7845470737"
            val linpassword = "*niaeiMY6"
            if (getDeviceName().equals("Xiaomi 22111317I", true)) {
                binding?.apply {
                    username.setText(namFrom)
                    password.setText(namFrom)
                    domain.setText(namSipServer)
                    remoteAddress.setText("sip:$namTo@$namSipServer")
                }
            } else {
                binding?.apply {
                    username.setText(namTo)
                    password.setText(namTo)
                    domain.setText(namSipServer)
                    remoteAddress.setText("sip:$namFrom@$namSipServer")
                }
            }
        }

    }

    private fun login() {
        val username = findViewById<EditText>(R.id.username).text.toString().trim()
        val password = findViewById<EditText>(R.id.password).text.toString().trim()
        val domain = findViewById<EditText>(R.id.domain).text.toString().trim()
        val transportType = when (findViewById<RadioGroup>(R.id.transport).checkedRadioButtonId) {
            R.id.udp -> TransportType.Udp
            R.id.tcp -> TransportType.Tcp
            else -> TransportType.Tls
        }
        val authInfo =
            Factory.instance().createAuthInfo(username, null, password, null, null, domain, null)

        val params = core.createAccountParams()
        val identity = Factory.instance().createAddress("sip:$username@$domain")
        params.identityAddress = identity

        val address = Factory.instance().createAddress("sip:$domain")
        address?.transport = transportType
        params.serverAddress = address
        params.isRegisterEnabled = true
        val account = core.createAccount(params)

        core.addAuthInfo(authInfo)
        core.addAccount(account)

        core.defaultAccount = account
        core.addListener(coreListener)
        core.start()
    }

    private fun createBasicChatRoom() {
        // In this tutorial we will create a Basic chat room
        // It doesn't include advanced features such as end-to-end encryption or groups
        // But it is interoperable with any SIP service as it's relying on SIP SIMPLE messages
        // If you try to enable a feature not supported by the basic backend, isValid() will return false
        val params = core.createDefaultChatRoomParams()
        params.backend = ChatRoom.Backend.Basic
        params.isEncryptionEnabled = false
        params.isGroupEnabled = false


        if (params.isValid) {
            // We also need the SIP address of the person we will chat with
            val remoteSipUri = findViewById<EditText>(R.id.remote_address).text.toString()
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)

            if (remoteAddress != null) {
                // And finally we will need our local SIP address
                val localAddress = core.defaultAccount?.params?.identityAddress
                val room = core.createChatRoom(params, localAddress, arrayOf(remoteAddress))

                if (room != null) {
                    chatRoom = room
                    findViewById<EditText>(R.id.remote_address).isEnabled = false
                }
            }
        }
    }

    private fun sendMessage() {
        uploadFileToFTP()

    }

    private fun sentTextMessages(ftpURL: String) {

        if (chatRoom == null) {
            // We need a ChatRoom object to send chat messages in it, so let's create it if it hasn't been done yet
            createBasicChatRoom()
        }
        val stringBuilder = StringBuilder()

        val message = binding?.message?.text.toString()
        Log.d(TAG, "sentTextMessages: message = $message ftpURL =$ftpURL")
        // We need to create a ChatMessage object using the ChatRoom
        if (message.isNotEmpty()) {
            stringBuilder.append(message)
            chatMessage = chatRoom!!.createMessageFromUtf8(stringBuilder.toString())
        }
        if (selectedMultipleUri.isNotEmpty()) {
            for (uri in selectedMultipleUri) {
                FileUtils.getFileInfo(this@BasicChatActivity, uri).apply {
                    //  chatMessage = chatRoom!!.createMessageFromUtf8(this.name!!)
                    var path = ""
                    path = if (this.path == null)
                        getFilePathFromUri(uri) ?: ""
                    else this.path!!
                    sendImageToFTP(
                        // chatMessage!!,
                        ftpURL,
                        path,
                        this.name ?: "",
                        FileUtils.getMimeType(File(path)),
                        this.extension!!,
                        stringBuilder
                    )
                }

            }
        } else {
            if (selectedFileUri != null) {
                FileUtils.getFileInfo(this@BasicChatActivity, selectedFileUri!!).apply {
                    //chatMessage = chatRoom!!.createMessageFromUtf8(this.name!!)
                    var path = ""
                    path = if (this.path == null)
                        getFilePathFromUri(selectedFileUri!!) ?: ""
                    else this.path!!
                    sendImageToFTP(
                        //  chatMessage!!,
                        ftpURL,
                        path,
                        this.name ?: "",
                        FileUtils.getMimeType(File(path)),
                        this.extension!!,
                        stringBuilder
                    )
                }
            } else {
                if (chatMessage != null) {
                    chatMessage?.addListener(chatMessageListener)
                    addMessageToHistory(chatMessage!!, false)
                    chatMessage?.send()
                }
                snackMessage("Not selected any file")
            }
        }


        // Clear the message input field
        // findViewById<EditText>(R.id.message).text.clear()


    }

    private fun snackMessage(msg: String) {
        Snackbar.make(
            binding?.root?.rootView!!,
            msg,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun sendImageViaFTP() {

        selectFiles.chooseFiles {
            selectFilesLauncher.launch(it)
        }

    }

    private fun uploadFileToFTP() {
        Log.d(TAG, "uploadFileToFTP: ")
        if (server.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
            if (selectedMultipleUri.isNotEmpty()) {
                for (uri in selectedMultipleUri) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (selectFiles.uploadFiles(server, username, password, uri,
                                ftpUtil = ftpUtil,
                                getFilePath = { uri ->
                                    getFilePathFromUri(uri)
                                }, getURL = {
                                    Log.d(TAG, "uiInit: uploaded url is $it")
                                    ftpURL = it
                                    runOnUiThread {
                                        //binding?.message?.setText(it)
                                        sentTextMessages(ftpURL = it)
                                    }

                                })
                        ) {
                            snackMessage("File upload successfully")
                        } else {
                            snackMessage("File upload failed")
                        }
                    }
                }
            } else if (selectedFileUri != null) {
                lifecycleScope.launch {
                    if (selectFiles.uploadFiles(server, username, password, selectedFileUri!!,
                            ftpUtil = ftpUtil,
                            getFilePath = { uri ->
                                getFilePathFromUri(uri)
                            }, getURL = {
                                Log.d(TAG, "uiInit: uploaded url is $it")
                                ftpURL = it
                                runOnUiThread {
                                    // binding?.message?.setText(it)
                                    sentTextMessages(ftpURL = it)
                                }
                            })
                    ) {
                        snackMessage("File upload successfully")
                    } else {
                        snackMessage("File upload failed")
                    }
                }
            } else {
                snackMessage("No files selected for upload.")
                sentTextMessages(ftpURL = "")
                Log.e(TAG, "No files selected for upload.")
            }
        } else {
            snackMessage("valid user name and password")
            Log.e(TAG, "uiInit: valid user name and password")
        }
    }

    fun getFilenameWithoutExtension(filename: String): String {

        val lastDotIndex = filename.lastIndexOf('.')
        return if (lastDotIndex == -1) {
            filename
        } else {
            filename.substring(0, lastDotIndex)
        }
    }

    private fun sendImageToFTP(
        /*chatMessage: ChatMessage , */
        ftpURL: String,
        filePath: String = getFilePathFromUri(selectedFileUri!!).toString(),
        fileName: String,
        mimeType: String,
        extension: String,
        stringBuilder: StringBuilder
    ) {
        // var chatMessageNew = chatMessage
        Log.d(
            TAG,
            "sendImageToFTP() called with: , ftpURL = $ftpURL, filePath = $filePath, fileName = $fileName, mimeType = $mimeType, extension = $extension"
        )
//        core.fileTransferServer = "ftp://Admin:ivrs%40123@192.168.1.167"
        stringBuilder.append("<-|->$fileName")
        // chatMessage?.addUtf8TextContent(stringBuilder.toString())
        chatMessage = chatRoom!!.createMessageFromUtf8(stringBuilder.toString())
        val content = Factory.instance().createContent()
        content.type = mimeType
        content.subtype = extension
        Log.d(TAG, "sendImageToFTP: name =${getFilenameWithoutExtension(fileName)}")
        copy("${getFilenameWithoutExtension(fileName)}.$extension", filePath)
        content.filePath = filePath
        content.name = getFilenameWithoutExtension(fileName)
        chatMessage?.addCustomHeader("attached", filePath)
        chatMessage?.addContent(content)
        chatMessage?.addListener(chatMessageListener)
        addMessageToHistory(chatMessage!!, false)
        chatMessage?.send()
        stringBuilder.clear()
    }

    private fun sendImage(
        chatMessage: ChatMessage,
        filePath: String,
        fileName: String,
        mimeType: String,
        extension: String
    ): ChatMessage {


        // We need to create a Content for our file transfer
        val content = Factory.instance().createContent()
        // Every content needs a content type & subtype
        content.type = mimeType
        content.subtype = extension

        // The simplest way to upload a file is to provide it's path
        // First copy the sample file from assets to the app directory if not done yet
        Log.d(
            TAG,
            "sendImage() called with: filePath = $filePath, fileName = $fileName, mimeType = $mimeType, extension = $extension"
        )
        copy("$fileName.$extension", filePath)
        content.filePath = filePath
        chatMessage.addFileContent(content)

        // We need to create a ChatMessage object using the ChatRoom
        //  val chatMessage = chatRoom!!.createFileTransferMessage(content)

        // Then we can send it, progress will be notified using the onMsgStateChanged callback
        chatMessage.addListener(chatMessageListener)
        // core.fileTransferServer = "sip:sip.linphone.org"
        addMessageToHistory(chatMessage, true)
        chatMessage.send()

        return chatMessage
    }

    private fun getFilePathFromUri(uris: Uri): String? {

        return uris.let { uri -> FileUtils.getPath(this, uri) }
    }

    private fun addMessageToHistory(
        chatMessage: ChatMessage,
        mode: Boolean = false,
        beforeText: String = "",
        afterText: String = ""
    ) {
        // To display a chat message, iterate over it's contents list

        for (content in chatMessage.contents) {
            when {
                content.isText -> {
                    if (mode == false) {
                        if ((content.utf8Text == null || content.utf8Text!!.isEmpty()) && ftpURL.isNotEmpty()) {
                            //only image chat
                            addTextMessageToHistory(
                                chatMessage,
                                content,
                                content.utf8Text.toString()
                            )
                            addDownloadButtonToHistory(
                                chatMessage,
                                content,
                                ftpURL,
                                content.utf8Text.toString().substringAfter("<-|->")
                            )
                        } else if (content.utf8Text!!.isNotEmpty() && ftpURL.isEmpty()) {
                            //only text chat
                            addTextMessageToHistory(chatMessage, content)
                        } else if (content.utf8Text!!.isNotEmpty() && ftpURL.isNotEmpty()) {
                            //both text and image chat
                            addTextMessageToHistory(chatMessage, content)
                            addDownloadButtonToHistory(chatMessage, content, ftpURL,content.utf8Text.toString().substringAfter("<-|->"))
                        } else {
                            //empty field
                        }
                    } else {

                        if (beforeText.isNotEmpty()) {
                            addTextMessageToHistory(chatMessage, content, beforeText)
                        }
                        if (afterText.isNotEmpty()) {
                            ftpURL = "ftp://Admin:ivrs%40123@192.168.1.167/$afterText"
                            addDownloadButtonToHistory(chatMessage, content, ftpURL, afterText)
                        }

                    }
                    Log.d(
                        TAG,
                        "addMessageToHistory: isText  =>>> name=${content.name} ${content.utf8Text} ftpURL=$ftpURL"
                    )

                    //selectedMultipleUri.clear()
                    //selectedFileUri =null
                }

                content.isFile -> {
                    Log.d(TAG, "addMessageToHistory: isFile")
                    if (ftpURL.isNotEmpty()) {
                        if (content.name?.endsWith(".jpeg") == true ||
                            content.name?.endsWith(".jpg") == true ||
                            content.name?.endsWith(".png") == true
                        ) {
                            Log.d(TAG, "addMessageToHistory: Image")
                            addDownloadButtonToHistory(chatMessage, content, ftpURL)
                        } else if (content.name?.endsWith(".mp4") == true ||
                            content.name?.endsWith(".avi") == true ||
                            content.name?.endsWith(".mov") == true ||
                            content.name?.endsWith(".mkv") == true
                        ) {
                            Log.d(TAG, "addMessageToHistory: Video")
                            addDownloadButtonToHistory(chatMessage, content, ftpURL)
                        } else if (content.name?.endsWith(".mp3") == true ||
                            content.name?.endsWith(".wav") == true ||
                            content.name?.endsWith(".aac") == true ||
                            content.name?.endsWith(".m4a") == true
                        ) {
                            Log.d(TAG, "addMessageToHistory: Audio")
                            addDownloadButtonToHistory(chatMessage, content, ftpURL)
                        } else {
                            Log.d(TAG, "addMessageToHistory: Documents")
                            addDownloadButtonToHistory(chatMessage, content, ftpURL)
                        }
                    }
                }
            }
        }
    }

    private fun addMessageToHistory(chatMessage: ChatMessage) {
        // To display a chat message, iterate over it's contents list
        for (content in chatMessage.contents) {
            when {
                content.isText -> {
                    // Content is of type plain/text
                    Log.d(TAG, "addMessageToHistory: isText")
                    addTextMessageToHistory(chatMessage, content, "")
                    if (ftpURL.isNotEmpty())
                    // addDownloadButtonToHistory(chatMessage, content, ftpURL)
                        addImageMessageToHistory(chatMessage, content)
                }

                content.isFile -> {
                    Log.d(TAG, "addMessageToHistory: isFile")
                    if (content.name?.endsWith(".jpeg") == true ||
                        content.name?.endsWith(".jpg") == true ||
                        content.name?.endsWith(".png") == true
                    ) {
                        addImageMessageToHistory(chatMessage, content)
                    }
                }

                content.isFileTransfer -> {
                    Log.d(TAG, "addMessageToHistory: isFileTransfer")
                    // Content represents a received file we didn't download yet
                    addDownloadButtonToHistory(chatMessage, content)
                }
            }
        }
    }

    private fun addTextMessageToHistory(
        chatMessage: ChatMessage,
        content: Content,
        msg: String = ""
    ) {
        val messageView = TextView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = if (chatMessage.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        messageView.layoutParams = layoutParams

        // Content is of type plain/text, we can get the text in the content

        var splitMsgBefore = ""
        var splitMsgAfter = ""
        var concat = ""
        if (content.utf8Text?.contains("<-|->") == true) {
            splitMsgAfter = content.utf8Text?.substringAfter("<-|->").toString()
            splitMsgBefore = content.utf8Text?.substringBefore("<-|->").toString()
            concat = "$splitMsgBefore\n$splitMsgAfter"
        } else {
            concat = content.utf8Text.toString()
        }

        messageView.text = "${concat}"

        if (chatMessage.isOutgoing) {
            messageView.setBackgroundColor(getColor(R.color.white))
        } else {
            messageView.setBackgroundColor(getColor(R.color.purple_200))
        }

        chatMessage.userData = messageView

        findViewById<LinearLayout>(R.id.messages).addView(messageView)
        findViewById<ScrollView>(R.id.scroll).fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun addDownloadButtonToHistory(chatMessage: ChatMessage, content: Content) {
        val buttonView = Button(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = if (chatMessage.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        buttonView.layoutParams = layoutParams
        buttonView.text = "Download"

        chatMessage.userData = buttonView
        buttonView.setOnClickListener {
            buttonView.isEnabled = false
            // Set the path to where we want the file to be stored
            // Here we will use the app private storage
            content.filePath = "${filesDir.absolutePath}/${content.name}"

            // Start the download
            chatMessage.downloadContent(content)

            // Download progress will be notified through onMsgStateChanged callback,
            // so we need to add a listener if not done yet
            if (!chatMessage.isOutgoing) {
                chatMessage.addListener(chatMessageListener)
            }
        }

        findViewById<LinearLayout>(R.id.messages).addView(buttonView)
        findViewById<ScrollView>(R.id.scroll).fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun addDownloadButtonToHistory(
        chatMessage: ChatMessage,
        content: Content,
        filePath: String,
        msg: String = ""
    ) {
        val buttonView = Button(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = if (chatMessage.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        buttonView.layoutParams = layoutParams
       /* if (msg.isNotEmpty())
            buttonView.text = msg
        else*/
            buttonView.text="Download"

        chatMessage.userData = buttonView
        buttonView.setOnClickListener {
            buttonView.isEnabled = false
            // Set the path to where we want the file to be stored
            // Here we will use the app private storage
            content.filePath = "${filePath}"
            Log.d(
                TAG,
                "addDownloadButtonToHistory: content.filePath=${content.filePath},filepath=$filePath content=${content.filePath}"
            )
            lifecycleScope.launch(Dispatchers.IO) {
                selectFiles.downloadFileViaFtpURL(
                    ftpURL,
                    selectFiles.getDownloadDirectoryPath(),
                    ftpUtil!!
                ) { status, context, uri, mimetype ->
                    Log.d(TAG, "addDownloadButtonToHistory: $uri")

                }
            }
            // Start the download
            chatMessage.downloadContent(content)

            // Download progress will be notified through onMsgStateChanged callback,
            // so we need to add a listener if not done yet
            if (!chatMessage.isOutgoing) {
                chatMessage.addListener(chatMessageListener)
            }
        }

        findViewById<LinearLayout>(R.id.messages).addView(buttonView)
        findViewById<ScrollView>(R.id.scroll).fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun addImageMessageToHistory(chatMessage: ChatMessage, content: Content) {
        val imageView = ImageView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = if (chatMessage.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        imageView.layoutParams = layoutParams

        // As we downloaded the file to the content.filePath, we can now use it to display the image
        imageView.setImageBitmap(BitmapFactory.decodeFile(content.filePath))

        chatMessage.userData = imageView

        findViewById<LinearLayout>(R.id.messages).addView(imageView)
        findViewById<ScrollView>(R.id.scroll).fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun copy(from: String, to: String) {
        // Used to copy a file from the assets to the app directory
        val outFile = File(to)
        if (outFile.exists()) {
            return
        }

        val outStream = FileOutputStream(outFile)
        val inFile = assets.open(from)
        val buffer = ByteArray(1024)
        var length: Int = inFile.read(buffer)

        while (length > 0) {
            outStream.write(buffer, 0, length)
            length = inFile.read(buffer)
        }

        inFile.close()
        outStream.flush()
        outStream.close()
    }

    companion object Phone {

        fun getDeviceName(): String {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer)) {
                model
            } else {
                "$manufacturer $model"
            }
        }
    }
}