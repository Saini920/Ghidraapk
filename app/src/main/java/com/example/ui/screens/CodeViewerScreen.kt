package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.AppViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(
    projectId: Int,
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val projectFlow = remember(projectId) { viewModel.getProject(projectId) }
    val project by projectFlow.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Loading...", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteProject(projectId)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (project == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PrimaryPurple
                )
            } else if (project?.status == "DECOMPILING") {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = PrimaryPurple)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Decompiling on Cloud Engine...", color = TextSecondary)
                }
            } else if (project?.status == "FAILED") {
                Text(
                    "Decompilation failed. Check GitHub Token and logs.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val sourceCode = project?.sourceCode ?: "// No source code available."
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceDark // Dark editor background
                ) {
                    Text(
                        text = syntaxHighlight(sourceCode),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

// Very basic C syntax highlighting
fun syntaxHighlight(code: String) = buildAnnotatedString {
    val keywords = listOf("int", "void", "char", "return", "if", "else", "#include")
    
    var currentIndex = 0
    while (currentIndex < code.length) {
        var matchedKeyword = false
        for (keyword in keywords) {
            if (code.startsWith(keyword, currentIndex)) {
                // Check if it's a whole word match
                val nextChar = if (currentIndex + keyword.length < code.length) code[currentIndex + keyword.length] else ' '
                if (!nextChar.isLetterOrDigit() && nextChar != '_') {
                    withStyle(style = SpanStyle(color = Color(0xFFA8C7FF))) { // AccentBlue for keywords
                        append(keyword)
                    }
                    currentIndex += keyword.length
                    matchedKeyword = true
                    break
                }
            }
        }
        
        if (!matchedKeyword) {
            if (code.startsWith("\"", currentIndex)) {
                val endQuote = code.indexOf("\"", currentIndex + 1)
                if (endQuote != -1) {
                    withStyle(style = SpanStyle(color = Color(0xFFD0BCFF))) { // PrimaryPurple for strings
                        append(code.substring(currentIndex, endQuote + 1))
                    }
                    currentIndex = endQuote + 1
                } else {
                    withStyle(style = SpanStyle(color = TextPrimary)) {
                        append(code[currentIndex].toString())
                    }
                    currentIndex++
                }
            } else {
                withStyle(style = SpanStyle(color = TextPrimary)) { 
                    append(code[currentIndex].toString())
                }
                currentIndex++
            }
        }
    }
}
