package com.ptmanager.backend.common.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

/**
 * 실제 S3 업로드 스토리지. `s3.enabled=true` 일 때만 활성화된다.
 * 자격증명은 AWS 기본 제공자 체인(환경변수·인스턴스 프로파일 등)으로 해결한다.
 */
@Service
@ConditionalOnProperty(prefix = "s3", name = ["enabled"], havingValue = "true")
class S3StorageService(
    @Value("\${s3.bucket}") private val bucket: String,
    @Value("\${s3.region}") private val region: String,
    @Value("\${s3.public-base-url:}") private val publicBaseUrl: String,
) : StorageService {

    private val s3Client: S3Client = S3Client.builder().region(Region.of(region)).build()

    override fun store(file: MultipartFile): String {
        val key = "notices/${UUID.randomUUID()}-${file.originalFilename ?: "file"}"
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.contentType)
            .build()
        s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))

        return if (publicBaseUrl.isNotBlank()) {
            "${publicBaseUrl.trimEnd('/')}/$key"
        } else {
            "https://$bucket.s3.$region.amazonaws.com/$key"
        }
    }
}
