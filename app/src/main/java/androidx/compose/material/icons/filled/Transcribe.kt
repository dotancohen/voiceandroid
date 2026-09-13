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

public val Icons.Filled.Transcribe: ImageVector
    get() {
        if (_transcribe != null) {
            return _transcribe!!
        }
        _transcribe = materialIcon(name = "Filled.Transcribe") {
            materialPath {
                moveTo(17.93f, 16.0f)
                lineToRelative(1.63f, -1.63f)
                curveToRelative(-2.77f, -3.02f, -2.77f, -7.56f, 0.0f, -10.74f)
                lineTo(17.93f, 2.0f)
                curveTo(14.03f, 5.89f, 14.02f, 11.95f, 17.93f, 16.0f)
                close()
                moveTo(22.92f, 10.95f)
                curveToRelative(-0.84f, -1.18f, -0.84f, -2.71f, 0.0f, -3.89f)
                lineToRelative(-1.68f, -1.69f)
                curveToRelative(-2.02f, 2.02f, -2.02f, 5.07f, 0.0f, 7.27f)
                lineTo(22.92f, 10.95f)
                close()
                moveTo(9.0f, 13.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveTo(5.0f, 6.79f, 5.0f, 9.0f)
                curveTo(5.0f, 11.21f, 6.79f, 13.0f, 9.0f, 13.0f)
                close()
                moveTo(15.39f, 15.56f)
                curveTo(13.71f, 14.7f, 11.53f, 14.0f, 9.0f, 14.0f)
                curveToRelative(-2.53f, 0.0f, -4.71f, 0.7f, -6.39f, 1.56f)
                curveTo(1.61f, 16.07f, 1.0f, 17.1f, 1.0f, 18.22f)
                verticalLineTo(21.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.78f)
                curveTo(17.0f, 17.1f, 16.39f, 16.07f, 15.39f, 15.56f)
                close()
            }
        }
        return _transcribe!!
    }

private var _transcribe: ImageVector? = null
