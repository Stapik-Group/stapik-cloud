export type TextEdit = {
    text: string;
    selectionStart: number;
    selectionEnd: number;
};

export const INDENT = "\t";

function findSelectedLinesBounds(text: string, selectionStart: number, selectionEnd: number) {
    const blockStart = text.lastIndexOf("\n", selectionStart - 1) + 1;
    const endsAfterLineBreak = selectionEnd > selectionStart && text[selectionEnd - 1] === "\n";
    const lastSelectedIndex = endsAfterLineBreak ? selectionEnd - 1 : selectionEnd;
    const lineBreakAfterBlock = text.indexOf("\n", lastSelectedIndex);
    const blockEnd = lineBreakAfterBlock === -1 ? text.length : lineBreakAfterBlock;

    return { blockStart, blockEnd };
}

export function indentSelection(text: string, selectionStart: number, selectionEnd: number): TextEdit {
    const selectedText = text.slice(selectionStart, selectionEnd);

    if (!selectedText.includes("\n")) {
        const cursorPosition = selectionStart + INDENT.length;
        return {
            text: text.slice(0, selectionStart) + INDENT + text.slice(selectionEnd),
            selectionStart: cursorPosition,
            selectionEnd: cursorPosition,
        };
    }

    const { blockStart, blockEnd } = findSelectedLinesBounds(text, selectionStart, selectionEnd);
    const lines = text.slice(blockStart, blockEnd).split("\n");
    const indentedBlock = lines.map((line) => INDENT + line).join("\n");

    return {
        text: text.slice(0, blockStart) + indentedBlock + text.slice(blockEnd),
        selectionStart: selectionStart + INDENT.length,
        selectionEnd: selectionEnd + lines.length * INDENT.length,
    };
}

export function outdentSelection(text: string, selectionStart: number, selectionEnd: number): TextEdit {
    const { blockStart, blockEnd } = findSelectedLinesBounds(text, selectionStart, selectionEnd);
    const lines = text.slice(blockStart, blockEnd).split("\n");
    const outdentedLines = lines.map((line) => (line.startsWith(INDENT) ? line.slice(INDENT.length) : line));

    const removedFromFirstLine = lines[0].length - outdentedLines[0].length;
    const removedInTotal = lines.reduce((sum, line, index) => sum + line.length - outdentedLines[index].length, 0);
    const newSelectionStart = Math.max(blockStart, selectionStart - removedFromFirstLine);
    const newSelectionEnd = Math.max(newSelectionStart, selectionEnd - removedInTotal);

    return {
        text: text.slice(0, blockStart) + outdentedLines.join("\n") + text.slice(blockEnd),
        selectionStart: newSelectionStart,
        selectionEnd: newSelectionEnd,
    };
}
