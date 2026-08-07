package com.tonic.analysis.source.emit;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * A writer that inserts the configured indent unit at the start of each line.
 */
public class IndentingWriter
{

    private final Writer writer;
    private final String indentString;
    private int indentLevel;
    private boolean atLineStart;
    private int currentLine = 1;

    /**
     * Creates a writer indenting with tabs.
     * @param writer the underlying writer
     */
    public IndentingWriter(Writer writer)
    {
        this(writer, "\t");
    }

    /**
     * Creates a writer with the given indent unit.
     * @param writer the underlying writer
     * @param indentString the string written once per indent level
     */
    public IndentingWriter(Writer writer, String indentString)
    {
        this.writer = writer;
        this.indentString = indentString;
        this.indentLevel = 0;
        this.atLineStart = true;
    }

    /**
     * Creates a writer accumulating into a StringWriter, readable via toString().
     * @return the string-backed writer
     */
    public static IndentingWriter toStringWriter()
    {
        return new IndentingWriter(new StringWriter());
    }

    /**
     * Increases the indentation level.
     */
    public void indent()
    {
        indentLevel++;
    }

    /**
     * Decreases the indentation level, never going below zero.
     */
    public void dedent()
    {
        if (indentLevel > 0)
        {
            indentLevel--;
        }
    }

    /**
     * Writes text, emitting the indent before the first character of each line.
     * @param text the text to write
     * @throws RuntimeException if the underlying writer fails
     */
    public void write(String text)
    {
        try
        {
            for (int i = 0; i < text.length(); i++)
            {
                char c = text.charAt(i);
                if (atLineStart && c != '\n' && c != '\r')
                {
                    writeIndent();
                    atLineStart = false;
                }
                writer.write(c);
                if (c == '\n')
                {
                    atLineStart = true;
                    currentLine++;
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write", e);
        }
    }

    /**
     * Writes text followed by a newline.
     * @param text the text to write
     */
    public void writeLine(String text)
    {
        write(text);
        newLine();
    }

    /**
     * Writes a newline.
     * @throws RuntimeException if the underlying writer fails
     */
    public void newLine()
    {
        try
        {
            writer.write("\n");
            atLineStart = true;
            currentLine++;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write newline", e);
        }
    }

    /**
     * Writes an empty line.
     */
    public void blankLine()
    {
        newLine();
    }

    private void writeIndent() throws IOException
    {
        for (int i = 0; i < indentLevel; i++)
        {
            writer.write(indentString);
        }
    }

    /**
     * @return the current indentation level
     */
    public int getIndentLevel()
    {
        return indentLevel;
    }

    /**
     * Writes already-formatted text without adding indentation, still tracking lines.
     * @param text the text to write
     * @throws RuntimeException if the underlying writer fails
     */
    public void writeRaw(String text)
    {
        try
        {
            writer.write(text);
            if (!text.isEmpty())
            {
                atLineStart = text.charAt(text.length() - 1) == '\n';
                for (int i = 0; i < text.length(); i++)
                {
                    if (text.charAt(i) == '\n')
                    {
                        currentLine++;
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write", e);
        }
    }

    /**
     * @return the 1-based line number the next character will be written to
     */
    public int getCurrentLine()
    {
        return currentLine;
    }

    /**
     * Flushes the underlying writer.
     * @throws RuntimeException if flushing fails
     */
    public void flush()
    {
        try
        {
            writer.flush();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to flush", e);
        }
    }

    /**
     * Flushes and returns the accumulated output.
     * @return the underlying writer's string form; the emitted source when backed by a StringWriter
     */
    @Override
    public String toString()
    {
        flush();
        return writer.toString();
    }
}
