# Notifications

- Single notification channel, "Budget Alerts".
- `core-domain`'s `evaluateBudget` result (see
  [Design/Core/05-domain-logic.md](../Core/05-domain-logic.md#budget-status-evaluation))
  is compared against the previously-stored status after every write; a
  transition into `NEARING` or `OVER` (not just "currently in that state")
  fires a notification, so re-opening the app doesn't spam a notification
  for a status that hasn't changed.
- Notification text distinguishes household ("This week's grocery budget
  is over by ₹X") from trip ("Goa Trip is nearing its budget") contexts,
  reusing the same evaluation function but different copy templates.
