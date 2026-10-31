package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.UploadKind;
import com.example.hapjugakapi.dto.PresignRequest;
import com.example.hapjugakapi.dto.PresignResponse;
import com.example.hapjugakapi.exception.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {

    /** 브라우저는 WebP로 보내지만, 변환을 못 하는 환경을 위해 둘을 더 받는다. */
    private static final Map<String, String> ALLOWED = Map.of(
            "image/webp", "webp",
            "image/jpeg", "jpg",
            "image/png", "png"
    );

    private static final Set<String> ALLOWED_TYPES = ALLOWED.keySet();

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;
    private final long presignMinutes;

    public StorageService(S3Client s3,
                          S3Presigner presigner,
                          @Value("${app.s3.bucket}") String bucket,
                          @Value("${app.s3.public-base-url}") String publicBaseUrl,
                          @Value("${app.s3.presign-minutes}") long presignMinutes) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
        this.presignMinutes = presignMinutes;
    }

    /**
     * 업로드용 임시 주소를 발급한다. 파일은 서버를 거치지 않고 브라우저에서 S3로 직접 간다.
     *
     * contentType을 서명에 포함시키는 것이 중요하다.
     * 서명과 다른 타입으로 올리면 S3가 거부한다.
     */
    public PresignResponse presign(PresignRequest request) {
        if (request.kind() == null) {
            throw new ConflictException("업로드 용도가 지정되지 않았습니다.");
        }
        String contentType = request.contentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ConflictException("이미지 파일만 올릴 수 있습니다.");
        }
        UploadKind kind = request.kind();
        Long size = request.size();
        if (size == null || size <= 0) {
            throw new ConflictException("파일 크기를 알 수 없습니다.");
        }
        if (size > kind.getMaxBytes()) {
            throw new ConflictException("이미지가 너무 큽니다. (최대 " + kind.getMaxLabel() + ")");
        }

        String key = kind.getPrefix() + "/" + LocalDate.now().getYear()
                + "/" + UUID.randomUUID() + "." + ALLOWED.get(contentType);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        String uploadUrl = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(presignMinutes))
                        .putObjectRequest(put)
                        .build()
        ).url().toString();

        return new PresignResponse(key, uploadUrl, publicBaseUrl + "/" + key);
    }

    /**
     * 업로드가 끝난 뒤 그 URL을 DB에 저장해도 되는지 확인한다.
     *
     * 서명에는 파일 크기를 묶을 수 없다. contentType은 서명에 들어가지만 크기는 못 들어간다.
     * 그래서 "서명 단계에서 못 막는 것"을 여기서 막는다.
     * 기준을 넘긴 객체는 DB에 남기지 않고 S3에서도 지운다.
     *
     * @return 저장해도 되는 URL. 빈 값이 들어오면 null (이미지 제거 요청)
     */
    public String verify(String publicUrl, UploadKind kind) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        String prefix = publicBaseUrl + "/";
        if (!publicUrl.startsWith(prefix)) {
            throw new ConflictException("허용되지 않은 이미지 주소입니다.");
        }
        String key = publicUrl.substring(prefix.length());
        if (!key.startsWith(kind.getPrefix() + "/")) {
            throw new ConflictException("이미지 용도가 맞지 않습니다.");
        }

        HeadObjectResponse head;
        try {
            head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new ConflictException("업로드된 이미지를 찾을 수 없습니다. 다시 올려주세요.");
        }

        if (!ALLOWED_TYPES.contains(head.contentType())) {
            delete(key);
            throw new ConflictException("이미지 파일만 올릴 수 있습니다.");
        }
        if (head.contentLength() > kind.getMaxBytes()) {
            delete(key);
            throw new ConflictException("이미지가 너무 큽니다. (최대 " + kind.getMaxLabel() + ")");
        }
        return publicUrl;
    }

    /**
     * 서버가 직접 올린다. 옛 base64 데이터를 S3로 옮기는 1회성 이관에만 쓴다.
     * 평소 업로드는 브라우저가 S3로 직접 보내므로 이 길을 타지 않는다.
     */
    public String uploadBytes(UploadKind kind, byte[] bytes, String contentType, String nameHint) {
        String ext = ALLOWED.getOrDefault(contentType, "bin");
        String key = kind.getPrefix() + "/legacy/" + nameHint + "." + ext;
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes)
        );
        return publicBaseUrl + "/" + key;
    }

    /** 교체되거나 검증에 실패한 이미지를 지운다. 실패해도 본 작업을 막지는 않는다. */
    public void deleteByUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        String prefix = publicBaseUrl + "/";
        if (!publicUrl.startsWith(prefix)) return;
        delete(publicUrl.substring(prefix.length()));
    }

    private void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ignored) {
            // 지우기 실패가 사용자 요청을 막을 이유는 없다. 수명 주기 규칙이 뒤처리한다.
        }
    }
}
