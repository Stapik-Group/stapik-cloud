import { INDENT } from "@/lib/text-indent";

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

export function formatForEditing(rawContent: string): string {
    try {
        const prettyContent = JSON.stringify(JSON.parse(rawContent), null, INDENT);
        const isLossless =
            removeWhitespaceOutsideStrings(prettyContent) === removeWhitespaceOutsideStrings(rawContent);
        return isLossless ? prettyContent : rawContent;
    } catch {
        return rawContent;
    }
}
