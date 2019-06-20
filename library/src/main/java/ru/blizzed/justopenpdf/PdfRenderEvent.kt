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

/**
 * This class represents an event of PDF rendering process.
 */
sealed class PdfRenderEvent {
    /**
     * PDF rendering in process.
     *
     * Getting this event you can for example show progress bar to user.
     */
    object Processing : PdfRenderEvent()

    /**
     * PDF rendering interrupted with error.
     *
     * Getting this event you can handle and react on any exceptions.
     */
    class Error(val throwable: Throwable) : PdfRenderEvent()

    /**
     * PDF rendering completed. [PdfView] displays all rendered pages.
     *
     * Getting this event you can for example hide progress bar.
     */
    object Completed : PdfRenderEvent()
}
