package dev.eduarddragu.anotherreminderapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Horizontal gutter of every screen. */
val Gutter = 20.dp

fun Modifier.gutter(): Modifier = padding(horizontal = Gutter)

/**
 * A screen: a list that fills the window and scrolls behind the status bar, then a bar of actions
 * pinned at the bottom, above the gesture bar and the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Screen(bottomBar: @Composable RowScope.() -> Unit, state: LazyListState = rememberLazyListState(), barWithKeyboard: Boolean = true, content: LazyListScope.() -> Unit) {
  val focus = LocalFocusManager.current
  // A tap on empty space takes the focus away, which closes an inline field; taps on controls are theirs.
  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(start = Gutter, end = Gutter, top = top + 16.dp, bottom = 16.dp), content = content)
    // Without the bar while typing ([barWithKeyboard] false): on Home it would sit on the keyboard and
    // cover the card being written in. Editors keep it, Save is there.
    if (barWithKeyboard || !WindowInsets.isImeVisible) {
      Row(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().gutter().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = bottomBar,
      )
    } else {
      Spacer(Modifier.imePadding())
    }
  }
}
