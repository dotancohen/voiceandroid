package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.data.MissingData
import com.dotancohen.voiceandroid.viewmodel.MissingDataViewModel

/**
 * Settings → Calculate missing data.
 *
 * Says what is missing first and calculates only when asked, because reading the
 * length of every recording on the phone is work and the user should see what it
 * is for before it starts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingDataScreen(
    onBack: () -> Unit,
    viewModel: MissingDataViewModel = viewModel(),
) {
    val survey by viewModel.survey.collectAsState()
    val report by viewModel.report.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val currently by viewModel.currently.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Missing data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.look() }, enabled = !isWorking) {
                        Icon(Icons.Default.Refresh, contentDescription = "Count again")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Some facts about a recording are not known when it arrives: a file imported " +
                    "before lengths were recorded has none, and a recording copied without its " +
                    "dates has no creation date. None of it is lost — it can be read back off " +
                    "the file — and this calculates it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (error != null) {
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            val current = survey
            if (current == null) {
                Text("Counting...", style = MaterialTheme.typography.bodyMedium)
            } else if (!current.anythingMissing) {
                Text("Nothing is missing.", style = MaterialTheme.typography.titleMedium)
            } else {
                GapTable(current)
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isWorking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        currently.ifEmpty { "Working..." },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.calculate() },
                    enabled = (current?.totalCalculable ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Calculate what can be calculated")
                }
            }

            val done = report
            if (done != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("What was calculated", style = MaterialTheme.typography.titleMedium)
                        Text(
                            done.summary(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        for (detail in done.details) {
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { viewModel.forgetReport() }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row per gap: how many, and what it is.
 *
 * Top-aligned, because a description wraps to two or three lines and its count
 * belongs beside the first of them.
 */
@Composable
private fun GapTable(survey: MissingData.Survey) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (gap in survey.gaps) {
            if (gap.count == 0) continue
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = gap.count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(gap.description, style = MaterialTheme.typography.bodyMedium)
                    if (!gap.calculable) {
                        Text(
                            "Cannot be calculated: ${gap.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
