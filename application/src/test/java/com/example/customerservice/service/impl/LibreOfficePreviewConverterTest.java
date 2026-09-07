package com.example.customerservice.service.impl;

import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.exception.OfficePreviewException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LibreOfficePreviewConverterTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsLegacyOfficeFileAndRemovesItsTemporaryDirectory() throws Exception {
        MinioAttachmentProperties properties = properties();
        byte[] pdf = "%PDF-1.7\npreview".getBytes();
        AtomicReference<List<String>> invokedCommand = new AtomicReference<>();
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(properties, (command, workingDirectory, timeout) -> {
            invokedCommand.set(command);
            Files.write(workingDirectory.resolve("source.pdf"), pdf);
            return 0;
        });

        byte[] result = converter.convertToPdf("schedule.xls", new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertArrayEquals(pdf, result);
        assertTrue(invokedCommand.get().stream().anyMatch(value -> value.startsWith("-env:UserInstallation=")));
        assertFalse(Files.list(tempDir).findAny().isPresent());
    }

    @Test
    void convertsPowerPointFilesViaSamePipeline() throws Exception {
        MinioAttachmentProperties properties = properties();
        byte[] pdf = "%PDF-1.7\npreview".getBytes();
        AtomicReference<List<String>> invokedCommand = new AtomicReference<>();
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(properties, (command, workingDirectory, timeout) -> {
            invokedCommand.set(command);
            Files.write(workingDirectory.resolve("source.pdf"), pdf);
            return 0;
        });

        byte[] result = converter.convertToPdf("slides.pptx", new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertArrayEquals(pdf, result);
        assertTrue(invokedCommand.get().stream().anyMatch(value -> value.endsWith("source.pptx")));
    }

    @Test
    void rejectsInvalidConverterOutput() {
        MinioAttachmentProperties properties = properties();
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(properties, (command, workingDirectory, timeout) -> {
            Files.writeString(workingDirectory.resolve("source.pdf"), "not a pdf");
            return 0;
        });

        assertThrows(OfficePreviewException.class,
                () -> converter.convertToPdf("report.doc", new ByteArrayInputStream(new byte[]{1})));
    }

    @Test
    void rejectsUnsupportedSourceExtensionBeforeStartingConverter() {
        MinioAttachmentProperties properties = properties();
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(properties, (command, workingDirectory, timeout) -> 0);

        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToPdf("report.docx", new ByteArrayInputStream(new byte[]{1})));
    }

    private MinioAttachmentProperties properties() {
        MinioAttachmentProperties properties = new MinioAttachmentProperties();
        properties.getPreview().setTempRoot(tempDir);
        properties.getPreview().setCommand("soffice");
        properties.getPreview().setTimeoutSeconds(2);
        properties.getPreview().setMaxOutputBytes(1024);
        return properties;
    }
}
