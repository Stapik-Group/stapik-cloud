package pl.stapik.cloud.document.dto;

import pl.stapik.cloud.document.diff.JsonChange;

import java.util.List;

public record DocumentComparison(DocumentSnapshot from, DocumentSnapshot to, List<JsonChange> changes) {
}
