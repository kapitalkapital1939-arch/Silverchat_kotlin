package com.silverchat.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Радиусы и отступы.
 *
 * Пузыри сообщений имеют «хвост»: у последнего сообщения группы один угол
 * меньше — как в Telegram. Поэтому здесь заданы ОБА варианта.
 */
object ScShapes {
    /* Пузыри */
    val bubbleIncoming = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    val bubbleIncomingTail = RoundedCornerShape(18.dp, 18.dp, 18.dp, 18.dp)
    val bubbleOutgoing = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    val bubbleOutgoingTail = RoundedCornerShape(18.dp, 18.dp, 18.dp, 18.dp)
    val bubbleMedia = RoundedCornerShape(14.dp)
    val bubbleSticker = RoundedCornerShape(0.dp)

    /* Карточки */
    val card = RoundedCornerShape(18.dp)
    val cardSmall = RoundedCornerShape(14.dp)
    val cardLarge = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val dialog = RoundedCornerShape(24.dp)

    /* Элементы */
    val button = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(999.dp)
    val field = RoundedCornerShape(20.dp)
    val avatar = RoundedCornerShape(percent = 50)
    val circleMessage = RoundedCornerShape(percent = 50)
    val banner = RoundedCornerShape(0.dp)
}

/** Отступы — единая сетка 4 dp. */
object ScSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /* Специфика мессенджера */
    val bubblePaddingH = 11.dp
    val bubblePaddingV = 7.dp
    val listHorizontal = 8.dp
    val screenHorizontal = 16.dp
    val betweenMessages = 2.dp
    val betweenGroups = 8.dp
}

/** Размеры аватарок во всех контекстах — иначе они «плавают» между экранами. */
object ScAvatarSize {
    val tray = 60.dp      // сторис-трей
    val listItem = 54.dp  // список чатов
    val message = 38.dp   // аватар отправителя в группе
    val chatHeader = 42.dp
    val profileHero = 104.dp
    val member = 44.dp
    val small = 28.dp
    val call = 132.dp
}

val ScMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)
