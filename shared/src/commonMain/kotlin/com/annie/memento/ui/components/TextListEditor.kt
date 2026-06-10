package com.annie.memento.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// examples list for cards
@Composable
fun TextListEditor(
    values: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Examples",
    addLabel: String = "Add example",
    placeholder: String = "Example",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { updated -> onChange(values.mapIndexed { i, v -> if (i == index) updated else v }) },
                    placeholder = { Text("$placeholder ${index + 1}") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onChange(values.filterIndexed { i, _ -> i != index }) }) {
                    Text("✕", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        MementoOutlineButton(addLabel, onClick = { onChange(values + "") }, leading = "+")
    }
}
