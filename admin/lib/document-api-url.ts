export function documentApiUrl(
    extensionId: string,
    slotKey: string,
    subPath: string,
    partition?: string,
    query: URLSearchParams = new URLSearchParams(),
): string {
    if (partition) {
        query.set("partition", partition);
    }
    const queryString = query.toString();
    return `/api/extensions/${extensionId}/documents/${slotKey}${subPath}${queryString ? `?${queryString}` : ""}`;
}
