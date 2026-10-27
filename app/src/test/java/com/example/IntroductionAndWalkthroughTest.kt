package com.example

import com.example.features.walkthrough.WALKTHROUGH_MODULES
import org.junit.Assert.*
import org.junit.Test

class IntroductionAndWalkthroughTest {

    @Test
    fun testWalkthroughModulesCompleteness() {
        // Must contain all 12 core modules plus the Settings & Privacy guide
        assertTrue("Must have at least 13 modules", WALKTHROUGH_MODULES.size >= 13)

        val titles = WALKTHROUGH_MODULES.map { it.title }

        assertTrue(titles.any { it.contains("Introduction", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Dashboard", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Personal Expense", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Official Expense", ignoreCase = true) || it.contains("Claims", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Category", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Borrow", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Income", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Lend", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Investment", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Rotational", ignoreCase = true) || it.contains("Transfer", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Manual Addition", ignoreCase = true) })
        assertTrue(titles.any { it.contains("AI", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Settings", ignoreCase = true) })
    }

    @Test
    fun testOfficialExpenseClaimsLifecycleInWalkthrough() {
        val officialModule = WALKTHROUGH_MODULES.firstOrNull {
            it.title.contains("Official", ignoreCase = true)
        }
        assertNotNull("Official Expenses module must exist", officialModule)

        val allBulletPoints = officialModule!!.sections.flatMap { it.bulletPoints }
        assertTrue("Must explain Pending Claim", allBulletPoints.any { it.contains("Pending Claim", ignoreCase = true) })
        assertTrue("Must explain Applied Claim", allBulletPoints.any { it.contains("Applied Claim", ignoreCase = true) })
        assertTrue("Must explain Reimbursement", allBulletPoints.any { it.contains("Reimbursement", ignoreCase = true) })
        assertTrue("Must explain Company Advance", allBulletPoints.any { it.contains("Advance", ignoreCase = true) })
    }

    @Test
    fun testTransfersExplanationExcludesIncomeExpense() {
        val transferModule = WALKTHROUGH_MODULES.firstOrNull {
            it.title.contains("Transfer", ignoreCase = true) || it.title.contains("Rotational", ignoreCase = true)
        }
        assertNotNull("Transfer module must exist", transferModule)

        val allText = transferModule!!.sections.joinToString(" ") { it.content }
        assertTrue(
            "Transfers must explicitly clarify zero net effect and exclusion from income/expense",
            allText.contains("excluded from both Income and Expense", ignoreCase = true) ||
            allText.contains("double count", ignoreCase = true)
        )
    }

    @Test
    fun testSettingsAndPrivacyGuideContents() {
        val settingsModule = WALKTHROUGH_MODULES.firstOrNull {
            it.title.contains("Settings", ignoreCase = true)
        }
        assertNotNull("Settings and Privacy guide must exist", settingsModule)

        val allText = settingsModule!!.sections.joinToString(" ") { it.content }
        assertTrue(allText.contains("User Profile", ignoreCase = true))
        assertTrue(allText.contains("Finance Categorization", ignoreCase = true))
        assertTrue(allText.contains("Appearance", ignoreCase = true))
        assertTrue(allText.contains("Google Drive", ignoreCase = true))
        assertTrue(allText.contains("Biometrics", ignoreCase = true))
        assertTrue(allText.contains("Privacy Blur", ignoreCase = true))
    }
}
