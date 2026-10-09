package dev.eduarddragu.anotherreminderapp.domain

/** What closing an inline edit of a checklist item does with what was typed. */
object Checklist {
  enum class Edit { KEEP, RENAME, REMOVE }

  /**
   * [open]: whether this item's edit is still the one open. Closing is reported twice (focus lost, then the
   * keyboard gone), and the second time the field is already empty: only the first may act, or an emptied
   * draft would remove the item.
   */
  fun close(open: Boolean, old: String, typed: String): Edit =
    when {
      !open -> Edit.KEEP
      typed.trim() == old -> Edit.KEEP
      typed.isBlank() -> Edit.REMOVE
      else -> Edit.RENAME
    }
}
