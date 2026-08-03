package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AppViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val currentToken by viewModel.githubToken.collectAsStateWithLifecycle()
    val currentRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val currentEvent by viewModel.githubEvent.collectAsStateWithLifecycle()
    val currentServer by viewModel.apiServer.collectAsStateWithLifecycle()
    val currentBotToken by viewModel.botTokenFlow.collectAsStateWithLifecycle()

    var token by remember(currentToken) { mutableStateOf(currentToken) }
    var repo by remember(currentRepo) { mutableStateOf(currentRepo) }
    var event by remember(currentEvent) { mutableStateOf(currentEvent) }
    var server by remember(currentServer) { mutableStateOf(currentServer) }
    var botToken by remember(currentBotToken) { mutableStateOf(currentBotToken) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Environment Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("GitHub Token (ghp_...)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = DividerDark,
                    focusedLabelColor = PrimaryPurple,
                    unfocusedLabelColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                label = { Text("Telegram Bot Token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = DividerDark,
                    focusedLabelColor = PrimaryPurple,
                    unfocusedLabelColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = repo,
                onValueChange = { repo = it },
                label = { Text("GitHub Repository") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = DividerDark,
                    focusedLabelColor = PrimaryPurple,
                    unfocusedLabelColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = event,
                onValueChange = { event = it },
                label = { Text("Dispatch Event Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = DividerDark,
                    focusedLabelColor = PrimaryPurple,
                    unfocusedLabelColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Custom API Server URL (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = DividerDark,
                    focusedLabelColor = PrimaryPurple,
                    unfocusedLabelColor = TextSecondary
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    viewModel.saveSettings(token, repo, event, server, botToken)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Configuration", color = OnPrimaryPurple)
            }
        }
    }
}
