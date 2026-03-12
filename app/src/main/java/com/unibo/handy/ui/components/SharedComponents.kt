package com.unibo.handy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import com.unibo.handy.ui.theme.HandyBackground
import com.unibo.handy.ui.theme.HandyPrimary
import com.unibo.handy.ui.theme.HandyPrimaryLight
import com.unibo.handy.ui.theme.HandySecondary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .alpha(alpha)
                .clip(CircleShape)
                .background(HandyPrimary.copy(alpha = 0.3f))
        )
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(60.dp)
                .background(HandyPrimary, CircleShape)
                .padding(12.dp)
        )
    }
}

@Composable
fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, color = Color.DarkGray) },
        leadingIcon = if (isSelected) {
            { Icon(Icons.Default.Check, null, tint = Color.DarkGray) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = HandyBackground,
            selectedContainerColor = Color(0xFFDDD2EA)
        )
    )
}

@Composable
fun RowScope.NavBarItem(index: Int, label: String, icon: ImageVector, selectedIndex: Int, onClick: () -> Unit) {
    NavigationBarItem(
        icon = { Icon(icon, contentDescription = label, tint = if (index == selectedIndex) HandyPrimary else Color.DarkGray) },
        label = { Text(label, color = if (index == selectedIndex) HandySecondary else Color.DarkGray) },
        selected = index == selectedIndex,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(indicatorColor = HandyPrimary.copy(alpha = 0.2f))
    )
}

@Composable
fun LetterAvatar(
    name: String
) {
    val initials = if (name.isNotBlank()) {
        name.trim().take(2).uppercase()
    } else {
        ""
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(75.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, HandyPrimaryLight, CircleShape)
    ) {
        Text(
            text = initials,
            color = HandyPrimaryLight,
            fontSize = (75 / 3.5).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MessageBubble(msg: ChatMessagesEntity, isMe: Boolean) {
    val bubbleColor = if (isMe) Color(0xFFDCF8C6) else Color.White
    val alignment = if (isMe) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(msg.message, fontSize = 16.sp, color = Color.DarkGray)
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(msg.timestamp),
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun ModeSwitchCard(isHelper: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(if (isHelper) "Modalità Helper" else "Modalità Richiedente", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HandyPrimary)
                Text(if (isHelper) "Sei visibile per lavori" else "Cerca professionisti", fontSize = 14.sp, color = Color.Gray)
            }
            Switch(
                checked = isHelper,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HandyPrimary,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.LightGray.copy(alpha = 0.4f)
                )
            )
        }
    }
}