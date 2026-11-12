package com.example.features.walkthrough

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FilterSelectionBlue

enum class SpotlightStep(
    val stepIndex: Int,
    val title: String,
    val description: String,
    val actionHint: String
) {
    IMPORT_STATEMENT(
        1,
        "Import Bank Statement",
        "Start by importing an official bank statement (PDF, Excel .xlsx, or CSV). All records are parsed 100% offline on your device with local password decryption.",
        "Password-protected statements from SBI, HDFC, ICICI, etc. are decrypted locally."
    ),
    STATEMENTS_LEDGER(
        2,
        "Statement Passbook & Search",
        "Browse your complete chronological bank passbook. Instantly search by transaction narration, merchant, UPI ID, or amount to find any record in seconds.",
        "Tap any transaction to view full audit details, edit notes, or reassign categories."
    ),
    STATEMENTS_FILTER(
        3,
        "Flipkart-Style Filter Drawer",
        "Open the two-column filter drawer to filter by Category, Account, Transaction Type, and Custom Date Range with blue selection accents.",
        "Active filters dynamically update your passbook, monthly summary, and PDF export."
    ),
    IMPORT_HISTORY(
        4,
        "Where to See All Imported Statements",
        "Review previously parsed bank statements, batch records, date spans, and automated mathematical audit outcomes in one place.",
        "Access your statement history anytime via the 'Import' button on top of Statement records."
    ),
    OFFICIAL_EXPENSES(
        5,
        "Unified 3-in-1 Official Claims",
        "Manage your work expense lifecycle in one unified ledger (Pending → Applied → Reimbursed), and track corporate advance balances (#ADV).",
        "Select multiple claims to batch mark them as applied or reimbursed in one tap."
    ),
    DASHBOARD_CASHFLOW(
        6,
        "Quick Add Records (+)",
        "Log cash expenses, income, loans, or corporate claims instantly on the fly without waiting for a bank statement.",
        "Tap '+' on top anytime to record physical cash spends or split payments."
    ),
    REPORTS_PDF(
        7,
        "Scoped Reports & PDF Export",
        "Generate audit-ready PDF passbooks and Excel spreadsheets with itemized particulars and monthly summaries strictly scoped to your active filters.",
        "Export clean PDFs directly to your phone storage or share via email and WhatsApp."
    ),
    SETTINGS_SECURITY(
        8,
        "Security, Privacy & Cloud Backup",
        "Protect your financial privacy with Biometric Lock and Privacy Blur. Back up encrypted data locally or sync to Google Drive.",
        "You're all set! Tap 'Get Started' to begin managing your finances."
    )
}

@Composable
fun SpotlightTourOverlay(
    step: SpotlightStep,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    // Intercept clicks on scrim so user focuses on tour
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("spotlight_tour_overlay")
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* Consume taps outside */ }
    ) {
        // 1. Dimmed Scrim with clear cutout over target bounds
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Dim screen
                drawRect(Color.Black.copy(alpha = 0.68f))

                targetBounds?.let { rawRect ->
                    val padding = 8.dp.toPx()
                    val cutoutRect = Rect(
                        left = (rawRect.left - padding).coerceAtLeast(4f),
                        top = (rawRect.top - padding).coerceAtLeast(4f),
                        right = (rawRect.right + padding).coerceAtMost(size.width - 4f),
                        bottom = (rawRect.bottom + padding).coerceAtMost(size.height - 4f)
                    )

                    // Clear cutout hole
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(cutoutRect.left, cutoutRect.top),
                        size = Size(cutoutRect.width, cutoutRect.height),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )

                    // Glowing accent border around spotlight
                    drawRoundRect(
                        color = FilterSelectionBlue,
                        topLeft = Offset(cutoutRect.left, cutoutRect.top),
                        size = Size(cutoutRect.width, cutoutRect.height),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
        }

        // 2. Floating OnRent-Style Tooltip Card
        val isTargetInBottomHalf = targetBounds != null && (targetBounds.top > screenHeightPx * 0.45f)
        val pointerAlignment = if (isTargetInBottomHalf) Alignment.BottomCenter else Alignment.TopCenter

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = pointerAlignment
        ) {
            val cardOffsetModifier = if (targetBounds != null) {
                if (isTargetInBottomHalf) {
                    val targetTopDp = with(density) { targetBounds.top.toDp() }
                    Modifier.padding(bottom = (configuration.screenHeightDp.dp - targetTopDp + 12.dp).coerceAtLeast(20.dp))
                } else {
                    val targetBottomDp = with(density) { targetBounds.bottom.toDp() }
                    Modifier.padding(top = (targetBottomDp + 12.dp).coerceAtLeast(20.dp))
                }
            } else {
                Modifier.align(Alignment.Center)
            }

                        // Calculate arrow horizontal offset matching target center
            val arrowOffset = if (targetBounds != null) {
                val targetCenterXDp = with(density) { ((targetBounds.left + targetBounds.right) / 2f).toDp() }
                (targetCenterXDp - (configuration.screenWidthDp.dp / 2f)).coerceIn(-120.dp, 120.dp)
            } else {
                0.dp
            }

            Column(
                modifier = cardOffsetModifier.fillMaxWidth(0.94f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Directional Pointer Arrow (if pointing UP to target above)
                if (!isTargetInBottomHalf && targetBounds != null) {
                    TooltipArrow(
                        pointingUp = true,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.offset(x = arrowOffset)
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Header: Sparkle + Tours from Yosan + Dismiss [✕]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = FilterSelectionBlue.copy(alpha = 0.15f),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = FilterSelectionBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tours from Yosan",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FilterSelectionBlue
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tour",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Description
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Pro-Tip / Hint Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = FilterSelectionBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = step.actionHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Footer: Step X of 8 + [Back] & [Next] Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${step.stepIndex} of ${SpotlightStep.values().size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (step.stepIndex > 1) {
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onBack()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Back", fontSize = 12.5.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNext()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = FilterSelectionBlue),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (step.stepIndex == SpotlightStep.values().size) "Get Started" else "Next",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (step.stepIndex == SpotlightStep.values().size) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Directional Pointer Arrow (if pointing DOWN to target below)
                if (isTargetInBottomHalf && targetBounds != null) {
                    TooltipArrow(
                        pointingUp = false,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.offset(x = arrowOffset)
                    )
                }
            }
        }
    }
}

@Composable
fun TooltipArrow(
    pointingUp: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width = 18.dp, height = 9.dp)
    ) {
        val path = Path()
        if (pointingUp) {
            path.moveTo(size.width / 2f, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
        } else {
            path.moveTo(0f, 0f)
            path.lineTo(size.width, 0f)
            path.lineTo(size.width / 2f, size.height)
        }
        path.close()
        drawPath(path = path, color = color)
    }
}
