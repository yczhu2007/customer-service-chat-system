package com.example.customerservice.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSessionTagMapperXmlContractTest {

    @Test
    void mapperXmlDeclaresCompositeIdAndCrudContracts() throws IOException {
        String resourcePath = "mapper/ChatSessionTagMapper.xml";
        try (InputStream inputStream =
                     Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Expected mapper resource to be on the classpath");

            String xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("<mapper namespace=\"com.example.customerservice.mapper.ChatSessionTagMapper\">"));
            assertTrue(xml.contains("<resultMap id=\"ChatSessionTagResultMap\""));
            assertTrue(xml.contains("<id property=\"sessionId\" column=\"session_id\"/>"));
            assertTrue(xml.contains("<id property=\"tag\" column=\"tag\"/>"));
            assertTrue(xml.contains("<insert id=\"insert\""));
            assertTrue(xml.contains("<delete id=\"deleteBySessionId\""));
            assertTrue(xml.contains("<select id=\"selectBySessionId\""));
        }
    }
}
