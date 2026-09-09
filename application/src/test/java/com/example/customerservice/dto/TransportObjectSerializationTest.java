package com.example.customerservice.dto;

import com.example.customerservice.common.Result;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportObjectSerializationTest {

    @Test
    void allTransportTypesImplementSerializable() {
        List<Class<?>> transportTypes = List.of(
                Result.class,
                AckRequest.class,
                AssignResult.class,
                ChatHistoryPage.class,
                ChatMessageDTO.class,
                ChatSessionDTO.class,
                EndSessionRequest.class,
                HistoryRequest.class,
                LoginRequest.class,
                LoginResponse.class,
                WebSocketTicketResponse.class,
                DeadLetterMessageVO.class,
                PageResult.class,
                MessageMutationResult.class,
                MessageReadResult.class,
                SupportTicketCreateDTO.class,
                SupportTicketUpdateDTO.class,
                SupportTicketVO.class,
                PermissionCreateDTO.class,
                PermissionUpdateDTO.class,
                PermissionVO.class,
                ReadMessagesRequest.class,
                RecallMessageRequest.class,
                RoleCreateDTO.class,
                RoleUpdateDTO.class,
                RoleVO.class,
                TransferSessionRequest.class,
                UserCreateDTO.class,
                UserUpdateDTO.class,
                UserVO.class
        );

        transportTypes.forEach(type -> assertTrue(
                Serializable.class.isAssignableFrom(type),
                () -> type.getSimpleName() + "必须实现Serializable"
        ));
    }

    @Test
    void historyResultCanBeSerializedWithItsMessageRecords() {
        ChatMessage message = new ChatMessage();
        message.setId("M001");
        ChatHistoryPage result = new ChatHistoryPage(
                List.of(message), 1, 20, null, false, 0, null, null, null
        );

        assertDoesNotThrow(() -> serialize(result));
        assertDoesNotThrow(() -> serialize(Result.success(result)));
    }

    @Test
    void readReceiptTransportContractsExposeWatermarkAndReadTime() {
        Set<String> historyFields = Arrays.stream(ChatHistoryPage.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        Set<String> receiptFields = Arrays.stream(MessageReadResult.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertTrue(historyFields.contains("counterpartLastReadMessageId"));
        assertTrue(historyFields.contains("counterpartLastReadMessageCreateTime"));
        assertTrue(historyFields.contains("counterpartLastReadAt"));
        assertTrue(receiptFields.contains("readAt"));
    }

    @Test
    void readMapperExposesCounterpartWatermarkQuery() {
        assertDoesNotThrow(() -> ChatMessageReadMapper.class.getMethod(
                "findLastReadWatermark",
                String.class,
                String.class
        ));
    }

    private static byte[] serialize(Object value) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
            return output.toByteArray();
        }
    }
}
