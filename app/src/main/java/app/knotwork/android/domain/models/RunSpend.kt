package app.knotwork.android.domain.models

/**
 * What a run tree has already charged against its ceilings, and how far those
 * ceilings have been raised for it.
 *
 * Read once, at the top of a run, to seed [RunBudgetLedger]: zeroes for a fresh
 * run, and the previous attempt's totals for a resumed one — which is what
 * makes a ceiling bind across a park and resume instead of restarting.
 *
 * The extension counts travel with the spend because they are read at the same
 * moment and for the same reason. A run resumed after the user answered a
 * ceiling pause carries both halves of the answer: the spend says how much it
 * has used, the extension says how much it was allowed. Seeding one without the
 * other would rebuild a ledger that breaches again on its first node — the run
 * would re-ask the question the user has just answered, forever.
 *
 * A named type rather than a tuple of integers because the numbers are not
 * interchangeable and a caller reading them positionally would have no way to
 * notice getting them the wrong way round.
 *
 * @property steps Node executions charged to the tree.
 * @property tokens Tokens charged to the tree.
 * @property stepCeilingExtensions How many extra portions of the step ceiling
 *   the user has granted this run tree. Zero for every run that never asked.
 * @property tokenCeilingExtensions How many extra portions of the token ceiling
 *   the user has granted this run tree.
 */
data class RunSpend(
    val steps: Int = 0,
    val tokens: Int = 0,
    val stepCeilingExtensions: Int = 0,
    val tokenCeilingExtensions: Int = 0,
)
