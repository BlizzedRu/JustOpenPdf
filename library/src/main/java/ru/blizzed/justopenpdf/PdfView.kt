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

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.IOException

open class PdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : ZoomLayout(context, attrs, defStyleAttr) {

    private val pagesContainer: LinearLayout

    @LayoutRes
    private var pageLayoutRes: Int = R.layout.pdf_view_page

    @IdRes
    private var pageLayoutImageViewId: Int = R.id.page_target_image

    private var renderResolutionFactor: Float = 1.0f

    private var renderingDisposable: Disposable? = null

    init {
        inflate(context, R.layout.pdf_view, this)

        pagesContainer = findViewById(R.id.pages_container)

        super.setTransformation(ZoomApi.TRANSFORMATION_CENTER_CROP, Gravity.TOP)
        super.setOverPinchable(false)

        context.withStyledAttributes(attrs, R.styleable.PdfView, defStyleAttr) {
            pageLayoutRes = getResourceId(R.styleable.PdfView_pageLayout, pageLayoutRes)
            pageLayoutImageViewId = getResourceId(R.styleable.PdfView_pageLayoutImageViewId, pageLayoutImageViewId)
            renderResolutionFactor = getFloat(R.styleable.PdfView_renderResolutionFactor, renderResolutionFactor)
            getResourceId(
                R.styleable.PdfView_android_background,
                R.color.view_pdf_background
            ).let(::setBackgroundResource)
        }
    }

    fun openPdf(file: File): LiveData<PdfRenderEvent> {
        val renderingProcess = MutableLiveData<PdfRenderEvent>()
        (parent as ViewGroup).doOnLayout { view ->
            pagesContainer.removeAllViews()

            val targetBitmapWidth = (view.width * renderResolutionFactor).toInt()
            try {
                val renderHelper = PdfRenderHelper(file, targetBitmapWidth)
                renderPages(renderHelper, renderingProcess)
            } catch (exception: IOException) {
                // Some IO problems including file not in PDF format
                renderingProcess.postValue(PdfRenderEvent.Error(PdfRenderException(cause = exception)))
            }
        }
        return renderingProcess
    }

    fun openPdf(filePath: String): LiveData<PdfRenderEvent> = openPdf(File(filePath))

    open fun onPageRendered(pageIndex: Int, bitmap: Bitmap) = Unit

    open fun onPageInflated(pageIndex: Int, pageView: View) = Unit

    private fun renderPages(renderHelper: PdfRenderHelper, renderingProcess: MutableLiveData<PdfRenderEvent>) {
        renderingDisposable?.dispose()
        renderingDisposable = renderHelper.renderAll()
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { renderingProcess.postValue(PdfRenderEvent.Processing) }
            .doOnNext { (index, bitmap) -> onPageRendered(index, bitmap) }
            .doOnComplete { renderingProcess.postValue(PdfRenderEvent.Completed) }
            .doOnError { renderingProcess.postValue(PdfRenderEvent.Error(it)) }
            .doOnTerminate(renderHelper::close)
            .map(PdfRenderHelper.RenderedPageData::bitmap)
            .subscribe(::addPage)
    }

    private fun addPage(pageBitmap: Bitmap) {
        View.inflate(context, pageLayoutRes, null)
            .also { pageView -> onPageInflated(pagesContainer.childCount, pageView) }
            .apply { id = View.generateViewId() }
            .also(pagesContainer::addView)
            .findViewById<ImageView>(pageLayoutImageViewId)
            .setImageBitmap(pageBitmap)
    }

}
