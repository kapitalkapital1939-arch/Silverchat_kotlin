package com.silverchat.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.User
import com.silverchat.core.model.UserBadges
import com.silverchat.core.model.labelRu

/**
 * Бейджи статуса: верификация, премиум, разработчик, админ.
 *
 * Все три статуса выдаются из админ-панели @silver, поэтому их отрисовка
 * собрана в одном месте: если дизайнер решит поменять галочку, правка
 * будет в одном файле, а не в 14 экранах.
 */

/** Верификационная галочка. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Int = 16, color: Color = ScTheme.accent) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = "Верифицированный аккаунт",
        tint = color,
        modifier = modifier.size(size.dp),
    )
}

/** Премиум-звезда (SilverChat Premium). */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier, size: Int = 16) {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = "SilverChat Premium",
        tint = ScTheme.premium,
        modifier = modifier.size(size.dp),
    )
}

/** Статус разработчика. */
@Composable
fun DeveloperBadge(modifier: Modifier = Modifier, size: Int = 16) {
    Icon(
        imageVector = Icons.Filled.Bolt,
        contentDescription = "Разработчик SilverChat",
        tint = Color(0xFFA855F7),
        modifier = modifier.size(size.dp),
    )
}

/**
 * Полный набор бейджей пользователя в правильном порядке:
 * владелец -> верификация -> разработчик -> премиум.
 * Порядок фиксирован: галочка владельца важнее обычной верификации.
 */
@Composable
fun UserBadgesRow(
    badges: UserBadges,
    modifier: Modifier = Modifier,
    iconSize: Int = 16,
) {
    if (badges.isEmpty) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (badges.owner) {
            OwnerChip()
        } else if (badges.admin) {
            AdminChip()
        }
        if (badges.verified) VerifiedBadge(size = iconSize)
        if (badges.developer) DeveloperBadge(size = iconSize)
        if (badges.premium) PremiumBadge(size = iconSize)
    }
}

/** Чип владельца продукта (@silver). */
@Composable
fun OwnerChip(modifier: Modifier = Modifier) {
    ChipBadge(text = "OWNER", color = ScTheme.danger, modifier = modifier)
}

@Composable
fun AdminChip(modifier: Modifier = Modifier) {
    ChipBadge(text = "ADMIN", color = Color(0xFFA855F7), modifier = modifier)
}

@Composable
fun ChipBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        modifier = modifier
            .clip(ScShapes.chip)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Роль в админ-панели — отдельная метка (не путать с бейджами пользователя). */
@Composable
fun AdminRoleChip(role: AdminRole, modifier: Modifier = Modifier) {
    val label = role.labelRu
    val color = when (role) {
        AdminRole.OWNER -> ScTheme.danger
        AdminRole.ADMIN -> Color(0xFFA855F7)
        AdminRole.MODERATOR -> ScTheme.accent
        AdminRole.SUPPORT -> ScTheme.info
        AdminRole.MARKET_MANAGER -> ScTheme.warning
        AdminRole.FINANCE -> ScTheme.success
        AdminRole.NONE -> ScTheme.textTertiary
    }
    Text(
        text = label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(ScShapes.chip)
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Бейджи имени в одну строку — используется рядом с именем в списках
 * и в шапке чата, где нет места для отдельной строки бейджей.
 */
@Composable
fun NameWithBadges(user: User?, modifier: Modifier = Modifier, iconSize: Int = 15) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = user?.fullName ?: "SilverChat",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            color = ScTheme.textPrimary,
        )
        user?.badges?.let { UserBadgesRow(it, iconSize = iconSize) }
    }
}
