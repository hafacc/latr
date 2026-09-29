package io.hafa.latr.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hafa.latr.ui.theme.LatrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsBottomSheet(
    loadCounts: suspend () -> List<Long>?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val counts by produceState<List<Long>?>(null) { value = loadCounts() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(text = "Stats", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            counts?.let { PickRankChart(it) } ?: Spacer(modifier = Modifier.height(184.dp))
        }
    }
}

/** One column per pick position: 0 is "none", 1..20 the rank; every bar carries its count. */
@Composable
fun PickRankChart(counts: List<Long>, modifier: Modifier = Modifier) {
    val max = maxOf(1L, counts.maxOrNull() ?: 0L)
    val labelStyle = TextStyle(fontSize = 10.sp, lineHeight = 10.sp, fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            counts.forEach { count ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "$count", style = labelStyle, color = labelColor, softWrap = false)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .widthIn(max = 24.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = count.coerceAtLeast(0L).toFloat() / max)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            counts.indices.forEach { position ->
                Text(
                    text = "$position",
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.outline,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PickRankChartPreview() {
    LatrTheme {
        PickRankChart(listOf(12L, 40L, 22L, 9L, 5L, 3L, 1L) + List(14) { 0L })
    }
}
