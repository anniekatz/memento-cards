package com.annie.memento.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PanelShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
val InsetShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
val ButtonShape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp)
val ChipShape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
val TileShape = CutCornerShape(topStart = 11.dp, bottomEnd = 11.dp)
val CardPanelShape = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
val MementoShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = InsetShape,
    large = PanelShape,
    extraLarge = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp),
)
