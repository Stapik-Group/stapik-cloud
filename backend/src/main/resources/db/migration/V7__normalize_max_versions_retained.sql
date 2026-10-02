UPDATE document_slot SET max_versions_retained = 1 WHERE max_versions_retained < 1;
