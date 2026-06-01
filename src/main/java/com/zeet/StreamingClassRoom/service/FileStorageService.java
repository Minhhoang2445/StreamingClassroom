package com.zeet.StreamingClassRoom.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.zeet.StreamingClassRoom.exception.BadRequestException;

@Service
public class FileStorageService {

    @Value("${app.upload.chat-dir}")
    private String chatUploadDir;

    @Value("${app.upload.max-file-size}")
    private long maxFileSize;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    public StoredFile storeChatFile(String sessionId, MultipartFile file) {
        validateFile(file);

        String originalFileName = cleanFileName(file.getOriginalFilename());
        String extension = getExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + extension;

        try {
            Path sessionDir = Path.of(chatUploadDir, sessionId);
            Files.createDirectories(sessionDir);

            Path targetPath = sessionDir.resolve(storedFileName).normalize();

            file.transferTo(targetPath);

            return new StoredFile(
                    originalFileName,
                    targetPath.toString(),
                    file.getContentType(),
                    file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        if (file.getSize() > maxFileSize) {
            throw new BadRequestException("File size exceeds limit");
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("File type is not allowed: " + contentType);
        }
    }

    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown";
        }

        return Path.of(fileName).getFileName().toString();
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf(".");
        if (index == -1) {
            return "";
        }

        return fileName.substring(index);
    }

    public record StoredFile(
            String originalFileName,
            String filePath,
            String fileType,
            Long fileSize
    ) {
    }
}