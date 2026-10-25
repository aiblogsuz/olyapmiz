package com.example.lifedots.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WhoTab { ME, DAD, MOM }

@Composable
fun WhoTabs(
    selected: WhoTab,
    onSelected: (WhoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = Color(0xFF1B1A16)
    val inactive = Color(0x33FFFFFF)
    val gold = Color(0xFFFFB300)
    val mutedGold = Color(0xFFC7A35F)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0x14FFFFFF),
        border = BorderStroke(1.dp, Color(0x33FFC62E)),
    ) {
        Row(modifier = Modifier.selectableGroup().padding(4.dp)) {
            WhoTab.values().forEach { tab ->
                val isActive = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(
                            color = if (isActive) active else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .selectable(selected = isActive, role = Role.Tab, onClick = { onSelected(tab) })
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (tab) {
                            WhoTab.ME -> "Me"
                            WhoTab.DAD -> "Dad"
                            WhoTab.MOM -> "Mom"
                        },
                        color = if (isActive) gold else mutedGold,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
