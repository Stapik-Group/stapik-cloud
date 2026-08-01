package pl.stapik.cloud.document.dto;

import pl.stapik.cloud.document.data.DocumentData;

public record WriteResult(DocumentData documentData, boolean conflict) {
}