package com.epfl.esl.workoutapp.mobile.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsManageScreen(
    userKey: String,
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val friends by vm.friendsList.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val incoming by vm.incomingRequests.collectAsState()
    val outgoing by vm.outgoingRequests.collectAsState()



    LaunchedEffect(userKey) {
        if (userKey.isNotBlank()){
            vm.loadFriendsList(userKey)
            vm.loadIncomingRequests(userKey)
            vm.loadOutgoingRequests(userKey)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage friends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Your friend code
            Card(shape = MaterialTheme.shapes.large) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your friend code", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(userKey, style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(userKey)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
            }

            if (incoming.isNotEmpty()) {
                Text("Friend requests", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(incoming, key = { it.fromKey }) { req ->
                        Card(shape = MaterialTheme.shapes.large) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(req.fromUsername, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    req.fromKey,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        vm.declineFriendRequest(userKey, req.fromKey) { ok, msg ->
                                            if (ok) {
                                                vm.loadIncomingRequests(userKey)
                                            } else {
                                                error = msg ?: "Decline failed"
                                            }
                                        }
                                    }) { Text("Decline") }

                                    Spacer(Modifier.width(8.dp))

                                    Button(onClick = {
                                        vm.acceptFriendRequest(userKey, req.fromKey) { ok, msg ->
                                            if (ok) {
                                                // refresh both lists; friend will now appear under Friends
                                                vm.loadIncomingRequests(userKey)
                                                vm.loadFriendsList(userKey)
                                            } else {
                                                error = msg ?: "Accept failed"
                                            }
                                        }
                                    }) { Text("Accept") }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            if (outgoing.isNotEmpty()) {
                Text("Sent requests", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(outgoing, key = { it.toKey }) { req ->
                        Card(shape = MaterialTheme.shapes.large) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(req.toKey, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Pending",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(onClick = {
                                    vm.cancelFriendRequest(userKey, req.toKey) { ok, msg ->
                                        if (ok) {
                                            vm.loadOutgoingRequests(userKey)
                                        } else {
                                            error = msg ?: "Cancel failed"
                                        }
                                    }
                                }) { Text("Cancel") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            Text("Friends", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            if (friends.isEmpty()) {
                Text("No friends yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(friends, key = { it.key }) { f ->
                        Card(shape = MaterialTheme.shapes.large) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(f.username, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(2.dp))
                                    Text(f.key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    vm.removeFriendMutual(userKey, f.key) { ok, msg ->
                                        if (ok) vm.loadFriendsList(userKey) else error = msg ?: "Remove failed"
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
