package com.vedha.urlshortener.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates PNG QR codes for short URLs using ZXing, and caches the encoded
 * PNG bytes in Redis (as base64) so repeated requests for the same code's QR
 * don't re-run the encoding matrix math every time.
 *
 * The Redis cache read/write are both best-effort: if Redis is down, QR
 * generation still works, it's just slower (re-encoded on every request)
 * instead of failing outright.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private static final String QR_CACHE_PREFIX = "qr:";
    private static final int QR_SIZE = 300;

    private final RedisTemplate<String, Object> redisTemplate;

    public byte[] generatePng(String targetUrl, String cacheKey) throws WriterException, IOException {
        String cached = readFromCache(cacheKey);
        if (cached != null) {
            return Base64.getDecoder().decode(cached);
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(targetUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        byte[] pngBytes = out.toByteArray();

        writeToCache(cacheKey, pngBytes);

        return pngBytes;
    }

    private String readFromCache(String cacheKey) {
        try {
            return (String) redisTemplate.opsForValue().get(QR_CACHE_PREFIX + cacheKey);
        } catch (Exception ex) {
            log.warn("Redis unavailable for QR cache read (key={}), regenerating: {}", cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeToCache(String cacheKey, byte[] pngBytes) {
        try {
            redisTemplate.opsForValue().set(
                    QR_CACHE_PREFIX + cacheKey,
                    Base64.getEncoder().encodeToString(pngBytes),
                    Duration.ofDays(7));
        } catch (Exception ex) {
            log.warn("Redis unavailable for QR cache write (key={}): {}", cacheKey, ex.getMessage());
        }
    }
}
