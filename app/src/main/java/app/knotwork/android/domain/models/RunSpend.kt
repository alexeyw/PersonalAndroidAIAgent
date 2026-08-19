package app.knotwork.android.domain.models

/**
 * What a run tree has already charged against its ceilings.
 *
 * Read once, at the top of a run, to seed [RunBudgetLedger]: zero for a fresh
 * run, and the previous attempt's totals for a resumed one — which is what
 * makes a ceiling bind across a park and resume instead of restarting.
 *
 * A named type rather than a pair of integers because the two numbers are not
 * interchangeable and a caller reading them positionally would have no way to
 * notice getting them the wrong way round.
 *
 * @property steps Node executions charged to the tree.
 * @property tokens Tokens charged to the tree.
 */
data class RunSpend(val steps: Int = 0, val tokens: Int = 0)
