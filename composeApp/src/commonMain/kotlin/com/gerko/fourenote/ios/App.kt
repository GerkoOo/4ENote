package com.gerko.fourenote.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.InkPage

/** The iPad app: a list of sample notes on the left, the selected note's first page on the right. */
@Composable
fun App() {
    MaterialTheme {
        var selected by remember { mutableStateOf(0) }
        Row(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                tonalElevation = 2.dp,
            ) {
                LazyColumn {
                    itemsIndexed(SampleNotes.all) { index, note ->
                        val isSelected = index == selected
                        Text(
                            text = note.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { selected = index }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center,
            ) {
                val note = SampleNotes.all[selected]
                val page: InkPage = note.document.pages.first()
                PageView(
                    page = page,
                    paper = note.document.paper,
                    modifier = Modifier
                        .padding(24.dp)
                        .aspectRatio(page.width / page.height),
                )
            }
        }
    }
}
