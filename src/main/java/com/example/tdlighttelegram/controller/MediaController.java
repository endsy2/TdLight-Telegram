package com.example.tdlighttelegram.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller for serving media files stored locally
 */
@Slf4j
@RestController
@RequestMapping("/api/telegram/media")
@CrossOrigin(origins = "*")
public class MediaController {

    private static final String MEDIA_STORAGE_PATH = "media-files";

    /**
     * Serve photo files
     */
    @GetMapping("/photos/{fileName}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String fileName) {
        return serveFile("photos", fileName, "image/jpeg");
    }

    /**
     * Serve video files
     */
    @GetMapping("/videos/{fileName}")
    public ResponseEntity<Resource> getVideo(@PathVariable String fileName) {
        return serveFile("videos", fileName, "video/mp4");
    }

    /**
     * Serve voice files
     */
    @GetMapping("/voices/{fileName}")
    public ResponseEntity<Resource> getVoice(@PathVariable String fileName) {
        return serveFile("voices", fileName, "audio/ogg");
    }

    /**
     * Serve audio files
     */
    @GetMapping("/audios/{fileName}")
    public ResponseEntity<Resource> getAudio(@PathVariable String fileName) {
        return serveFile("audios", fileName, "audio/mpeg");
    }

    /**
     * Serve document files
     */
    @GetMapping("/documents/{fileName}")
    public ResponseEntity<Resource> getDocument(@PathVariable String fileName) {
        return serveFile("documents", fileName, "application/octet-stream");
    }

    /**
     * Serve thumbnail files
     */
    @GetMapping("/thumbnails/{fileName}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String fileName) {
        return serveFile("thumbnails", fileName, "image/jpeg");
    }

    /**
     * Serve profile photo files
     */
    @GetMapping("/profile-photos/{fileName}")
    public ResponseEntity<Resource> getProfilePhoto(@PathVariable String fileName) {
        return serveFile("profile-photos", fileName, "image/jpeg");
    }

    /**
     * Generic method to serve files with proper headers for streaming
     */
    private ResponseEntity<Resource> serveFile(String category, String fileName, String contentType) {
        try {
            // Validate filename to prevent directory traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                log.warn("Invalid filename requested: {}", fileName);
                return ResponseEntity.badRequest().build();
            }

            // Build file path
            Path filePath = Paths.get(MEDIA_STORAGE_PATH, category, fileName);
            
            // Check if file exists
            if (!Files.exists(filePath)) {
                log.warn("File not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Create resource
            Resource resource = new FileSystemResource(filePath);
            
            // Get file size for Content-Length header
            long fileSize = Files.size(filePath);

            // Build response with proper headers for streaming
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000") // Cache for 1 year
                    .body(resource);

        } catch (IOException e) {
            log.error("Error serving file: {}/{}", category, fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
