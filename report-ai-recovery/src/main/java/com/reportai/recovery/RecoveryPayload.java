package com.reportai.recovery;

public sealed interface RecoveryPayload permits MissingInputRequest, InputSubmission, DownloadApprovalRequest,
        DownloadDecision, CodeDiagnosisRequest, CodeDiagnosis, DependencyRepairRequest, DependencyRepairPlan,
        OcrRequest, OcrResult, ChartExtractionRequest, ChartExtractionResult, ResultConflict, ConflictResolution,
        UserInputRequest, UserInputResponse { }
