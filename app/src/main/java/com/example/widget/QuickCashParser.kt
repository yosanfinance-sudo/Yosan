package com.example.widget

import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import java.util.Locale

data class ParsedCashEntry(
    val amount: Double,
    val note: String,
    val suggestedCategoryName: String
)

object QuickCashParser {

    private val KEYWORD_TO_CATEGORY = listOf(
        listOf("tea", "chai", "coffee", "lunch", "dinner", "breakfast", "snack", "snacks", "food", "cafe", "restaurant", "burger", "pizza", "biryani", "dosa", "samosa", "swiggy", "zomato", "eat", "hotel", "ice cream", "juice", "bakery")
            to listOf("Food & Dining", "Food", "Dining", "Meals"),

        listOf("petrol", "diesel", "fuel", "auto", "cab", "taxi", "uber", "ola", "bus", "metro", "toll", "parking", "train", "rickshaw", "fare", "transport", "travel", "rapido", "flight")
            to listOf("Transportation", "Travel & Fuel", "Travel", "Fuel", "Transport"),

        listOf("grocery", "groceries", "milk", "vegetable", "vegetables", "veggies", "fruit", "fruits", "sabji", "supermarket", "mart", "bread", "egg", "eggs", "ration", "oil", "rice", "dal", "atta", "curd", "paneer")
            to listOf("Groceries", "Grocery", "Daily Needs"),

        listOf("cloth", "clothes", "shirt", "pant", "pants", "shoes", "shoe", "shopping", "mall", "amazon", "flipkart", "book", "stationery", "bag", "dress", "tshirt", "jeans")
            to listOf("Shopping", "Clothing", "Lifestyle"),

        listOf("recharge", "mobile", "wifi", "electricity", "current", "power", "water", "gas", "cylinder", "bill", "rent", "maintenance", "broadband", "dth", "recharge")
            to listOf("Bills & Utilities", "Bills", "Utilities", "Rent"),

        listOf("medicine", "doctor", "pharmacy", "tablet", "tablets", "hospital", "clinic", "medical", "health", "dentist", "treatment")
            to listOf("Healthcare", "Health & Care", "Medical", "Health"),

        listOf("haircut", "salon", "spa", "shave", "parlour", "soap", "shampoo", "perfume", "trimming")
            to listOf("Personal Care", "Personal", "Self Care"),

        listOf("movie", "cinema", "game", "netflix", "show", "theatre", "popcorn", "outing", "party")
            to listOf("Entertainment", "Movies", "Outing"),

        listOf("salary", "wage", "freelance", "stipend", "bonus", "profit", "interest")
            to listOf("Salary", "Income", "Freelance")
    )

    fun parse(rawInput: String): ParsedCashEntry {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return ParsedCashEntry(amount = 0.0, note = "", suggestedCategoryName = "")
        }

        // 1. Look for currency numbers (e.g. 50, 120.50, ₹100, Rs. 200, 500rs, 500 rupees)
        val amountRegex = Regex("""(?i)(?:rs\.?|₹|inr)?\s*(\d+(?:\.\d{1,2})?)\s*(?:rs|rupees|rupee|inr|₹|bucks)?""")
        val match = amountRegex.findAll(trimmed).firstOrNull { m ->
            val numStr = m.groupValues[1]
            numStr.toDoubleOrNull() != null && (numStr.toDoubleOrNull() ?: 0.0) > 0.0
        }

        val amount = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        // 2. Extract note by stripping the matched amount and common filler words
        var cleanedNote = if (match != null) {
            trimmed.removeRange(match.range).trim()
        } else {
            trimmed
        }

        val fillerWords = listOf(
            "(?i)\\b(?:spent|spend|paid|pay|give|gave|given|bought|buy)\\b",
            "(?i)\\b(?:for|on|at|in|to|towards|of)\\b",
            "(?i)\\b(?:rs|rupees|rupee|inr|bucks|cash|in cash)\\b",
            "(?i)\\b(?:rupees|rupee|rs\\.?|₹)\\b"
        )

        for (pattern in fillerWords) {
            cleanedNote = cleanedNote.replace(Regex(pattern), " ")
        }

        cleanedNote = cleanedNote.replace(Regex("""[^\w\s\-]"""), " ").replace(Regex("""\s+"""), " ").trim()

        if (cleanedNote.isBlank()) {
            cleanedNote = if (amount > 0.0) "Cash Expense" else ""
        } else {
            cleanedNote = cleanedNote.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

        // 3. Detect suggested category name
        val combinedText = "$trimmed $cleanedNote".lowercase(Locale.getDefault())
        var suggestedCat = ""

        for ((keywords, possibleNames) in KEYWORD_TO_CATEGORY) {
            if (keywords.any { kw -> combinedText.contains(Regex("""\b${Regex.escape(kw)}\b""")) }) {
                suggestedCat = possibleNames.first()
                break
            }
        }

        return ParsedCashEntry(
            amount = amount,
            note = cleanedNote,
            suggestedCategoryName = suggestedCat
        )
    }

    fun matchCategory(suggestedName: String, categories: List<CategoryEntity>): CategoryEntity? {
        if (categories.isEmpty()) return null
        if (suggestedName.isBlank()) return null

        // Direct match
        val direct = categories.firstOrNull { it.name.equals(suggestedName, ignoreCase = true) }
        if (direct != null) return direct

        // Keyword list match
        for ((_, possibleNames) in KEYWORD_TO_CATEGORY) {
            if (possibleNames.any { it.equals(suggestedName, ignoreCase = true) }) {
                val matched = categories.firstOrNull { cat ->
                    possibleNames.any { it.equals(cat.name, ignoreCase = true) }
                }
                if (matched != null) return matched
            }
        }

        // Partial match
        return categories.firstOrNull {
            it.name.contains(suggestedName, ignoreCase = true) || suggestedName.contains(it.name, ignoreCase = true)
        }
    }
}
