package app.wakil.agent.domain.rules

object SecurityPolicy {

    /**
     * Determines whether an action requires explicit human approval dialog.
     * Hard Rule:
     * - Deleting any record ALWAYS requires approval.
     * - Changing a deadline/due date ALWAYS requires approval.
     * - External messaging / phone actions (L2) are strictly blocked in Release 1.
     * - Code modification (L3) is strictly blocked in Release 1.
     */
    fun requiresUserApproval(
        actionType: String,
        targetField: String? = null
    ): Boolean {
        if (actionType.equals("DELETE", ignoreCase = true)) return true
        if (actionType.equals("UPDATE", ignoreCase = true) && targetField?.contains("dueDate", ignoreCase = true) == true) return true
        if (actionType.equals("UPDATE", ignoreCase = true) && targetField?.contains("deadline", ignoreCase = true) == true) return true
        return false
    }

    /**
     * Release 1 constraint: Only L0 (Read) and L1 (Local user data) are permitted.
     * L2 (Phone/messaging) and L3 (Code modification) are completely disabled.
     */
    fun isLevelPermittedInRelease1(level: PermissionLevel): Boolean {
        return level == PermissionLevel.L0 || level == PermissionLevel.L1
    }
}
