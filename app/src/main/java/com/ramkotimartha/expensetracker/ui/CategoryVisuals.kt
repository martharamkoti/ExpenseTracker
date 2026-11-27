package com.ramkotimartha.expensetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import java.util.Locale
import kotlin.math.abs

data class CategoryVisualOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val softColor: Color,
    val primaryForType: TransactionType? = null // null = universal
)

/**
 * Provides simple, soft rounded icons and harmonious muted pastel colors for every
 * Expense and Income tag — both default tags and custom user-created tags.
 */
object CategoryVisuals {

    val ALL_OPTIONS: List<CategoryVisualOption> = listOf(
        // Expense-focused options
        CategoryVisualOption("food", "Food", Icons.Rounded.Restaurant, Color(0xFFFF8A65), TransactionType.EXPENSE),
        CategoryVisualOption("groceries", "Groceries", Icons.Rounded.ShoppingBasket, Color(0xFF81C784), TransactionType.EXPENSE),
        CategoryVisualOption("coffee", "Cafe", Icons.Rounded.LocalCafe, Color(0xFFA1887F), TransactionType.EXPENSE),
        CategoryVisualOption("transport", "Transit", Icons.Rounded.DirectionsCar, Color(0xFF64B5F6), TransactionType.EXPENSE),
        CategoryVisualOption("travel", "Travel", Icons.Rounded.Flight, Color(0xFF4FC3F7), TransactionType.EXPENSE),
        CategoryVisualOption("fuel", "Fuel", Icons.Rounded.LocalGasStation, Color(0xFF7986CB), TransactionType.EXPENSE),
        CategoryVisualOption("bills", "Bills", Icons.AutoMirrored.Rounded.ReceiptLong, Color(0xFFFFB74D), TransactionType.EXPENSE),
        CategoryVisualOption("utilities", "Utilities", Icons.Rounded.Bolt, Color(0xFFFFCA28), TransactionType.EXPENSE),
        CategoryVisualOption("home", "Home", Icons.Rounded.Home, Color(0xFF4DB6AC), TransactionType.EXPENSE),
        CategoryVisualOption("shopping", "Shopping", Icons.Rounded.ShoppingBag, Color(0xFF9575CD), TransactionType.EXPENSE),
        CategoryVisualOption("health", "Health", Icons.Rounded.MedicalServices, Color(0xFFF06292), TransactionType.EXPENSE),
        CategoryVisualOption("fitness", "Fitness", Icons.Rounded.FitnessCenter, Color(0xFFE57373), TransactionType.EXPENSE),
        CategoryVisualOption("subscriptions", "Subs", Icons.Rounded.Autorenew, Color(0xFFBA68C8), TransactionType.EXPENSE),
        CategoryVisualOption("entertainment", "Fun", Icons.Rounded.Movie, Color(0xFF7E57C2), TransactionType.EXPENSE),
        CategoryVisualOption("education", "Study", Icons.Rounded.School, Color(0xFF5C6BC0), TransactionType.EXPENSE),
        CategoryVisualOption("gifts", "Gifts", Icons.Rounded.CardGiftcard, Color(0xFFEC407A), null),
        CategoryVisualOption("pets", "Pets", Icons.Rounded.Pets, Color(0xFF8D6E63), TransactionType.EXPENSE),

        // Income-focused & universal options
        CategoryVisualOption("salary", "Salary", Icons.Rounded.AccountBalanceWallet, Color(0xFF66BB6A), TransactionType.INCOME),
        CategoryVisualOption("work", "Work", Icons.Rounded.WorkOutline, Color(0xFF4DB6AC), TransactionType.INCOME),
        CategoryVisualOption("investments", "Invest", Icons.AutoMirrored.Rounded.TrendingUp, Color(0xFF64B5F6), TransactionType.INCOME),
        CategoryVisualOption("savings", "Savings", Icons.Rounded.Savings, Color(0xFF9CCC65), TransactionType.INCOME),
        CategoryVisualOption("bank", "Bank", Icons.Rounded.AccountBalance, Color(0xFF78909C), TransactionType.INCOME),
        CategoryVisualOption("other", "General", Icons.Rounded.Sell, Color(0xFF90A4AE), null)
    )

    private val OPTION_BY_KEY = ALL_OPTIONS.associateBy { it.key }

    private val FALLBACK_SOFT_PALETTE = listOf(
        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFF9575CD),
        Color(0xFFFFB74D),
        Color(0xFFF06292),
        Color(0xFF4DB6AC),
        Color(0xFFFF8A65),
        Color(0xFF7986CB)
    )

    fun optionsForType(type: TransactionType): List<CategoryVisualOption> {
        return ALL_OPTIONS.sortedBy { option ->
            when (option.primaryForType) {
                type -> 0
                null -> 1
                else -> 2
            }
        }
    }

    fun resolve(tag: Tag): CategoryVisualOption {
        tag.iconKey?.let { key ->
            OPTION_BY_KEY[key]?.let { return it }
        }
        val inferredKey = inferIconKey(tag.name, tag.type)
        val base = OPTION_BY_KEY[inferredKey] ?: OPTION_BY_KEY.getValue("other")
        // Give custom "other" tags a deterministic soft tint based on tag name so they stay visually distinct
        return if (base.key == "other" && !tag.name.equals("Other", ignoreCase = true)) {
            val paletteColor = FALLBACK_SOFT_PALETTE[abs(tag.name.lowercase(Locale.ROOT).hashCode()) % FALLBACK_SOFT_PALETTE.size]
            base.copy(softColor = paletteColor)
        } else {
            base
        }
    }

    fun inferIconKey(name: String, type: TransactionType): String {
        val n = name.trim().lowercase(Locale.ROOT)
        if (n.isEmpty()) {
            return if (type == TransactionType.INCOME) "salary" else "other"
        }

        return when {
            containsAny(n, "coffee", "cafe", "tea", "chai", "bakery") -> "coffee"
            containsAny(n, "food", "dining", "lunch", "dinner", "breakfast", "restaurant", "snack", "eat") -> "food"
            containsAny(n, "grocer", "supermarket", "vegetable", "milk", "mart", "provision", "kirana") -> "groceries"
            containsAny(n, "flight", "travel", "trip", "vacation", "holiday", "hotel", "train") -> "travel"
            containsAny(n, "fuel", "petrol", "diesel", "gas station", "cng") -> "fuel"
            containsAny(n, "transport", "transit", "commute", "cab", "uber", "ola", "auto", "bus", "metro", "car", "bike", "parking") -> "transport"
            containsAny(n, "electric", "water", "power", "wifi", "internet", "broadband", "gas", "utilit", "recharge", "mobile") -> "utilities"
            containsAny(n, "rent", "home", "house", "flat", "housing", "maintenance", "furniture") -> "home"
            containsAny(n, "subscrip", "netflix", "spotify", "streaming", "ott", "membership") -> "subscriptions"
            containsAny(n, "bill", "emi", "loan", "insurance", "tax", "credit") -> "bills"
            containsAny(n, "gym", "fitness", "sport", "workout", "yoga") -> "fitness"
            containsAny(n, "health", "medic", "doctor", "hospital", "pharm", "dental", "clinic") -> "health"
            containsAny(n, "movie", "entertain", "fun", "game", "gaming", "cinema", "party", "leisure") -> "entertainment"
            containsAny(n, "educat", "study", "school", "college", "book", "course", "tuition") -> "education"
            containsAny(n, "gift", "present", "donat", "charity") -> "gifts"
            containsAny(n, "pet", "dog", "cat", "vet") -> "pets"
            containsAny(n, "shop", "cloth", "apparel", "fashion", "shoe", "electronic", "gadget", "beauty", "salon") -> "shopping"
            containsAny(n, "salary", "paycheck", "payroll", "wage", "stipend") -> "salary"
            containsAny(n, "freelance", "work", "client", "business", "consult", "project", "commission") -> "work"
            containsAny(n, "invest", "dividend", "stock", "mutual", "share", "interest", "return", "profit") -> "investments"
            containsAny(n, "refund", "cashback", "reward", "reimburse", "bonus", "saving", "allowance") -> "savings"
            containsAny(n, "bank", "transfer", "deposit") -> "bank"
            type == TransactionType.INCOME && n == "other" -> "savings"
            else -> "other"
        }
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }
}

@Composable
fun CategoryIconBadge(
    tag: Tag,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    val visual = remember(tag.id, tag.name, tag.type, tag.iconKey) {
        CategoryVisuals.resolve(tag)
    }
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(visual.softColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.softColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun CreateTagDialog(
    transactionType: TransactionType,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String) -> Unit
) {
    val options = remember(transactionType) { CategoryVisuals.optionsForType(transactionType) }
    var name by remember { mutableStateOf("") }
    var selectedIconKey by remember(transactionType) {
        mutableStateOf(if (transactionType == TransactionType.INCOME) "salary" else "other")
    }
    var isIconManuallyChosen by remember { mutableStateOf(false) }

    // Automatically highlight the matching icon while typing unless the user explicitly picked one
    LaunchedEffect(name, transactionType) {
        if (!isIconManuallyChosen) {
            selectedIconKey = CategoryVisuals.inferIconKey(name, transactionType)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (transactionType == TransactionType.EXPENSE) "New Expense Tag" else "New Income Tag",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            if (transactionType == TransactionType.EXPENSE) "e.g. Subscriptions" else "e.g. Freelance"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Choose Icon",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options, key = { it.key }) { option ->
                        val isSelected = selectedIconKey == option.key
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) {
                                option.softColor.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, option.softColor)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selectedIconKey = option.key
                                    isIconManuallyChosen = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.label,
                                    tint = if (isSelected) {
                                        option.softColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed, selectedIconKey)
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
