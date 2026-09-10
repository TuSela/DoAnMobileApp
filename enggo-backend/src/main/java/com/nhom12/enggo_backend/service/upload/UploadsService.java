package com.nhom12.enggo_backend.service.upload;

import com.nhom12.enggo_backend.exception.AppException;
import com.nhom12.enggo_backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UploadsService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.cloudfront.domain}")
    private String cloudFrontDomain;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    public String uploadImage(MultipartFile file) throws IOException {
        String detectedContentType = validateFile(file);

        // Tạo tên file duy nhất trong thư mục "images" trên S3
        String fileName = "images/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                // Dùng content-type xác định được từ magic bytes (đáng tin cậy hơn header client gửi lên)
                .contentType(detectedContentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

        // Trả về URL HTTPS của CloudFront CDN
        return "https://" + cloudFrontDomain + "/" + fileName;
    }

    public void deleteImage(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains(cloudFrontDomain)) {
            return;
        }

        // Tách lấy file key (ví dụ: "images/uuid_name.png") từ URL CloudFront
        String fileKey = fileUrl.replace("https://" + cloudFrontDomain + "/", "");

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    /**
     * Kiểm tra file hợp lệ (không rỗng, không quá lớn, đúng định dạng ảnh được hỗ trợ).
     * @return content-type thực tế được xác định từ magic bytes của file (jpeg/png/webp)
     */
    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        // Không tin tưởng hoàn toàn Content-Type do client (Postman, trình duyệt...) tự khai báo,
        // vì giá trị này có thể sai lệch hoặc bị rỗng/"application/octet-stream".
        // Thay vào đó, kiểm tra "magic bytes" (chữ ký nhị phân) thực tế của file để xác định đúng định dạng ảnh.
        try {
            byte[] header = new byte[12];
            int read = file.getInputStream().read(header);
            String detectedType = read < 4 ? null : detectImageType(header);
            if (detectedType == null) {
                throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
            }
            return detectedType;
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    /**
     * Nhận diện định dạng ảnh dựa trên magic bytes ở đầu file.
     * Trả về "image/jpeg", "image/png", "image/webp", hoặc null nếu không phải 1 trong 3 định dạng được hỗ trợ.
     */
    private String detectImageType(byte[] header) {
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return "image/png";
        }
        // JPEG: FF D8 FF
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // WEBP: "RIFF" .... "WEBP"
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}