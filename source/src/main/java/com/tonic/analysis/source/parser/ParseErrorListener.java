package com.tonic.analysis.source.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Callback the parser reports each error to, with factories for the throwing, collecting, logging
 * and silent policies.
 */
@FunctionalInterface
public interface ParseErrorListener
{

    /**
     * Reports one error encountered while parsing.
     * @param error the error to report
     */
    void onError(ParseException error);

    /**
     * @return a listener that rethrows the first error it is given, aborting the parse
     */
    static ParseErrorListener throwing()
    {
        return error -> { throw error; };
    }

    /**
     * @param errors the caller-owned list each error is appended to
     * @return a listener that appends to the list and lets parsing continue
     */
    static ParseErrorListener collecting(List<ParseException> errors)
    {
        return errors::add;
    }

    /**
     * @return a fresh listener that accumulates errors in a list of its own
     */
    static CollectingErrorListener collecting()
    {
        return new CollectingErrorListener();
    }

    /**
     * @param logger receives the formatted message of each error
     * @return a listener that logs and then discards every error
     */
    static ParseErrorListener logging(Consumer<String> logger)
    {
        return error -> logger.accept(error.getFormattedMessage());
    }

    /**
     * @return a listener that discards every error
     */
    static ParseErrorListener silent()
    {
        return error -> {};
    }

    /**
     * Chains a second listener behind this one, both receiving every error.
     * @param other the listener to notify after this one
     * @return the combined listener
     */
    default ParseErrorListener andThen(ParseErrorListener other)
    {
        return error -> {
            this.onError(error);
            other.onError(error);
        };
    }

    class CollectingErrorListener implements ParseErrorListener
    {
        private final List<ParseException> errors = new ArrayList<>();

        /**
         * Appends the error to the internal list.
         * @param error the error to collect
         */
        @Override
        public void onError(ParseException error)
        {
            errors.add(error);
        }

        /**
         * @return the errors
         */
        public List<ParseException> getErrors()
        {
            return errors;
        }

        /**
         * @return true if at least one error was collected
         */
        public boolean hasErrors()
        {
            return !errors.isEmpty();
        }

        /**
         * @return how many errors were collected
         */
        public int errorCount()
        {
            return errors.size();
        }

        /**
         * Discards all collected errors.
         */
        public void clear()
        {
            errors.clear();
        }

        /**
         * @return every collected error's formatted message, one per line
         */
        public String formatAll()
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < errors.size(); i++)
            {
                if (i > 0) sb.append("\n");
                sb.append(errors.get(i).getFormattedMessage());
            }
            return sb.toString();
        }
    }
}
