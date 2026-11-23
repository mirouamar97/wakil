package app.wakil.agent.data

import app.wakil.agent.data.local.backup.BackupCodec
import app.wakil.agent.data.local.backup.BackupCounts
import app.wakil.agent.data.local.backup.BackupDecodeResult
import app.wakil.agent.data.local.backup.BackupError
import app.wakil.agent.data.local.backup.BackupRecordIds
import app.wakil.agent.data.local.backup.ConversationBackupItem
import app.wakil.agent.data.local.backup.GoalBackupItem
import app.wakil.agent.data.local.backup.MessageBackupItem
import app.wakil.agent.data.local.backup.NoteBackupItem
import app.wakil.agent.data.local.backup.NoteLinkBackupItem
import app.wakil.agent.data.local.backup.ProjectBackupItem
import app.wakil.agent.data.local.backup.TaskBackupItem
import app.wakil.agent.data.local.backup.WakilBackupPayload
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun id(number: Long) = UUID(0L, number).toString()

    private fun sample() = WakilBackupPayload(
        goals = listOf(GoalBackupItem(id(1), "Goal", createdAt = 1, updatedAt = 2)),
        projects = listOf(ProjectBackupItem(id(2), id(1), "Project", createdAt = 1, updatedAt = 2)),
        tasks = listOf(TaskBackupItem(id(3), id(2), id(1), "Task", createdAt = 1, updatedAt = 2)),
        notes = listOf(NoteBackupItem(id(4), "Note", "Body", createdAt = 1, updatedAt = 2)),
        noteLinks = listOf(NoteLinkBackupItem(id(5), id(4), "TASK", id(3), 2)),
        conversations = listOf(ConversationBackupItem(id(6), "Chat", 1, 2)),
        messages = listOf(MessageBackupItem(id(7), id(6), "user", "Hello", createdAt = 2)),
    )

    @Test
    fun roundTripPreservesRecordsAndCounts() {
        val payload = sample()
        val decoded = BackupCodec.decode(BackupCodec.encode(payload))

        assertTrue(decoded is BackupDecodeResult.Valid)
        assertEquals(payload, (decoded as BackupDecodeResult.Valid).payload)
        assertEquals(7, BackupCounts.from(payload).total)
    }

    @Test
    fun rejectsMalformedAndUnsupportedBackups() {
        assertEquals(
            BackupError.INVALID_JSON,
            (BackupCodec.decode("{broken") as BackupDecodeResult.Invalid).error
        )
        assertEquals(
            BackupError.UNSUPPORTED_VERSION,
            (BackupCodec.decode(BackupCodec.encode(sample().copy(schemaVersion = 2))) as BackupDecodeResult.Invalid).error
        )
        assertEquals(
            BackupError.INVALID_APP,
            (BackupCodec.decode(BackupCodec.encode(sample().copy(app = "Other"))) as BackupDecodeResult.Invalid).error
        )
    }

    @Test
    fun rejectsDuplicateIdsAndInvalidFields() {
        val duplicate = sample().copy(goals = listOf(
            GoalBackupItem(id(1), "First", createdAt = 1, updatedAt = 1),
            GoalBackupItem(id(1), "Second", createdAt = 1, updatedAt = 1),
        ))
        assertEquals(BackupError.INVALID_DATA, (BackupCodec.decode(BackupCodec.encode(duplicate)) as BackupDecodeResult.Invalid).error)

        val invalid = sample().copy(goals = listOf(
            GoalBackupItem(id(1), "", progress = 101, createdAt = 1, updatedAt = 1)
        ))
        assertEquals(BackupError.INVALID_DATA, (BackupCodec.decode(BackupCodec.encode(invalid)) as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun flagsBrokenReferencesButAllowsReferencesToExistingDeviceRecords() {
        val payload = sample()
        assertEquals(
            BackupError.BROKEN_REFERENCE,
            BackupCodec.validateReferences(payload.copy(projects = emptyList()), BackupRecordIds())
        )
        assertEquals(
            null,
            BackupCodec.validateReferences(
                payload.copy(goals = emptyList(), projects = emptyList()),
                BackupRecordIds(goals = setOf(id(1)), projects = setOf(id(2)))
            )
        )
    }

    @Test
    fun countsConflictsWithoutChangingAnyRecords() {
        val payload = sample()
        val collisions = BackupCounts.from(payload).collisionsWith(
            payload,
            BackupRecordIds(goals = setOf(id(1)), messages = setOf(id(7)))
        )
        assertEquals(2, collisions)
    }
}
