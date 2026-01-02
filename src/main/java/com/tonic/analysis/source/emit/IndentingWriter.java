package com.tonic.analysis.source.emit;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * A writer that handles indentation for source code emission.
 */
public class IndentingWriter {

    private final Writer writer;
    private final String indentString;
    private int indentLevel;
    private boolean atLineStart;

    public IndentingWriter(Writer writer) {
        this(writer, "    ");
    }

    public IndentingWriter(Writer writer, String indentString) {
        this.writer = writer;
        this.indentString = indentString;
        this.indentLevel = 0;
        this.atLineStart = true;
    }

    /**
     * Creates a writer that outputs to a string.
     */
    public static IndentingWriter toStringWriter() {
        return new IndentingWriter(new StringWriter());
    }

    /**
     * Increases the indentation level.
     */
    public void indent() {
        indentLevel++;
    }

    /**
     * Decreases the indentation level.
     */
    public void dedent() {
        if (indentLevel > 0) {
            indentLevel--;
        }
    }

    /**
     * Writes a string, handling indentation at line starts.
     */
    public void write(String text) {
        try {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (atLineStart && c != '\n' && c != '\r') {
                    writeIndent();
                    atLineStart = false;
                }
                writer.write(c);
                if (c == '\n') {
                    atLineStart = true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write", e);
        }
    }

    /**
     * Writes a line of text followed by a newline.
     */
    public void writeLine(String text) {
        write(text);
        newLine();
    }

    /**
     * Writes a newline.
     */
    public void newLine() {
        try {
            writer.write("\n");
            atLineStart = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write newline", e);
        }
    }

    /**
     * Writes an empty line.
     */
    public void blankLine() {
        newLine();
    }

    private void writeIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            writer.write(indentString);
        }
    }

    /**
     * Gets the current indentation level.
     */
    public int getIndentLevel() {
        return indentLevel;
    }

    /**
     * Writes content directly without adding indentation.
     * Useful for already-formatted content.
     */
    public void writeRaw(String text) {
        try {
            writer.write(text);
            if (!text.isEmpty()) {
                atLineStart = text.charAt(text.length() - 1) == '\n';
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write", e);
        }
    }

    /**
     * Flushes the underlying writer.
     */
    public void flush() {
        try {
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to flush", e);
        }
    }

    /**
     * Gets the output as a string (only works if constructed with StringWriter).
     */
    @Override
    public String toString() {
        flush();
        return writer.toString();
    }
}
