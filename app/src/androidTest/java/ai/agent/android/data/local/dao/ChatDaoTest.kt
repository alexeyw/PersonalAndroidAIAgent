package ai.agent.android.data.local.dao

import ai.agent.android.data.local.AppDatabase
import ai.agent.android.data.local.models.ChatMessageEntity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var chatDao: ChatDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        chatDao = db.chatDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetMessages() = runBlocking {
        val message1 = ChatMessageEntity(sessionId = "session1", role = "USER", content = "Hello", timestamp = 1000L)
        val message2 = ChatMessageEntity(sessionId = "session1", role = "AGENT", content = "Hi", timestamp = 2000L)
        val messageOther = ChatMessageEntity(sessionId = "session2", role = "USER", content = "Test", timestamp = 3000L)

        chatDao.insertMessage(message1)
        chatDao.insertMessage(message2)
        chatDao.insertMessage(messageOther)

        val messages = chatDao.getMessagesBySessionId("session1").first()

        assertEquals(2, messages.size)
        assertEquals("Hello", messages[0].content)
        assertEquals("Hi", messages[1].content)
    }

    @Test
    fun deleteSession() = runBlocking {
        val message1 = ChatMessageEntity(sessionId = "session1", role = "USER", content = "Hello", timestamp = 1000L)
        val message2 = ChatMessageEntity(sessionId = "session2", role = "USER", content = "Test", timestamp = 2000L)

        chatDao.insertMessage(message1)
        chatDao.insertMessage(message2)

        chatDao.deleteSession("session1")

        val session1Messages = chatDao.getMessagesBySessionId("session1").first()
        val session2Messages = chatDao.getMessagesBySessionId("session2").first()

        assertTrue(session1Messages.isEmpty())
        assertEquals(1, session2Messages.size)
    }
}
