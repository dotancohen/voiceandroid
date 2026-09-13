/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copied unchanged from androidx.compose.material:material-icons-extended
 * 1.7.6 (its sources jar), under the license above. VoiceAndroid keeps the
 * few icons it uses that the core icon set lacks, so that the thousands of
 * extended icons it does not use are not packed into the application.
 */

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.Merge: ImageVector
    get() {
        if (_merge != null) {
            return _merge!!
        }
        _merge = materialIcon(name = "Filled.Merge") {
            materialPath {
                moveTo(6.41f, 21.0f)
                lineTo(5.0f, 19.59f)
                lineToRelative(4.83f, -4.83f)
                curveToRelative(0.75f, -0.75f, 1.17f, -1.77f, 1.17f, -2.83f)
                verticalLineToRelative(-5.1f)
                lineTo(9.41f, 8.41f)
                lineTo(8.0f, 7.0f)
                lineToRelative(4.0f, -4.0f)
                lineToRelative(4.0f, 4.0f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(13.0f, 6.83f)
                verticalLineToRelative(5.1f)
                curveToRelative(0.0f, 1.06f, 0.42f, 2.08f, 1.17f, 2.83f)
                lineTo(19.0f, 19.59f)
                lineTo(17.59f, 21.0f)
                lineTo(12.0f, 15.41f)
                lineTo(6.41f, 21.0f)
                close()
            }
        }
        return _merge!!
    }

private var _merge: ImageVector? = null
