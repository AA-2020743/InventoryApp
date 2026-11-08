package com.supermarket.inventory.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.ui.theme.lossColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// One end of a payment as the confirmation draws it: who it is, and what
// they hold (the till) or owe (a supplier, a customer) before the money
// moves. `sign` is which way the payment moves that figure: the till paying
// a supplier goes down (-1), the till collecting from a customer goes up
// (+1), and what either of them owes goes down.
data class PaymentSide(
    val icon: ImageVector,
    val name: String,
    val caption: String,
    val before: BigDecimal?,
    val sign: Int,
    val tint: Color,
)

// "Are you sure?" for a payment, drawn as the payment itself: the money
// flowing from one side to the other, what each side holds before and after,
// and a slide-to-confirm instead of a button, so a stray tap on a busy
// counter can't pay or collect anything.
//
// With `fixedAmount` it confirms one known amount (pay the invoice, collect
// the tab). Without it, it asks how much first, up to `maxAmount`, and the
// picture follows what's typed. `tillBalance` caps an outflow at what the
// till holds, the same limit the server enforces.
//
// `onConfirm` returns null once the payment is recorded, or the reason it
// wasn't; the sheet stays open to show that reason.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentConfirmSheet(
    question: String,
    from: PaymentSide,
    to: PaymentSide,
    accent: Color,
    slideLabel: String,
    maxAmount: BigDecimal,
    fixedAmount: BigDecimal? = null,
    tillBalance: BigDecimal? = null,
    onConfirm: suspend (BigDecimal) -> String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val amount = fixedAmount ?: typed.toMoneyOrNull()
    val problem = when {
        fixedAmount == null && typed.isNotBlank() && (amount == null || amount.signum() <= 0) ->
            stringResource(R.string.deferred_sale_invalid_amount)
        amount != null && amount > maxAmount ->
            stringResource(R.string.pay_confirm_exceeds, formatAmount(maxAmount.toPlainString()))
        amount != null && tillBalance != null && amount > tillBalance.max(BigDecimal.ZERO) ->
            stringResource(R.string.pay_confirm_till_short, formatAmount(tillBalance.max(BigDecimal.ZERO).toPlainString()))
        else -> null
    }

    // A moment on the tick, then out of the way.
    LaunchedEffect(done) {
        if (done) {
            delay(700)
            onDismiss()
        }
    }

    val amountField: (@Composable () -> Unit)? = if (fixedAmount == null) {
        @Composable { AmountField(typed, maxAmount, enabled = !busy && !done, onChange = { typed = it; error = null }) }
    } else {
        null
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        PaymentConfirmContent(
            question = question,
            from = from,
            to = to,
            accent = accent,
            slideLabel = slideLabel,
            amount = amount?.takeIf { it.signum() > 0 },
            maxAmount = maxAmount,
            amountField = amountField,
            problem = problem,
            error = error,
            busy = busy,
            done = done,
            onSlide = {
                amount?.let { value ->
                    scope.launch {
                        busy = true
                        error = null
                        val failure = onConfirm(value)
                        busy = false
                        if (failure == null) done = true else error = failure
                    }
                }
            },
            onCancel = { if (!busy) onDismiss() },
        )
    }
}

// The sheet's body without the sheet, so it can be drawn on its own (the
// screenshot tests do) and everything it shows comes from its arguments.
@Composable
fun PaymentConfirmContent(
    question: String,
    from: PaymentSide,
    to: PaymentSide,
    accent: Color,
    slideLabel: String,
    amount: BigDecimal?,
    maxAmount: BigDecimal,
    amountField: (@Composable () -> Unit)?,
    problem: String?,
    error: String?,
    busy: Boolean,
    done: Boolean,
    onSlide: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
    ) {
        Text(question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (amountField != null) {
            Spacer(Modifier.height(12.dp))
            amountField()
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            SideRow(from, amount)
            FlowLine(accent, amount)
            SideRow(to, amount)
        }

        val note = when {
            error != null -> error to lossColor()
            problem != null -> problem to lossColor()
            amount != null && amount.compareTo(maxAmount) == 0 && amountField != null ->
                stringResource(R.string.pay_confirm_settles) to accent
            else -> null
        }
        if (note != null) {
            Text(
                note.first,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = note.second,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        SlideToConfirm(
            label = slideLabel,
            accent = accent,
            enabled = amount != null && problem == null,
            busy = busy,
            done = done,
            onConfirmed = onSlide,
        )
        TextButton(onClick = onCancel, enabled = !busy && !done, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
internal fun AmountField(value: String, maxAmount: BigDecimal, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.pay_confirm_amount)) },
        suffix = { Text("LE") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    // The two amounts people reach for: half of what's left, and all of it.
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val half = maxAmount.divide(BigDecimal(2), 2, RoundingMode.HALF_UP).toPlainString()
        val all = maxAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        FilterChip(selected = value == half, enabled = enabled, onClick = { onChange(half) }, label = { Text(stringResource(R.string.pay_confirm_half)) })
        FilterChip(selected = value == all, enabled = enabled, onClick = { onChange(all) }, label = { Text(stringResource(R.string.pay_confirm_all)) })
    }
}

// One end of the payment: who, and their figure once the money has moved,
// with what it was underneath.
@Composable
private fun SideRow(side: PaymentSide, amount: BigDecimal?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(side.tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(side.icon, contentDescription = null, tint = side.tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(side.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(side.caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val before = side.before
        if (before != null) {
            val after = if (amount == null) before else before.add(amount.multiply(BigDecimal(side.sign)))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatAmount(after.toPlainString()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    // Below zero only when the till can't cover it - which the
                    // sheet refuses, and this shows why at a glance.
                    color = if (after.signum() < 0) lossColor() else MaterialTheme.colorScheme.onSurface,
                )
                if (amount != null) {
                    Text(
                        stringResource(R.string.pay_confirm_was, formatAmount(before.toPlainString())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// The money on its way: dots running down a line from the payer to the
// payee, with the amount riding beside them.
@Composable
private fun FlowLine(accent: Color, amount: BigDecimal?) {
    val phase by rememberInfiniteTransition(label = "flow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(44.dp).height(52.dp)) {
            val x = size.width / 2
            drawLine(accent.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            repeat(3) { i ->
                val t = (phase + i / 3f) % 1f
                drawCircle(accent.copy(alpha = sin(t * PI).toFloat().coerceIn(0f, 1f)), radius = 4.dp.toPx(), center = Offset(x, t * size.height))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (amount != null) formatAmount(amount.toPlainString()) else "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

// Drag the disc to the far end to confirm. Let go short of it and it slides
// back; a refused payment slides it back too. Screen readers get it as a
// button with the same label, since a drag is no way to confirm by touch
// exploration.
@Composable
private fun SlideToConfirm(
    label: String,
    accent: Color,
    enabled: Boolean,
    busy: Boolean,
    done: Boolean,
    onConfirmed: () -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val thumb = 52.dp
    val inset = 4.dp
    var trackWidth by remember { mutableFloatStateOf(0f) }
    val maxOffset = (trackWidth - with(density) { (thumb + inset * 2).toPx() }).coerceAtLeast(0f)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val active = enabled && !busy && !done

    LaunchedEffect(busy, done, enabled) {
        if (done) offset.animateTo(maxOffset) else if (!busy) offset.animateTo(0f)
    }

    val progress = if (maxOffset > 0f) offset.value / maxOffset else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(thumb + inset * 2)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = if (enabled) 0.14f else 0.06f))
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .semantics {
                role = Role.Button
                contentDescription = label
                if (active) onClick(label) { onConfirmed(); true }
            },
    ) {
        // The track fills behind the disc as it goes.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(with(density) { offset.value.toDp() } + thumb + inset * 2)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = if (enabled) 0.22f else 0f)),
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = thumb + inset * 2)
                .graphicsLayer { alpha = 1f - progress },
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .padding(inset)
                .size(thumb)
                .clip(CircleShape)
                .background(if (enabled) accent else MaterialTheme.colorScheme.outlineVariant)
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxOffset)) }
                    },
                    orientation = Orientation.Horizontal,
                    enabled = active,
                    reverseDirection = rtl,
                    onDragStopped = {
                        if (maxOffset > 0f && offset.value >= maxOffset * 0.85f) {
                            offset.animateTo(maxOffset)
                            onConfirmed()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val onAccent = MaterialTheme.colorScheme.surface
            when {
                busy -> CircularProgressIndicator(Modifier.size(22.dp), color = onAccent, strokeWidth = 2.5.dp)
                done -> Icon(Icons.Filled.Check, contentDescription = null, tint = onAccent)
                else -> Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = onAccent)
            }
        }
    }
}

private fun String.toMoneyOrNull(): BigDecimal? =
    trim().replace(',', '.').toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
