package app.knotwork.android.domain.models

/**
 * A complete snapshot of the agent workspace for the Files screen: the file
 * entries plus the storage-budget usage, fetched together so the list and the
 * quota indicator are always consistent with each other.
 *
 * @property files The workspace's regular files, path-sorted (the order returned
 *   by [app.knotwork.android.domain.services.AgentWorkspace.list]).
 * @property usage The storage-budget snapshot at the time of listing.
 */
data class WorkspaceListing(val files: List<WorkspaceFile>, val usage: WorkspaceUsage)
