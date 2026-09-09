package com.silverchat.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.glass.AmbientGlassBackground
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.glass.glassSurface
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.feature.auth.AuthStep
import com.silverchat.feature.auth.AuthViewModel

/**
 * Экран входа по номеру телефона.
 *
 * Никаких паролей: вход только по OTP. Это осознанное решение —
 * пароль в мессенджере означает отдельную процедуру сброса, а SIM-карта
 * уже является фактором владения.
 */
@Composable
fun PhoneScreen(
    onOtpReady: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Переход на экран OTP — по смене шага, а не по колбэку из ViewModel:
    // иначе навигация сработала бы дважды при реконфигурации
    LaunchedEffect(state.step) {
        if (state.step == AuthStep.OTP || state.step == AuthStep.REGISTER || state.step == AuthStep.DONE) {
            onOtpReady(state.fullPhone)
        }
    }

    AuthScaffold(modifier = modifier) {
        LogoBadge(icon = Icons.Filled.Phone, tint = ScTheme.accent)

        Spacer(Modifier.height(ScSpacing.lg))

        Text(
            text = "Ваш номер телефона",
            color = ScTheme.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = "Мы отправим код подтверждения в SMS. Номер нужен, чтобы находить знакомых и защищать аккаунт.",
            color = ScTheme.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(ScSpacing.xl))

        // Код страны и номер в одной строке: так поле читается как единый номер
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            SilverTextField(
                value = state.countryDialCode,
                onValueChange = viewModel::onCountryChanged,
                label = "Код",
                keyboardType = KeyboardType.Phone,
                maxLength = 5,
                modifier = Modifier.width(96.dp),
            )
            SilverTextField(
                value = formatPhone(state.phoneDigits),
                onValueChange = { viewModel.onPhoneChanged(it) },
                label = "Номер телефона",
                placeholder = "60 123 456",
                error = state.phoneError,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                maxLength = 13,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(ScSpacing.xl))

        SilverButton(
            text = "Получить код",
            onClick = viewModel::submitPhone,
            enabled = state.canRequestOtp,
            loading = state.requestOtpLoading,
            leadingIcon = Icons.Filled.Sms,
            size = com.silverchat.core.designsystem.component.SilverButtonSize.LARGE,
        )

        Spacer(Modifier.height(ScSpacing.lg))
        Text(
            text = "Продолжая, вы принимаете условия использования и политику конфиденциальности SilverChat.",
            color = ScTheme.textTertiary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Экран ввода OTP-кода. */
@Composable
fun OtpScreen(
    phone: String,
    onRegistered: () -> Unit,
    onNeedProfile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        when (state.step) {
            AuthStep.REGISTER -> onNeedProfile()
            AuthStep.DONE -> onRegistered()
            else -> Unit
        }
    }

    AuthScaffold(modifier = modifier) {
        LogoBadge(icon = Icons.Filled.Sms, tint = ScTheme.accent)

        Spacer(Modifier.height(ScSpacing.lg))
        Text(
            text = "Введите код",
            color = ScTheme.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = "Код отправлен на $phone",
            color = ScTheme.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(ScSpacing.xl))

        // Одно поле на 6 цифр, а не 6 отдельных ячеек: меньше узлов,
        // и вставка кода из SMS работает одним тапом
        OtpInput(
            code = state.otpCode,
            error = state.otpError,
            onCodeChange = viewModel::onOtpChanged,
        )

        Spacer(Modifier.height(ScSpacing.lg))

        SilverButton(
            text = "Подтвердить",
            onClick = viewModel::submitOtp,
            loading = state.verifyLoading,
            enabled = state.otpCode.length == 6,
        )

        Spacer(Modifier.height(ScSpacing.md))

        Text(
            text = if (state.resendSecondsLeft > 0) {
                "Отправить код повторно через ${state.resendSecondsLeft} с"
            } else {
                "Отправить код повторно"
            },
            color = if (state.resendAvailable) ScTheme.accent else ScTheme.textTertiary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ScShapes.chip)
                .clickable(enabled = state.resendAvailable, onClick = viewModel::resendOtp)
                .padding(vertical = 10.dp),
        )

        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = "Изменить номер",
            color = ScTheme.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ScShapes.chip)
                .clickable(onClick = onBack)
                .padding(vertical = 10.dp),
        )
    }
}

/** Экран регистрации профиля после подтверждения номера. */
@Composable
fun RegisterScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step == AuthStep.DONE) onDone()
    }

    AuthScaffold(modifier = modifier) {
        LogoBadge(icon = Icons.Filled.Phone, tint = ScTheme.premium)

        Spacer(Modifier.height(ScSpacing.lg))
        Text(
            text = "Создайте профиль",
            color = ScTheme.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = "Имя увидят собеседники. Юзернейм можно не заполнять — займёте позже или купите в маркете.",
            color = ScTheme.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(ScSpacing.xl))

        SilverTextField(
            value = state.firstName,
            onValueChange = viewModel::onFirstNameChanged,
            label = "Имя",
            placeholder = "Алина",
            maxLength = 64,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(ScSpacing.md))
        SilverTextField(
            value = state.lastName,
            onValueChange = viewModel::onLastNameChanged,
            label = "Фамилия (необязательно)",
            placeholder = "Громова",
            maxLength = 64,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(ScSpacing.md))
        SilverTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChanged,
            label = "Юзернейм (необязательно)",
            placeholder = "alina",
            error = state.usernameError,
            maxLength = 32,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            leadingIcon = Icons.Filled.Phone,
        )

        Spacer(Modifier.height(ScSpacing.xl))

        SilverButton(
            text = "Начать общение",
            onClick = viewModel::submitRegistration,
            loading = state.registerLoading,
            enabled = state.canRegister,
            size = com.silverchat.core.designsystem.component.SilverButtonSize.LARGE,
        )

        if (state.me != null) {
            Spacer(Modifier.height(ScSpacing.md))
            SilverButton(
                text = "Заполнить позже",
                onClick = viewModel::skipRegistration,
                variant = com.silverchat.core.designsystem.component.SilverButtonVariant.TEXT,
            )
        }

        state.registerError?.let {
            Spacer(Modifier.height(ScSpacing.md))
            Text(
                text = it,
                color = ScTheme.danger,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* =========================================================================
   ОБЩИЕ ЭЛЕМЕНТЫ ЭКРАНОВ АВТОРИЗАЦИИ
   ========================================================================= */

/**
 * Каркас экрана авторизации: стеклянная карточка на амбиентном фоне.
 *
 * Стеклянная поверхность здесь не декорация, а способ показать фирменный
 * стиль до входа в приложение — первое, что видит пользователь.
 */
@Composable
private fun AuthScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScopeContent,
) {
    val glassState = androidx.compose.runtime.remember { GlassState() }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ScTheme.backgroundGradientStart, ScTheme.backgroundGradientEnd),
                ),
            ),
    ) {
        AmbientGlassBackground(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(ScSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassSurface(state = glassState, shape = ScShapes.cardLarge, blurRadius = 28.dp)
                    .padding(ScSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}

/** Алиас, чтобы не писать длинную сигнатуру лямбды в каждом экране. */
private typealias ColumnScopeContent = @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit

/** Круглый логотип-бейдж над заголовком. */
@Composable
private fun LogoBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/** Поле ввода OTP: визуально 6 ячеек, технически — один TextField. */
@Composable
private fun OtpInput(
    code: String,
    error: String?,
    onCodeChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            // Ячейки рисуются под прозрачным полем: фокус и вставка из буфера
            // работают как у обычного TextField, а выглядят как PIN-ввод
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm, Alignment.CenterHorizontally),
            ) {
                repeat(6) { index ->
                    val filled = index < code.length
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(ScShapes.cardSmall)
                            .background(if (error != null) ScTheme.danger.copy(alpha = 0.12f) else ScTheme.surface)
                            .border(
                                width = 1.dp,
                                color = when {
                                    error != null -> ScTheme.danger
                                    index == code.length -> ScTheme.accent
                                    else -> ScTheme.outline
                                },
                                shape = ScShapes.cardSmall,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (filled) code[index].toString() else "",
                            color = ScTheme.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        // Подсветка активной ячейки
                        if (index == code.length && error == null) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(22.dp)
                                    .clip(CircleShape)
                                    .background(ScTheme.accent),
                            )
                        }
                    }
                }
            }

            androidx.compose.foundation.text.BasicTextField(
                value = code,
                onValueChange = { newValue -> onCodeChange(newValue.filter { it.isDigit() }.take(6)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .invisibleButInteractive(),
            )
        }

        error?.let {
            Spacer(Modifier.height(ScSpacing.sm))
            Text(it, color = ScTheme.danger, fontSize = 12.5.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Делает поле невидимым, сохраняя размеры и обработку ввода.
 *
 * Именно alpha(0.01f), а не 0f: при полной прозрачности Compose может
 * исключить узел из отрисовки вместе с обработкой фокуса на некоторых
 * версиях — курсор перестаёт ловить вставку из SMS.
 */
private fun Modifier.invisibleButInteractive(): Modifier = this.alpha(0.01f)

/** Группировка номера по маске: 60 123 456. */
private fun formatPhone(digits: String): String = buildString {
    digits.forEachIndexed { index, ch ->
        if (index == 2 || index == 5 || index == 8) append(' ')
        append(ch)
    }
}
