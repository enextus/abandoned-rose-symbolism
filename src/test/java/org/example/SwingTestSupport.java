package org.example;

import javax.accessibility.AccessibleContext;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;

/** Small, layout-independent helpers for Swing contract tests. */
final class SwingTestSupport {
    private SwingTestSupport() { }

    static <T extends Component> T findByAccessibleName(Component root, Class<T> type, String accessibleName) {
        if (type.isInstance(root)) {
            AccessibleContext context = root.getAccessibleContext();
            if (context != null && accessibleName.equals(context.getAccessibleName())) return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findByAccessibleNameOrNull(child, type, accessibleName);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Component not found: " + type.getSimpleName() + " / " + accessibleName);
    }

    static <T extends Component> T findFirst(Component root, Class<T> type) {
        T found = findFirstOrNull(root, type);
        if (found == null) throw new AssertionError("Component not found: " + type.getSimpleName());
        return found;
    }

    static JLabel findLabel(Component root, String text) {
        JLabel found = findLabelOrNull(root, text);
        if (found == null) throw new AssertionError("Label not found: " + text);
        return found;
    }

    private static <T extends Component> T findByAccessibleNameOrNull(Component root, Class<T> type, String name) {
        if (type.isInstance(root)) {
            AccessibleContext context = root.getAccessibleContext();
            if (context != null && name.equals(context.getAccessibleName())) return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findByAccessibleNameOrNull(child, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T findFirstOrNull(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findFirstOrNull(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JLabel findLabelOrNull(Component root, String text) {
        if (root instanceof JLabel label && text.equals(label.getText())) return label;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel found = findLabelOrNull(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
