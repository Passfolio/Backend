package com.capstone.passfolio.domain.ai.entity;

import com.capstone.passfolio.common.auditor.TimeBaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "ai_jobs")
public class AiJob extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "ai_job_id", length = 64)
    private String aiJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private AiJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private AiJobStatus status = AiJobStatus.PENDING;

    @Column(name = "input_file_id")
    private Long inputFileId;

    @Column(name = "output_pdf_url", columnDefinition = "TEXT")
    private String outputPdfUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    public void assignAiJobId(String aiJobId) {
        this.aiJobId = aiJobId;
    }

    public void markDone(String outputPdfUrl) {
        this.status = AiJobStatus.DONE;
        this.outputPdfUrl = outputPdfUrl;
    }

    public void markDoneWithResult(String resultJson) {
        this.status = AiJobStatus.DONE;
        this.resultJson = resultJson;
    }

    public void markError(String errorMessage) {
        this.status = AiJobStatus.ERROR;
        this.errorMessage = errorMessage;
    }
}
