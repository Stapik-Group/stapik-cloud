"use client";

import { useLayoutEffect, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/lib/i18n/LocaleProvider";
import { INDENT, indentSelection, outdentSelection } from "@/lib/text-indent";

function removeWhitespaceOutsideStrings(json: string): string {
    let result = "";
    let isInsideString = false;
    let isEscaped = false;

    for (const character of json) {
        if (isInsideString) {
            result += character;
            if (isEscaped) {
                isEscaped = false;
            } else if (character === "\\") {
                isEscaped = true;
            } else if (character === "\"") {
                isInsideString = false;
            }
        } else if (character === "\"") {
            isInsideString = true;
            result += character;
        } else if (!/\s/.test(character)) {
            result += character;
        }
    }

    return result;
}

// Pretty-prints only when re-serialising does not change any value (big integers, number formats,
// duplicate keys, escapes). Otherwise the stored text is shown untouched.
function formatForEditing(rawContent: string): string {
    try {
        const prettyContent = JSON.stringify(JSON.parse(rawContent), null, INDENT);
        const isLossless =
            removeWhitespaceOutsideStrings(prettyContent) === removeWhitespaceOutsideStrings(rawContent);
        return isLossless ? prettyContent : rawContent;
    } catch {
        return rawContent;
    }
}

export function JsonDocumentEditor({
                                       extensionId,
                                       slotKey,
                                       storedContent,
                                       lastKnownUpdate,
                                   }: {
    extensionId: string;
    slotKey: string;
    storedContent: string;
    lastKnownUpdate: string;
}) {
    const router = useRouter();
    const { t } = useTranslation();
    const [initialText] = useState(() => formatForEditing(storedContent));
    const [text, setText] = useState(initialText);
    const [error, setError] = useState<string | null>(null);
    const [hasConflict, setHasConflict] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const pendingSelectionRef = useRef<{ start: number; end: number } | null>(null);
    const isTabCapturePausedRef = useRef(false);

    const isDirty = text !== initialText;

    useLayoutEffect(() => {
        const pendingSelection = pendingSelectionRef.current;
        if (pendingSelection && textareaRef.current) {
            textareaRef.current.setSelectionRange(pendingSelection.start, pendingSelection.end);
            pendingSelectionRef.current = null;
        }
    }, [text]);

    function handleTextChange(newText: string) {
        setText(newText);
        setError(null);
    }

    function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
        if (event.key === "Escape") {
            isTabCapturePausedRef.current = true;
            return;
        }

        if (event.key !== "Tab" || isTabCapturePausedRef.current) {
            return;
        }

        event.preventDefault();
        const { selectionStart, selectionEnd } = event.currentTarget;
        const edit = event.shiftKey
            ? outdentSelection(text, selectionStart, selectionEnd)
            : indentSelection(text, selectionStart, selectionEnd);

        if (edit.text === text) {
            return;
        }

        pendingSelectionRef.current = { start: edit.selectionStart, end: edit.selectionEnd };
        handleTextChange(edit.text);
    }

    function handleDiscard() {
        setText(initialText);
        setError(null);
    }

    async function handleSave() {
        setError(null);

        try {
            JSON.parse(text);
        } catch (parseError) {
            const message = parseError instanceof Error ? parseError.message : String(parseError);
            setError(t("documents.editorInvalidJson", { message }));
            return;
        }

        setIsSaving(true);
        try {
            const response = await fetch(`/api/extensions/${extensionId}/documents/${slotKey}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                // lastKnownUpdate must be passed back as the exact string received from the API:
                // parsing it into a Date would cut microseconds and cause false conflicts.
                body: JSON.stringify({ content: text, clientLastKnownUpdate: lastKnownUpdate }),
            });

            if (response.status === 409) {
                setHasConflict(true);
                return;
            }

            if (!response.ok) {
                setError(t("documents.editorSaveError", { status: response.status }));
                return;
            }

            router.refresh();
        } finally {
            setIsSaving(false);
        }
    }

    return (
        <div className="space-y-2">
            <textarea
                value={text}
                ref={textareaRef}
                onChange={(event) => handleTextChange(event.target.value)}
                onKeyDown={handleKeyDown}
                onBlur={() => {
                    isTabCapturePausedRef.current = false;
                }}
                spellCheck={false}
                rows={20}
                style={{ tabSize: 2 }}
                className="input w-full font-mono text-sm"
            />

            {error && <p className="text-sm text-danger">{error}</p>}

            {hasConflict && (
                <div className="space-y-2">
                    <p className="text-sm text-danger">{t("documents.editorConflict")}</p>
                    <button type="button" className="btn-secondary text-sm" onClick={() => router.refresh()}>
                        {t("documents.editorReloadLatest")}
                    </button>
                </div>
            )}

            <div className="flex gap-2">
                <button
                    type="button"
                    onClick={handleSave}
                    disabled={!isDirty || isSaving}
                    className="btn-primary"
                >
                    {isSaving ? t("documents.editorSaving") : t("documents.editorSave")}
                </button>
                <button
                    type="button"
                    onClick={handleDiscard}
                    disabled={!isDirty || isSaving}
                    className="btn-secondary"
                >
                    {t("documents.editorDiscard")}
                </button>
            </div>
        </div>
    );
}
