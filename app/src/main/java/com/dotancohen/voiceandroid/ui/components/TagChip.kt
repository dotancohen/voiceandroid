package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.data.TagColours

/**
 * One Tag, in its colour.
 *
 * The colour is the Tag's own (chosen, or calculated from its name), and the
 * label is black or white depending on how bright that colour is, since a
 * colour taken from a hash is as likely to be pale as dark.
 */
@Composable
fun TagChip(name: String, colour: String? = null, modifier: Modifier = Modifier) {
    val hex = TagColours.colourFor(name, colour)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = Color(TagColours.argbFor(name, colour))
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (TagColours.prefersDarkText(hex)) Color.Black else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
