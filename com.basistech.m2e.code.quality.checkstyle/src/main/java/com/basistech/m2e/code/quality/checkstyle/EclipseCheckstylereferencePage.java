package com.basistech.m2e.code.quality.checkstyle;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class EclipseCheckstylereferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private Composite parent;

    public EclipseCheckstylereferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
    }

    public void init(IWorkbench workbench) {
    }

    /*
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    public void createFieldEditors() {
        parent = getFieldEditorParent();
        String text;

        text = Messages.EclipseCheckstylereferencePage_0;
        addField(new BooleanFieldEditor(CheckstyleEclipseConstants.ECLIPSE_CS_GENERATE_FORMATTER_SETTINGS, text, parent));

    }

}
