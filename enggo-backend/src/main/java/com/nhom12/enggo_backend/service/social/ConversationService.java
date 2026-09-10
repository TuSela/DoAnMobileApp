package com.nhom12.enggo_backend.service.social;

import com.nhom12.enggo_backend.dto.request.social.SendMessageRequest;
import com.nhom12.enggo_backend.dto.response.social.ConversationResponse;
import com.nhom12.enggo_backend.dto.response.social.MessageResponse;
import com.nhom12.enggo_backend.entity.identity.User;
import com.nhom12.enggo_backend.entity.social.Conversation;
import com.nhom12.enggo_backend.entity.social.Message;
import com.nhom12.enggo_backend.entity.social.Participant;
import com.nhom12.enggo_backend.exception.AppException;
import com.nhom12.enggo_backend.exception.ErrorCode;
import com.nhom12.enggo_backend.repository.UserRepository;
import com.nhom12.enggo_backend.repository.social.ConversationRepository;
import com.nhom12.enggo_backend.repository.social.MessageRepository;
import com.nhom12.enggo_backend.repository.social.ParticipantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationService {

    ConversationRepository conversationRepository;
    ParticipantRepository participantRepository;
    MessageRepository messageRepository;
    UserRepository userRepository;
    SimpMessagingTemplate messagingTemplate; // ← THÊM để broadcast realtime

    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(Integer currentUserId, Integer targetUserId) {
        User currentUser = getUser(currentUserId);
        User targetUser = getUser(targetUserId);

        return conversationRepository.findPrivateConversation(currentUser, targetUser)
                .map(this::toConversationResponse)
                .orElseGet(() -> {
                    Conversation conversation = Conversation.builder()
                            .type("PRIVATE")
                            .createdBy(currentUser)
                            .build();
                    conversationRepository.save(conversation);

                    participantRepository.save(Participant.builder()
                            .conversation(conversation).user(currentUser).role("MEMBER").build());
                    participantRepository.save(Participant.builder()
                            .conversation(conversation).user(targetUser).role("MEMBER").build());

                    return toConversationResponse(conversation);
                });
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(Integer currentUserId) {
        User currentUser = getUser(currentUserId);
        return participantRepository.findByUser(currentUser).stream()
                .map(p -> toConversationResponse(p.getConversation()))
                .collect(Collectors.toList());
    }


    
    @Transactional
    public MessageResponse sendMessage(Integer currentUserId, Integer conversationId, SendMessageRequest request) {
        User sender = getUser(currentUserId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (!participantRepository.existsByConversationAndUser(conversation, sender))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .type(request.getType() != null ? request.getType() : "TEXT")
                .build();
        messageRepository.save(message);

        conversation.setLastMessage(message);
        conversationRepository.save(conversation);

        MessageResponse response = toMessageResponse(message);

        // Broadcast realtime tới tất cả participant trong conversation
        List<Participant> participants = participantRepository.findByConversation(conversation);
        for (Participant p : participants) {
            // Gửi tới /user/{username}/queue/chat
            messagingTemplate.convertAndSendToUser(
                    p.getUser().getUsername(),
                    "/queue/chat",
                    buildChatEvent(conversationId, response)
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Integer currentUserId, Integer conversationId, int page, int size) {
        User currentUser = getUser(currentUserId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (!participantRepository.existsByConversationAndUser(conversation, currentUser))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        return messageRepository
                .findByConversationOrderByCreatedAtDesc(conversation, PageRequest.of(page, size))
                .map(this::toMessageResponse);
    }

    // ---- Helper ----

    private User getUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private Map<String, Object> buildChatEvent(Integer conversationId, MessageResponse msg) {
        Map<String, Object> event = new HashMap<>();
        event.put("conversationId", conversationId);
        event.put("id", msg.getId());
        event.put("senderId", msg.getSenderId());
        event.put("senderUsername", msg.getSenderUsername());
        event.put("content", msg.getContent());
        event.put("type", msg.getType());
        event.put("createdAt", msg.getCreatedAt());
        return event;
    }

    private ConversationResponse toConversationResponse(Conversation c) {
        String lastContent = null;
        if (c.getLastMessage() != null) lastContent = c.getLastMessage().getContent();
        return ConversationResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .type(c.getType())
                .lastMessageContent(lastContent)
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .senderId(m.getSender().getId())
                .senderUsername(m.getSender().getUsername())
                .content(m.getContent())
                .type(m.getType())
                .createdAt(m.getCreatedAt())
                .build();
    }
}