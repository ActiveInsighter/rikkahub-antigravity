package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Repository backed by [AgentDatabase], completely isolated from Chat's
 * [me.rerere.rikkahub.data.repository.ConversationRepository].
 */
class AgentRepository(
    private val conversationDao: AgentConversationDao,
    private val messageNodeDao: AgentMessageNodeDao,
) {
    fun observeConversations(sortOrder: ConversationSortOrder): Flow<List<Conversation>> {
        val source = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> conversationDao.getAllSortedByUpdateFlow()
            ConversationSortOrder.CREATE_TIME -> conversationDao.getAllSortedByCreateFlow()
        }
        return source.map { list ->
            list.map { entity ->
                Conversation(
                    id = Uuid.parse(entity.id),
                    assistantId = DEFAULT_ASSISTANT_ID,
                    title = entity.title,
                    messageNodes = emptyList(),
                    isPinned = entity.isPinned,
                    createAt = Instant.ofEpochMilli(entity.createAt),
                    updateAt = Instant.ofEpochMilli(entity.updateAt),
                )
            }
        }
    }

    suspend fun getConversation(id: Uuid): Conversation? {
        val entity = conversationDao.getById(id.toString()) ?: return null
        val nodes = messageNodeDao.getByConversationId(entity.id).map { nodeEntity ->
            val messages = runCatching {
                JsonInstant.decodeFromString<List<UIMessage>>(nodeEntity.messages)
            }.getOrDefault(emptyList())
            MessageNode(
                id = Uuid.parse(nodeEntity.id),
                messages = messages,
                selectIndex = nodeEntity.selectIndex.coerceIn(0, (messages.size - 1).coerceAtLeast(0)),
            )
        }
        return Conversation(
            id = Uuid.parse(entity.id),
            assistantId = DEFAULT_ASSISTANT_ID,
            title = entity.title,
            messageNodes = nodes,
            isPinned = entity.isPinned,
            createAt = Instant.ofEpochMilli(entity.createAt),
            updateAt = Instant.ofEpochMilli(entity.updateAt),
        )
    }

    suspend fun saveConversation(conversation: Conversation) {
        val convId = conversation.id.toString()
        val entity = AgentConversationEntity(
            id = convId,
            title = conversation.title,
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            isPinned = conversation.isPinned,
        )
        conversationDao.upsert(entity)
        val nodeEntities = conversation.messageNodes.mapIndexed { index, node ->
            AgentMessageNodeEntity(
                id = node.id.toString(),
                conversationId = convId,
                nodeIndex = index,
                messages = JsonInstant.encodeToString(node.messages),
                selectIndex = node.selectIndex,
            )
        }
        messageNodeDao.replaceForConversation(convId, nodeEntities)
    }

    suspend fun renameConversation(id: Uuid, title: String) {
        conversationDao.updateTitle(
            id = id.toString(),
            title = title,
            updateAt = Instant.now().toEpochMilli(),
        )
    }

    suspend fun setPinned(id: Uuid, pinned: Boolean) {
        conversationDao.updatePinned(id = id.toString(), isPinned = pinned)
    }

    suspend fun deleteConversation(id: Uuid) {
        conversationDao.deleteById(id.toString())
    }

    suspend fun deleteAllConversations() {
        conversationDao.deleteAll()
    }
}
