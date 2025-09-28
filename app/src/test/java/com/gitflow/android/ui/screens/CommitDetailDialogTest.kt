package com.gitflow.android.ui.screens

import com.gitflow.android.data.models.FileTreeNode
import com.gitflow.android.data.models.FileTreeNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitDetailDialogTest {

    @Test
    fun collapseSingleChildDirectories_mergesDirectoryChains() {
        val fileNode = FileTreeNode(
            name = "File.kt",
            path = "Folder1/Folder2/File.kt",
            type = FileTreeNodeType.FILE
        )
        val folder2 = FileTreeNode(
            name = "Folder2",
            path = "Folder1/Folder2",
            type = FileTreeNodeType.DIRECTORY,
            children = listOf(fileNode)
        )
        val folder1 = FileTreeNode(
            name = "Folder1",
            path = "Folder1",
            type = FileTreeNodeType.DIRECTORY,
            children = listOf(folder2)
        )
        val root = FileTreeNode(
            name = "",
            path = "",
            type = FileTreeNodeType.DIRECTORY,
            children = listOf(folder1)
        )

        val collapsed = collapseSingleChildDirectories(root, isRoot = true)

        val collapsedChild = collapsed.children.single()
        assertEquals("Folder1/Folder2", collapsedChild.name)
        assertEquals("Folder1/Folder2", collapsedChild.path)
        assertEquals(1, collapsedChild.children.size)
        assertEquals("File.kt", collapsedChild.children.single().name)
    }

    @Test
    fun collapseSingleChildDirectories_preservesBranches() {
        val sharedFile = FileTreeNode(
            name = "File.txt",
            path = "Folder/File.txt",
            type = FileTreeNodeType.FILE
        )
        val nestedFolder = FileTreeNode(
            name = "Nested",
            path = "Folder/Nested",
            type = FileTreeNodeType.DIRECTORY,
            children = listOf(sharedFile)
        )
        val folder = FileTreeNode(
            name = "Folder",
            path = "Folder",
            type = FileTreeNodeType.DIRECTORY,
            children = listOf(
                sharedFile.copy(path = "Folder/File2.txt", name = "File2.txt"),
                nestedFolder
            )
        )
        val root = FileTreeNode(
            name = "",
            path = "",
            type = FileTreeNodeType.DIRECTORY,
            children = listOf(folder)
        )

        val collapsed = collapseSingleChildDirectories(root, isRoot = true)

        val collapsedChild = collapsed.children.single()
        assertEquals("Folder", collapsedChild.name)
        assertEquals("Folder", collapsedChild.path)
        assertEquals(2, collapsedChild.children.size)
        assertTrue(collapsedChild.children.any { it.name == "File2.txt" })
        assertTrue(collapsedChild.children.any { it.name == "Nested" })
    }
}
