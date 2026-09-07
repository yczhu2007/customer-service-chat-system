package com.example.customerservice.service.impl;

import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.exception.OfficePreviewException;
import com.example.customerservice.service.OfficePreviewConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LibreOfficePreviewConverter implements OfficePreviewConverter {
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private final MinioAttachmentProperties.Preview properties;
    private final ProcessRunner processRunner;

    @Autowired
    public LibreOfficePreviewConverter(MinioAttachmentProperties properties) {
        this(properties, LibreOfficePreviewConverter::runProcess);
    }

    LibreOfficePreviewConverter(MinioAttachmentProperties properties, ProcessRunner processRunner) {
        this.properties = properties.getPreview();
        this.processRunner = processRunner;
    }

    @Override
    public byte[] convertToPdf(String sourceName, InputStream source) {
        String extension = extensionOf(sourceName);
        if (!"doc".equals(extension) && !"xls".equals(extension)) {
            throw new IllegalArgumentException("仅支持预览 DOC 和 XLS 文件");
        }

        Path workDirectory = null;
        try {
            workDirectory = createWorkDirectory();
            Path input = workDirectory.resolve("source." + extension);
            Files.copy(source, input, StandardCopyOption.REPLACE_EXISTING);
            List<String> command = List.of(
                    properties.getCommand(), "-env:UserInstallation=" + workDirectory.resolve("profile").toUri(),
                    "--headless", "--convert-to", "pdf",
                    "--outdir", workDirectory.toString(), input.toString()
            );
            int exitCode = processRunner.run(
                    command, workDirectory, Duration.ofSeconds(properties.getTimeoutSeconds())
            );
            if (exitCode != 0) throw new OfficePreviewException("文档预览转换失败");
            return readValidatedPdf(workDirectory.resolve("source.pdf"));
        } catch (OfficePreviewException | IllegalArgumentException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OfficePreviewException("文档预览转换已中断", exception);
        } catch (IOException exception) {
            throw new OfficePreviewException("文档预览转换失败", exception);
        } finally {
            deleteWorkDirectory(workDirectory);
        }
    }

    private Path createWorkDirectory() throws IOException {
        Path root = properties.getTempRoot();
        if (root == null) return Files.createTempDirectory("attachment-preview-");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, "attachment-preview-");
    }

    private byte[] readValidatedPdf(Path output) throws IOException {
        if (!Files.isRegularFile(output)) throw new OfficePreviewException("文档预览转换未生成 PDF");
        long size = Files.size(output);
        if (size <= PDF_SIGNATURE.length || size > properties.getMaxOutputBytes()) {
            throw new OfficePreviewException("文档预览转换结果大小异常");
        }
        byte[] content = Files.readAllBytes(output);
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (content[i] != PDF_SIGNATURE[i]) throw new OfficePreviewException("文档预览转换结果无效");
        }
        return content;
    }

    private void deleteWorkDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("清理文档预览临时文件失败：{}", path, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("清理文档预览临时目录失败：{}", directory, exception);
        }
    }

    private String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static int runProcess(List<String> command, Path workingDirectory, Duration timeout)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(workingDirectory.resolve("libreoffice.log").toFile())
                .start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new OfficePreviewException("文档预览转换超时");
        }
        return process.exitValue();
    }

    @FunctionalInterface
    interface ProcessRunner {
        int run(List<String> command, Path workingDirectory, Duration timeout)
                throws IOException, InterruptedException;
    }
}
