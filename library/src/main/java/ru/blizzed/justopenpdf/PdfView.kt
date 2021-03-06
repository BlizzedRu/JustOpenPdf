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
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnLayout
import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.IOException

/**
 * Uses [PdfRenderHelper] to render PDF file's pages to plain image (bitmap).
 *
 * A view for each page will be inflated and added to pages container after rendering ends.
 *
 * You can customize page view by setting "app:pageLayoutRes" flag with your custom page layout that must contain [ImageView].
 * Note that this attribute works in pair with "app:pageLayoutImageViewId" that points on target [ImageView] in layout.
 * If it is not set "@id/page_target_image" will be used.
 *
 *
 * Note: This library is not well optimized. PDF file renders entirely, all pages at once. So you can encounter an memory troubles
 * if you will try to open PDF with a big amount of pages. A size of each bitmap depends on container's size.
 * With increasing of screen resolution a memory consumption also increases.
 *
 * You can affect on bitmaps sizes using "app:renderResolutionFactor" attribute. The default factor is 1
 * (it means that bitmap size will coincide with the size of PdfView container).
 *
 * I did research about this topic and I found out that up to 15 pages on device with FullHD screen are still good and
 * better in comparison with [android.webkit.WebView] with the opened Google Doc Previewer.
 *
 *
 * This view based on [ZoomLayout] so you can customize it as you want.
 * I am very thankful to [natario1](https://github.com/natario1) for this [ZoomLayout] library.
 *
 * TODO: Add double tap to zoom
 * TODO: Current page listener for updating page counter
 * TODO: Dynamic rendering (not all pages at once) and moving to RecyclerView
 */
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

    /**
     * Opens and displays [file] in PDF format.
     *
     * Pass an [eventListener] parameter for receiving rendering events.
     *
     * @param file File in PDF format to render and display.
     * @param eventListener Callback to listen to render events.
     *
     * @see [PdfRenderEvent]
     */
    fun openPdf(file: File, eventListener: ((PdfRenderEvent) -> Unit)? = null) {
        (parent as ViewGroup).doOnLayout { view ->
            pagesContainer.removeAllViews()

            val targetBitmapWidth = (view.width * renderResolutionFactor).toInt()
            try {
                val renderHelper = PdfRenderHelper(file, targetBitmapWidth)
                renderPages(renderHelper, eventListener)
            } catch (exception: IOException) {
                // Some IO problems including file not in PDF format
                eventListener?.invoke(PdfRenderEvent.Error(PdfRenderException(cause = exception)))
            }
        }
    }

    /**
     * Opens and displays file associated with [filePath] in PDF format.
     *
     * Pass an [eventListener] parameter for receiving rendering events.
     *
     * @param filePath Path to file in PDF format to render and display.
     * @param eventListener Callback to listen to render events.
     *
     * @see [PdfRenderEvent]
     */
    fun openPdf(filePath: String, eventListener: ((PdfRenderEvent) -> Unit)? = null) =
        openPdf(File(filePath), eventListener)

    /**
     * Notifies you that page at [pageIndex] has been rendered to a specified [bitmap].
     *
     * Can be useful to react on rendering events.
     * Also can be used for modifying rendered [bitmap] according to your requirements.
     *
     * @param pageIndex An index of rendered page.
     * @param bitmap A bitmap source of rendered page.
     */
    open fun onPageRendered(pageIndex: Int, bitmap: Bitmap) = Unit

    /**
     * Notifies you that view for rendered page at [pageIndex] has been inflated to [pageView].
     *
     * Can be useful to react on inflating events.
     * Also can be used for modifying inflated [pageView] according to your requirements.
     *
     * @param pageIndex An index of rendered page.
     * @param pageView An inflated view for rendered page
     */
    open fun onPageInflated(pageIndex: Int, pageView: View) = Unit

    @CallSuper
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderingDisposable?.dispose()
    }

    private fun renderPages(renderHelper: PdfRenderHelper, eventListener: ((PdfRenderEvent) -> Unit)? = null) {
        renderingDisposable?.dispose()
        renderingDisposable = renderHelper.renderAll()
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { eventListener?.invoke(PdfRenderEvent.Processing) }
            .doOnNext { (index, bitmap) -> onPageRendered(index, bitmap) }
            .doOnComplete { eventListener?.invoke(PdfRenderEvent.Completed) }
            .doOnError { throwable -> eventListener?.invoke(PdfRenderEvent.Error(throwable)) }
            .doOnTerminate(renderHelper::close)
            .map(PdfRenderHelper.RenderedPageData::bitmap)
            .subscribe(::addPage)
    }

    private fun addPage(pageBitmap: Bitmap) {
        View.inflate(context, pageLayoutRes, null)
            .apply { id = View.generateViewId() }
            .also { pageView -> onPageInflated(pagesContainer.childCount, pageView) }
            .also(pagesContainer::addView)
            .findViewById<ImageView>(pageLayoutImageViewId)
            .setImageBitmap(pageBitmap)
    }

}
