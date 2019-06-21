# JustOpenPdf
Quite simple Android library for PDF preview

Are you looking for a simple solution to open PDF but all options don't suit you for different reasons?

Here **JustOpenPdf** is! 
* It doesn't bloat out your apk size (really lightweight)
* It's under Apache GNU License 2.0 that gives you a lot of freedom
* It's based on [ZoomLayout](https://github.com/natario1/ZoomLayout) so you get a very customizable solution
* It doesn't drag third-party libraries for PDF rendering – just uses built-in Android [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)
* Min SDK – 21 Lollipop

## Installing

#### Gradle

In your `build.gradle` file inside the *dependencies* section

* Gradle 3.0 and above
``` 
dependencies {
   ...
   implementation 'ru.blizzed:justopenpdf:1.0.0'
}
```
  
* Below 3.0
``` 
dependencies {
    ...
    compile 'ru.blizzed:justopenpdf:1.0.0'
}
```


## Usage

Just include PdfView in your layout:

```xml
    <ru.blizzed.justopenpdf.PdfView
            android:id="@+id/pdfView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"/>
```

Then in your Activity, Fragment, ViewController, etc use the following code:

```kotlin
    val pdfView = findViewById<PdfView>(R.id.pdfView)
    
    // With file path
    pdfView.openPdf("Path to the loaded PDF file.")
    
    // With file
    pdfView.openPdf(pdfFile)
```


If you want to listen to events of rendering process you can pass `eventListener` param to `openPdf` method.

```kotlin
    pdfView.openPdf(pdfFile) { event ->
        when (event) {
            is PdfRenderEvent.Processing -> {
                // Show progress bar
            }
            is PdfRenderEvent.Error -> {
                // Show rendering error
            }
            is PdfRenderEvent.Completed -> {
                // Hide progress bar and show PdfView
            }
        }
    }
```

## Customization

You can customize page view by setting `app:pageLayoutRes` flag with your custom page layout that must contain `ImageView`.

Note that this attribute works in pair with `app:pageLayoutImageViewId` that points on target `ImageView` in layout.
If it is not set `@id/page_target_image` will be used.


For more customization you can also extend `PdfView` and get access to `onPageRendered(pageIndex: Int, bitmap: Bitmap)` 
and `onPageInflated(pageIndex: Int, pageView: View)` callback methods where you can modify result bitmap or inflated view of the page at specified position. 

Since this view is based on `ZoomLayout` you can use all its features. [Read more about ZoomLayout on author's GitHub](https://github.com/natario1/ZoomLayout).

## Warning

This library is not well optimized. PDF file renders entirely, all pages at once.
So you can encounter an memory troubles if you will try to open PDF with a big amount of pages.
A size of each bitmap depends on container's size. With increasing of screen resolution a memory consumption also increases.
You can affect on bitmaps sizes using `app:renderResolutionFactor` attribute. The default factor is 1
(it means that bitmap size will coincide with the size of PdfView container).
I did research about this topic and I found out that up to 15 pages on device with FullHD screen are still good and
better in comparison with [WebView](https://developer.android.com/reference/android/webkit/WebView) with the opened Google Doc Previewer.

## TODOs

* Support of double tap to zoom
* Add current page listener for updating page counter
* Dynamic rendering (not all pages at once) and moving to RecyclerView

## License

```
Copyright (c) 2019 BlizzedRu (Ivan Vlasov)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
