package cz.wingit.ui.common;

import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/**
 * For a branch or tag name field: text inserted at once (paste, drop) is trimmed and each run of
 * whitespace becomes {@code _}, so a pasted task title like "Fix login page" gives "Fix_login_page".
 * Single typed characters stay as they are, so typing works normally and a typed space is still
 * reported as invalid.
 */
public final class PastedNameFilter extends DocumentFilter {

    private PastedNameFilter() {
    }

    /** Installs the filter on {@code field}, whose document must be a plain text document. */
    public static void install(JTextComponent field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new PastedNameFilter());
    }

    /** Pasted text as part of a name: trimmed, each run of spaces, tabs or line breaks replaced by {@code _}. */
    public static String name(String text) {
        return text.strip().replaceAll("\\s+", "_");
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
            throws BadLocationException {
        super.insertString(fb, offset, pasted(text), attrs);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        super.replace(fb, offset, length, pasted(text), attrs);
    }

    private static String pasted(String text) {
        return text == null || text.length() < 2 ? text : name(text);
    }
}
