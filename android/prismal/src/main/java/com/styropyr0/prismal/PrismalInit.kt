/*
 * Copyright 2025 Styropyr0
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
 *
 * Parhlou modification (2026-11-12): new file. Lets the host app load the AGSL shader assets
 * directly, so glass still works when App Startup's provider has not run (tests, removed provider).
 */

package com.styropyr0.prismal

import android.content.Context
import com.styropyr0.prismal.internal.PrismalAgslAssets

/** Loads Prismal's shader assets. Safe to call many times; App Startup normally does this for you. */
fun initializePrismal(context: Context) {
    if (isAGSLShaderSupported()) {
        PrismalAgslAssets.bind(context.applicationContext.assets)
    }
}
