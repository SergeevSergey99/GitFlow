package com.gitflow.android.data.storage

import android.app.Application
import android.os.Environment
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedStorageTest {

    private fun treeUri(documentId: String) =
        DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", documentId)

    @Test
    fun primaryVolumeFolder_mapsUnderExternalStorage() {
        assertEquals(
            File(Environment.getExternalStorageDirectory(), "Projects/app"),
            treeUriToFile(treeUri("primary:Projects/app"))
        )
    }

    @Test
    fun sdCardFolder_mapsUnderItsVolume() {
        assertEquals(File("/storage/1A2B-3C4D/repo"), treeUriToFile(treeUri("1A2B-3C4D:repo")))
    }

    @Test
    fun volumeRoot_mapsToTheVolumeItself() {
        assertEquals(Environment.getExternalStorageDirectory(), treeUriToFile(treeUri("primary:")))
    }
}
