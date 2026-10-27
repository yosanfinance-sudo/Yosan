package com.example

import com.example.data.local.entity.PendingFeedbackEntity
import com.example.data.remote.ChatMessageDto
import com.example.data.remote.ChatRequest
import com.example.data.remote.ChatResponse
import com.example.data.remote.FeedbackRequest
import com.example.data.remote.FeedbackResponse
import com.example.ui.components.MarkdownElement
import com.example.ui.components.parseMarkdownText
import org.junit.Assert.*
import org.junit.Test

class ChatAndFeedbackTest {

    @Test
    fun testMarkdownTableParsing() {
        val markdown = """
            Here is your spending overview:

            | Category | Amount | Share |
            | :--- | :--- | :--- |
            | Food & Dining | ₹4,500 | 45% |
            | Transportation | ₹2,500 | 25% |
            | Utilities | ₹1,500 | 15% |

            Keep up the good savings habit!
        """.trimIndent()

        val elements = parseMarkdownText(markdown)
        assertTrue("Should have multiple parsed elements", elements.size >= 3)

        val tableElement = elements.find { it is MarkdownElement.Table } as? MarkdownElement.Table
        assertNotNull("Must find a Table element", tableElement)

        assertEquals("Headers count should be 3", 3, tableElement!!.headers.size)
        assertEquals("Category", tableElement.headers[0])
        assertEquals("Amount", tableElement.headers[1])
        assertEquals("Share", tableElement.headers[2])

        assertEquals("Should have 3 data rows", 3, tableElement.rows.size)
        assertEquals("Food & Dining", tableElement.rows[0][0])
        assertEquals("₹4,500", tableElement.rows[0][1])
        assertEquals("45%", tableElement.rows[0][2])

        assertEquals("Utilities", tableElement.rows[2][0])
    }

    @Test
    fun testMarkdownHeaderAndBulletParsing() {
        val markdown = """
            # Monthly Financial Review
            ### Top Highlights
            - Reduced discretionary spending by 10%
            - Paid credit card bill on time
            Regular paragraph text here.
        """.trimIndent()

        val elements = parseMarkdownText(markdown)
        assertEquals(5, elements.size)

        val h1 = elements[0] as MarkdownElement.Header
        assertEquals(1, h1.level)
        assertEquals("Monthly Financial Review", h1.text)

        val h3 = elements[1] as MarkdownElement.Header
        assertEquals(3, h3.level)
        assertEquals("Top Highlights", h3.text)

        val b1 = elements[2] as MarkdownElement.BulletPoint
        assertEquals("Reduced discretionary spending by 10%", b1.text)

        val p = elements[4] as MarkdownElement.Paragraph
        assertEquals("Regular paragraph text here.", p.text)
    }

    @Test
    fun testMarkdownTableWithUnequalColumnsHandledGracefully() {
        val markdown = """
            | Item | Cost |
            | :--- | :--- |
            | Coffee | $5 | extra |
            | Tea |
        """.trimIndent()

        val elements = parseMarkdownText(markdown)
        val table = elements.find { it is MarkdownElement.Table } as? MarkdownElement.Table
        assertNotNull(table)
        assertEquals(2, table!!.headers.size)
        // Ensure rows are padded or trimmed to match headers size without crashing
        assertEquals(2, table.rows[0].size)
        assertEquals(2, table.rows[1].size)
    }

    @Test
    fun testChatDtoStructures() {
        val req = ChatRequest(
            message = "Show my debts",
            history = listOf(
                ChatMessageDto(role = "user", text = "Hi"),
                ChatMessageDto(role = "model", text = "Hello!")
            )
        )

        assertEquals("Show my debts", req.message)
        assertEquals(2, req.history?.size)

        val res = ChatResponse(
            success = true,
            reply = "| Debt | Due |\n| --- | --- |\n| Loan A | 1000 |",
            source = "gemini"
        )
        assertTrue(res.success)
        assertTrue(res.reply.contains("Loan A"))
        assertEquals("gemini", res.source)
    }

    @Test
    fun testFeedbackDtoAndValidation() {
        val req = FeedbackRequest(
            category = "BUG_REPORT",
            rating = 5,
            subject = "Crash on receipt scan",
            description = "App closes unexpectedly when picking large PDF.",
            appVersion = "v1.1.0",
            deviceModel = "Pixel 7 Pro",
            androidVersion = "Android 14 (API 34)",
            syncId = "fb_sync_123"
        )

        assertEquals("BUG_REPORT", req.category)
        assertEquals(5, req.rating)
        assertEquals("Crash on receipt scan", req.subject)
        assertEquals("fb_sync_123", req.syncId)

        val res = FeedbackResponse(
            success = true,
            feedbackId = 42L,
            isDuplicate = false,
            message = "Thank you!"
        )
        assertTrue(res.success)
        assertEquals(42L, res.feedbackId)
    }

    @Test
    fun testPendingFeedbackEntity() {
        val entity = PendingFeedbackEntity(
            syncId = "test_sync_id_abc",
            category = "FEATURE_REQUEST",
            rating = 4,
            subject = "CSV Export format options",
            description = "Would love to export in QIF format for desktop accounting software.",
            appVersion = "v1.1.0",
            deviceModel = "Samsung Galaxy S23",
            androidVersion = "Android 14",
            createdAt = 1790000000L,
            isSynced = false
        )

        assertEquals("test_sync_id_abc", entity.syncId)
        assertFalse(entity.isSynced)
        assertEquals("FEATURE_REQUEST", entity.category)
    }

    @Test
    fun testLocalFinancialAiAnalyzerProducesMarkdownTables() {
        val expenseReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "Monthly expense breakdown", null
        )
        assertTrue("Local analyzer must produce table for expenses", expenseReply.contains("|"))
        assertTrue("Local analyzer must format table headers", expenseReply.contains("| :--- |"))

        val loansReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "What are my active loans and debts?", null
        )
        assertTrue("Local analyzer must produce table for loans", loansReply.contains("|"))

        val accountsReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "Show my account balances", null
        )
        assertTrue("Local analyzer must produce table for accounts", accountsReply.contains("|"))

        val savingsReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "How can I save more?", null
        )
        assertTrue("Local analyzer must produce table for 50/30/20 budget", savingsReply.contains("|"))

        // Query-Specific Intent Tests:
        val greetingReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "Hello", null
        )
        assertTrue("Greeting must introduce assistant", greetingReply.contains("Yosan AI Financial Assistant"))
        assertTrue("Greeting must suggest what user can ask", greetingReply.contains("What you can ask me"))

        val highestExpenseReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "What is my highest expense?", null
        )
        assertTrue("Highest expense must show top expenses header", highestExpenseReply.contains("Top Expenses") || highestExpenseReply.contains("Largest Expenses"))
        assertTrue("Highest expense must include table", highestExpenseReply.contains("|"))

        val categoryReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "How much did I spend on food?", null
        )
        assertTrue("Category reply must address Food", categoryReply.contains("Food Spending"))

        val recentReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "Show my recent transactions", null
        )
        assertTrue("Recent reply must address Recent Activity", recentReply.contains("Recent Activity") || recentReply.contains("Recent Transactions"))

        val todayReply = com.example.features.ai.LocalFinancialAiAnalyzer.generateResponse(
            "What did I spend today?", null
        )
        assertTrue("Today reply must address Today", todayReply.contains("Today"))
    }

    @Test
    fun testStatementAiModelsAndMapping() {
        val request = com.example.data.remote.StatementAiParseRequest(
            textContent = "01/10/2026 SWIGGY 450.00",
            fileName = "hdfc_statement.pdf"
        )
        assertEquals("hdfc_statement.pdf", request.fileName)
        assertTrue(request.textContent.contains("SWIGGY"))

        val txDto = com.example.data.remote.StatementAiTransactionDto(
            transactionDate = "2026-10-01",
            description = "Swiggy Bangalore",
            debitAmount = 450.0,
            creditAmount = 0.0,
            amount = 450.0,
            transactionType = "EXPENSE",
            balanceAfterTransaction = 12450.0,
            suggestedCategory = "Food & Dining",
            referenceNumber = "UPI123456"
        )

        val response = com.example.data.remote.StatementAiParseResponse(
            success = true,
            transactions = listOf(txDto),
            totalParsed = 1,
            source = "gemini"
        )

        assertTrue(response.success)
        assertEquals(1, response.totalParsed)
        assertEquals("gemini", response.source)
        assertEquals("Food & Dining", response.transactions[0].suggestedCategory)
        assertEquals(450.0, response.transactions[0].amount, 0.001)
    }
}
