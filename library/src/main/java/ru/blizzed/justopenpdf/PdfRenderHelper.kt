/*
 * Copyright 2019 BlizzedRu (Ivan Vlasov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.blizzed.justopenpdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.reactivex.Observable
import io.reactivex.Single
import java.io.File

internal class PdfRenderHelper(pdfFile: File, private val requiredBitmapWidth: Int) : AutoCloseable {

    data class RenderedPageData(val pageIndex: Int, val bitmap: Bitmap)

    private val pdfFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    private val pdfRenderer: PdfRenderer = PdfRenderer(pdfFileDescriptor)

    fun renderAll(): Observable<RenderedPageData> = renderRange(0 until getPagesCount())

    fun renderRange(range: IntRange): Observable<RenderedPageData> = Observable.create { emitter ->
        try {
            assertPagesCountNotNull()
            assertIndexInRange(range.first)
            assertIndexInRange(range.endInclusive)

            range.map { pageIndex ->
                RenderedPageData(pageIndex, renderPageSync(pageIndex))
            }.forEach(emitter::onNext)

            emitter.onComplete()
        } catch (renderException: PdfRenderException) {
            emitter.onError(renderException)
        } catch (exception: Exception) {
            emitter.onError(PdfRenderException(cause = exception))
        }
    }

    fun render(index: Int): Single<Bitmap> = Single.fromCallable {
        assertPagesCountNotNull()
        assertIndexInRange(index)

        renderPageSync(index)
    }

    fun getPagesCount(): Int = pdfRenderer.pageCount

    override fun close() {
        pdfFileDescriptor.close()
        pdfRenderer.close()
    }

    private fun renderPageSync(index: Int): Bitmap = pdfRenderer
        .openPage(index)
        .use { page ->
            Bitmap.createBitmap(
                requiredBitmapWidth,
                calculateBitmapHeight(page.width, page.height),
                Bitmap.Config.ARGB_8888
            ).also { targetBitmap ->
                page.render(targetBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }

    private fun calculateBitmapHeight(initialPageWidth: Int, initialPageHeight: Int): Int =
        (initialPageHeight.toFloat() / initialPageWidth * requiredBitmapWidth).toInt()

    @Throws(PdfRenderException::class)
    private fun assertIndexInRange(index: Int) {
        if (index < 0) throw PdfRenderException("Requested page index $index < 0")
        if (index >= getPagesCount()) throw PdfRenderException("Requested page index $index out of range. Max valid value is ${getPagesCount() - 1}")
    }

    @Throws(PdfRenderException::class)
    private fun assertPagesCountNotNull() {
        if (getPagesCount() == 0) throw PdfRenderException("Nothing to render. Pages count is 0")
    }

}
