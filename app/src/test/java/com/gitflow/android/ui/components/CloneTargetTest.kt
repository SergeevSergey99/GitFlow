package com.gitflow.android.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloneTargetTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun newFolderName_isUsable() {
        assertTrue(isCloneTargetUsable(tempFolder.root.absolutePath, "fresh-repo"))
    }

    @Test
    fun existingFolderOrBlankName_isRejected() {
        tempFolder.newFolder("taken")

        assertFalse(isCloneTargetUsable(tempFolder.root.absolutePath, "taken"))
        assertFalse(isCloneTargetUsable(tempFolder.root.absolutePath, " taken "))
        assertFalse(isCloneTargetUsable(tempFolder.root.absolutePath, "  "))
    }
}
