package dev.eduarddragu.anotherreminderapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The one title pattern of every screen: a mono accent label, then the title in the display serif. */
@Composable
fun ScreenTitle(label: String, title: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    SectionLabel(label)
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.displaySmall)
  }
}
