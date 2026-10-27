package com.example.features.sync

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.MatchType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExcelTransactionRow(
    val syncId: String,
    val date: String,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val account: String,
    val notes: String,
    val runningBalance: Double?,
    val lastModified: Long
)

data class ExcelCategoryRow(
    val id: Long = 0,
    val name: String,
    val type: CategoryType = CategoryType.EXPENSE,
    val colorHex: String = "#10B981",
    val isSystem: Boolean = false
)

data class ExcelRuleRow(
    val id: Long = 0,
    val keyword: String,
    val categoryName: String,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val matchType: MatchType = MatchType.CONTAINS,
    val priority: Int = 1,
    val isActive: Boolean = true
)

data class ExcelAccountRow(
    val id: Long = 0,
    val bankName: String,
    val accountName: String,
    val accountType: String = "Savings",
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0
)

data class ParsedWorkbookData(
    val transactions: List<ExcelTransactionRow> = emptyList(),
    val categories: List<ExcelCategoryRow> = emptyList(),
    val rules: List<ExcelRuleRow> = emptyList(),
    val accounts: List<ExcelAccountRow> = emptyList()
)

data class SyncConflict(
    val syncId: String,
    val appTransaction: TransactionEntity,
    val excelRow: ExcelTransactionRow
)

data class SyncResult(
    val addedInApp: Int = 0,
    val updatedInApp: Int = 0,
    val deletedInApp: Int = 0,
    val addedInExcel: Int = 0,
    val updatedInExcel: Int = 0,
    val deletedInExcel: Int = 0,
    val categoriesAdded: Int = 0,
    val categoriesUpdated: Int = 0,
    val rulesAdded: Int = 0,
    val rulesUpdated: Int = 0,
    val conflicts: List<SyncConflict> = emptyList(),
    val errors: List<String> = emptyList(),
    val message: String = "",
    val updatedTransactionsForApp: List<TransactionEntity> = emptyList(),
    val newTransactionsForApp: List<TransactionEntity> = emptyList(),
    val deleteSyncIdsFromApp: List<String> = emptyList(),
    val newCategoriesForApp: List<CategoryEntity> = emptyList(),
    val updatedCategoriesForApp: List<CategoryEntity> = emptyList(),
    val newRulesForApp: List<CategorizationRuleEntity> = emptyList(),
    val updatedRulesForApp: List<CategorizationRuleEntity> = emptyList(),
    val allReconciledRowsForExcel: List<ExcelTransactionRow> = emptyList(),
    val allCategoriesForExcel: List<CategoryEntity> = emptyList(),
    val allRulesForExcel: List<CategorizationRuleEntity> = emptyList(),
    val allAccountsForExcel: List<AccountEntity> = emptyList()
)

object ExcelSyncService {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    @Synchronized
    fun formatTimestamp(ts: Long): String {
        return try {
            TIME_FORMAT.format(Date(ts))
        } catch (e: Exception) {
            ts.toString()
        }
    }

    @Synchronized
    fun parseTimestamp(str: String): Long {
        val trimmed = str.trim()
        val num = trimmed.toLongOrNull()
        if (num != null) return num
        return try {
            TIME_FORMAT.parse(trimmed)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                DATE_FORMAT.parse(trimmed)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    @Synchronized
    fun normalizeDate(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return DATE_FORMAT.format(Date())

        val serial = trimmed.toDoubleOrNull()
        if (serial != null && serial > 1000 && serial < 100000) {
            val days = serial.toLong()
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, days.toInt())
            }
            return DATE_FORMAT.format(cal.time)
        }

        val patterns = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "dd-MMM-yyyy",
            "dd MMM yyyy",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.isLenient = false
                val date = sdf.parse(trimmed)
                if (date != null) {
                    return DATE_FORMAT.format(date)
                }
            } catch (_: Exception) {}
        }
        return trimmed
    }

    // ==========================================
    // 1. MULTI-SHEET EXCEL WORKBOOK GENERATOR
    // ==========================================

    fun generateExcelWorkbook(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        rules: List<CategorizationRuleEntity> = emptyList()
    ): ByteArray {
        val accountMap = accounts.associate { it.id to (it.bankName.ifBlank { it.accountName }) }
        val rows = transactions.map { tx ->
            val accountName = accountMap[tx.accountId] ?: "Primary Account"
            val actualAmount = if (tx.amount > 0) tx.amount else if (tx.debitAmount > 0) tx.debitAmount else tx.creditAmount
            ExcelTransactionRow(
                syncId = tx.syncId.ifBlank { tx.id.toString() },
                date = tx.transactionDate,
                description = tx.description,
                amount = actualAmount,
                type = tx.transactionType,
                category = tx.categoryName,
                account = accountName,
                notes = tx.notes,
                runningBalance = tx.balanceAfterTransaction,
                lastModified = tx.updatedAt
            )
        }
        return writeMultiSheetWorkbook(rows, categories, rules, accounts)
    }

    fun writeRowsToXlsxBytes(
        rows: List<ExcelTransactionRow>,
        categories: List<CategoryEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        rules: List<CategorizationRuleEntity> = emptyList()
    ): ByteArray {
        return writeMultiSheetWorkbook(rows, categories, rules, accounts)
    }

    fun writeMultiSheetWorkbook(
        transactions: List<ExcelTransactionRow>,
        categories: List<CategoryEntity>,
        rules: List<CategorizationRuleEntity>,
        accounts: List<AccountEntity>
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val zos = ZipOutputStream(bos)

        val catList = if (categories.isNotEmpty()) categories else listOf(
            CategoryEntity(name = "Food & Dining", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Groceries", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Shopping", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Bills & Utilities", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Salary", type = CategoryType.INCOME),
            CategoryEntity(name = "Investment", type = CategoryType.INVESTMENT),
            CategoryEntity(name = "Transportation", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Healthcare", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Rent", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Miscellaneous", type = CategoryType.EXPENSE),
            CategoryEntity(name = "Uncategorized", type = CategoryType.EXPENSE)
        )

        val accList = if (accounts.isNotEmpty()) accounts else listOf(
            AccountEntity(id = 1, bankName = "HDFC Bank", accountName = "Primary Savings", accountNumberMasked = "XX1234"),
            AccountEntity(id = 2, bankName = "Cash / UPI Wallet", accountName = "Daily Cash", accountNumberMasked = "CASH")
        )

        val catMaxRow = maxOf(catList.size + 1, 100)
        val accMaxRow = maxOf(accList.size + 1, 50)
        val maxTxRow = maxOf(transactions.size + 1, 5000)
        val maxRuleRow = maxOf(rules.size + 1, 500)

        // 1. [Content_Types].xml
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet5.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 2. _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 3. xl/_rels/workbook.xml.rels
        zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet5.xml"/>
  <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 4. xl/workbook.xml
        zos.putNextEntry(ZipEntry("xl/workbook.xml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Transactions" sheetId="1" r:id="rId1"/>
    <sheet name="Categories" sheetId="2" r:id="rId2"/>
    <sheet name="Rules" sheetId="3" r:id="rId3"/>
    <sheet name="Accounts" sheetId="4" r:id="rId4"/>
    <sheet name="Guide &amp; OneDrive Tips" sheetId="5" r:id="rId5"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 5. xl/styles.xml
        zos.putNextEntry(ZipEntry("xl/styles.xml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="4">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
    <font><i/><sz val="10"/><color rgb="FF64748B"/><name val="Calibri"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF10B981"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF3B82F6"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFE2E8F0"/></left>
      <right style="thin"><color rgb="FFE2E8F0"/></right>
      <top style="thin"><color rgb="FFE2E8F0"/></top>
      <bottom style="thin"><color rgb="FFE2E8F0"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="1" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 6. Sheet 1: Transactions
        zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        val sheet1Sb = StringBuilder()
        sheet1Sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet1Sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet1Sb.append("""<cols>""")
        sheet1Sb.append("""<col min="1" max="1" width="38" customWidth="1"/>""") // Sync ID
        sheet1Sb.append("""<col min="2" max="2" width="14" customWidth="1"/>""") // Date
        sheet1Sb.append("""<col min="3" max="3" width="34" customWidth="1"/>""") // Description
        sheet1Sb.append("""<col min="4" max="4" width="16" customWidth="1"/>""") // Amount
        sheet1Sb.append("""<col min="5" max="5" width="18" customWidth="1"/>""") // Type (Dropdown)
        sheet1Sb.append("""<col min="6" max="6" width="24" customWidth="1"/>""") // Category (Dropdown)
        sheet1Sb.append("""<col min="7" max="7" width="24" customWidth="1"/>""") // Account (Dropdown)
        sheet1Sb.append("""<col min="8" max="8" width="28" customWidth="1"/>""") // Notes
        sheet1Sb.append("""<col min="9" max="9" width="18" customWidth="1"/>""") // Running Balance
        sheet1Sb.append("""<col min="10" max="10" width="22" customWidth="1"/>""") // Last Modified
        sheet1Sb.append("""</cols>""")

        sheet1Sb.append("""<sheetData>""")
        val txHeaders = listOf(
            "Transaction ID", "Date", "Description", "Amount", "Type",
            "Category", "Account", "Notes", "Running Balance", "Last Modified"
        )
        sheet1Sb.append("""<row r="1">""")
        txHeaders.forEachIndexed { colIdx, header ->
            sheet1Sb.append("""<c r="${getColumnLetter(colIdx)}1" t="inlineStr" s="1"><is><t>${escapeXml(header)}</t></is></c>""")
        }
        sheet1Sb.append("""</row>""")

        transactions.forEachIndexed { rIdx, row ->
            val rNum = rIdx + 2
            sheet1Sb.append("""<row r="$rNum">""")
            sheet1Sb.append("""<c r="A$rNum" t="inlineStr" s="0"><is><t>${escapeXml(row.syncId)}</t></is></c>""")
            sheet1Sb.append("""<c r="B$rNum" t="inlineStr" s="0"><is><t>${escapeXml(row.date)}</t></is></c>""")
            sheet1Sb.append("""<c r="C$rNum" t="inlineStr" s="0"><is><t>${escapeXml(row.description)}</t></is></c>""")
            sheet1Sb.append("""<c r="D$rNum" s="0"><v>${row.amount}</v></c>""")
            sheet1Sb.append("""<c r="E$rNum" t="inlineStr" s="0"><is><t>${escapeXml(row.type.name)}</t></is></c>""")
            sheet1Sb.append("""<c r="F$rNum" t="inlineStr" s="0"><is><t>${escapeXml(row.category)}</t></is></c>""")
            sheet1Sb.append("""<c r="G$rNum" t="inlineStr" s="0"><is><t>${escapeXml(row.account)}</t></is></c>""")
            sheet1Sb.append("""<c r="H$rNum" t="inlineStr" s="0"><is><t>${escapeXml(row.notes)}</t></is></c>""")
            if (row.runningBalance != null) {
                sheet1Sb.append("""<c r="I$rNum" s="0"><v>${row.runningBalance}</v></c>""")
            } else {
                sheet1Sb.append("""<c r="I$rNum" s="0"/>""")
            }
            sheet1Sb.append("""<c r="J$rNum" t="inlineStr" s="0"><is><t>${escapeXml(formatTimestamp(row.lastModified))}</t></is></c>""")
            sheet1Sb.append("""</row>""")
        }
        sheet1Sb.append("""</sheetData>""")

        // Data Validations
        sheet1Sb.append("""<dataValidations count="3">""")
        sheet1Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="E2:E$maxTxRow">""")
        sheet1Sb.append("""<formula1>&quot;EXPENSE,INCOME,TRANSFER,INVESTMENT,REFUND,LENDING,BORROWING&quot;</formula1>""")
        sheet1Sb.append("""</dataValidation>""")

        sheet1Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="F2:F$maxTxRow">""")
        sheet1Sb.append("""<formula1>Categories!${'$'}B${'$'}2:${'$'}B${'$'}$catMaxRow</formula1>""")
        sheet1Sb.append("""</dataValidation>""")

        sheet1Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="G2:G$maxTxRow">""")
        sheet1Sb.append("""<formula1>Accounts!${'$'}B${'$'}2:${'$'}B${'$'}$accMaxRow</formula1>""")
        sheet1Sb.append("""</dataValidation>""")
        sheet1Sb.append("""</dataValidations>""")
        sheet1Sb.append("""</worksheet>""")

        zos.write(sheet1Sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 7. Sheet 2: Categories
        zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
        val sheet2Sb = StringBuilder()
        sheet2Sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet2Sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet2Sb.append("""<cols>""")
        sheet2Sb.append("""<col min="1" max="1" width="16" customWidth="1"/>""") // Category ID
        sheet2Sb.append("""<col min="2" max="2" width="28" customWidth="1"/>""") // Category Name
        sheet2Sb.append("""<col min="3" max="3" width="20" customWidth="1"/>""") // Type
        sheet2Sb.append("""<col min="4" max="4" width="16" customWidth="1"/>""") // Color Hex
        sheet2Sb.append("""<col min="5" max="5" width="14" customWidth="1"/>""") // Is System
        sheet2Sb.append("""</cols>""")

        sheet2Sb.append("""<sheetData>""")
        val catHeaders = listOf("Category ID", "Category Name", "Type", "Color Hex", "Is System")
        sheet2Sb.append("""<row r="1">""")
        catHeaders.forEachIndexed { colIdx, header ->
            sheet2Sb.append("""<c r="${getColumnLetter(colIdx)}1" t="inlineStr" s="1"><is><t>${escapeXml(header)}</t></is></c>""")
        }
        sheet2Sb.append("""</row>""")

        catList.forEachIndexed { rIdx, cat ->
            val rNum = rIdx + 2
            sheet2Sb.append("""<row r="$rNum">""")
            sheet2Sb.append("""<c r="A$rNum" s="0"><v>${cat.id}</v></c>""")
            sheet2Sb.append("""<c r="B$rNum" t="inlineStr" s="0"><is><t>${escapeXml(cat.name)}</t></is></c>""")
            sheet2Sb.append("""<c r="C$rNum" t="inlineStr" s="0"><is><t>${escapeXml(cat.type.name)}</t></is></c>""")
            sheet2Sb.append("""<c r="D$rNum" t="inlineStr" s="0"><is><t>${escapeXml(cat.colorHex)}</t></is></c>""")
            sheet2Sb.append("""<c r="E$rNum" t="inlineStr" s="0"><is><t>${cat.isSystem.toString().uppercase()}</t></is></c>""")
            sheet2Sb.append("""</row>""")
        }
        sheet2Sb.append("""</sheetData>""")

        sheet2Sb.append("""<dataValidations count="1">""")
        sheet2Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="C2:C$catMaxRow">""")
        sheet2Sb.append("""<formula1>&quot;EXPENSE,INCOME,INVESTMENT,LENDING,BORROWING,OTHER&quot;</formula1>""")
        sheet2Sb.append("""</dataValidation>""")
        sheet2Sb.append("""</dataValidations>""")
        sheet2Sb.append("""</worksheet>""")

        zos.write(sheet2Sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 8. Sheet 3: Rules
        zos.putNextEntry(ZipEntry("xl/worksheets/sheet3.xml"))
        val sheet3Sb = StringBuilder()
        sheet3Sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet3Sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet3Sb.append("""<cols>""")
        sheet3Sb.append("""<col min="1" max="1" width="14" customWidth="1"/>""") // Rule ID
        sheet3Sb.append("""<col min="2" max="2" width="28" customWidth="1"/>""") // Keyword
        sheet3Sb.append("""<col min="3" max="3" width="24" customWidth="1"/>""") // Category Name
        sheet3Sb.append("""<col min="4" max="4" width="18" customWidth="1"/>""") // Tx Type
        sheet3Sb.append("""<col min="5" max="5" width="18" customWidth="1"/>""") // Match Type
        sheet3Sb.append("""<col min="6" max="6" width="12" customWidth="1"/>""") // Priority
        sheet3Sb.append("""<col min="7" max="7" width="12" customWidth="1"/>""") // Is Active
        sheet3Sb.append("""</cols>""")

        sheet3Sb.append("""<sheetData>""")
        val ruleHeaders = listOf("Rule ID", "Keyword / Pattern", "Category Name", "Transaction Type", "Match Type", "Priority", "Is Active")
        sheet3Sb.append("""<row r="1">""")
        ruleHeaders.forEachIndexed { colIdx, header ->
            sheet3Sb.append("""<c r="${getColumnLetter(colIdx)}1" t="inlineStr" s="1"><is><t>${escapeXml(header)}</t></is></c>""")
        }
        sheet3Sb.append("""</row>""")

        val defaultRules = if (rules.isNotEmpty()) rules else listOf(
            CategorizationRuleEntity(id = 1, keyword = "SWIGGY", categoryId = 1, categoryName = "Food & Dining", transactionType = TransactionType.EXPENSE, matchType = MatchType.CONTAINS, priority = 1, isActive = true),
            CategorizationRuleEntity(id = 2, keyword = "ZOMATO", categoryId = 1, categoryName = "Food & Dining", transactionType = TransactionType.EXPENSE, matchType = MatchType.CONTAINS, priority = 1, isActive = true),
            CategorizationRuleEntity(id = 3, keyword = "SALARY", categoryId = 5, categoryName = "Salary", transactionType = TransactionType.INCOME, matchType = MatchType.CONTAINS, priority = 2, isActive = true),
            CategorizationRuleEntity(id = 4, keyword = "AMAZON", categoryId = 3, categoryName = "Shopping", transactionType = TransactionType.EXPENSE, matchType = MatchType.CONTAINS, priority = 1, isActive = true)
        )

        defaultRules.forEachIndexed { rIdx, rule ->
            val rNum = rIdx + 2
            sheet3Sb.append("""<row r="$rNum">""")
            sheet3Sb.append("""<c r="A$rNum" s="0"><v>${rule.id}</v></c>""")
            sheet3Sb.append("""<c r="B$rNum" t="inlineStr" s="0"><is><t>${escapeXml(rule.keyword)}</t></is></c>""")
            sheet3Sb.append("""<c r="C$rNum" t="inlineStr" s="0"><is><t>${escapeXml(rule.categoryName)}</t></is></c>""")
            sheet3Sb.append("""<c r="D$rNum" t="inlineStr" s="0"><is><t>${escapeXml(rule.transactionType.name)}</t></is></c>""")
            sheet3Sb.append("""<c r="E$rNum" t="inlineStr" s="0"><is><t>${escapeXml(rule.matchType.name)}</t></is></c>""")
            sheet3Sb.append("""<c r="F$rNum" s="0"><v>${rule.priority}</v></c>""")
            sheet3Sb.append("""<c r="G$rNum" t="inlineStr" s="0"><is><t>${rule.isActive.toString().uppercase()}</t></is></c>""")
            sheet3Sb.append("""</row>""")
        }
        sheet3Sb.append("""</sheetData>""")

        sheet3Sb.append("""<dataValidations count="3">""")
        sheet3Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="C2:C$maxRuleRow">""")
        sheet3Sb.append("""<formula1>Categories!${'$'}B${'$'}2:${'$'}B${'$'}$catMaxRow</formula1>""")
        sheet3Sb.append("""</dataValidation>""")

        sheet3Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="D2:D$maxRuleRow">""")
        sheet3Sb.append("""<formula1>&quot;EXPENSE,INCOME,TRANSFER,INVESTMENT,REFUND&quot;</formula1>""")
        sheet3Sb.append("""</dataValidation>""")

        sheet3Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="E2:E$maxRuleRow">""")
        sheet3Sb.append("""<formula1>&quot;CONTAINS,EXACT,STARTS_WITH,ENDS_WITH,REGEX&quot;</formula1>""")
        sheet3Sb.append("""</dataValidation>""")
        sheet3Sb.append("""</dataValidations>""")
        sheet3Sb.append("""</worksheet>""")

        zos.write(sheet3Sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 9. Sheet 4: Accounts
        zos.putNextEntry(ZipEntry("xl/worksheets/sheet4.xml"))
        val sheet4Sb = StringBuilder()
        sheet4Sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet4Sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet4Sb.append("""<cols>""")
        sheet4Sb.append("""<col min="1" max="1" width="14" customWidth="1"/>""") // Account ID
        sheet4Sb.append("""<col min="2" max="2" width="24" customWidth="1"/>""") // Bank Name
        sheet4Sb.append("""<col min="3" max="3" width="24" customWidth="1"/>""") // Account Name
        sheet4Sb.append("""<col min="4" max="4" width="18" customWidth="1"/>""") // Account Type
        sheet4Sb.append("""<col min="5" max="5" width="18" customWidth="1"/>""") // Opening Balance
        sheet4Sb.append("""<col min="6" max="6" width="18" customWidth="1"/>""") // Current Balance
        sheet4Sb.append("""</cols>""")

        sheet4Sb.append("""<sheetData>""")
        val accHeaders = listOf("Account ID", "Bank Name", "Account Name", "Account Type", "Opening Balance", "Current Balance")
        sheet4Sb.append("""<row r="1">""")
        accHeaders.forEachIndexed { colIdx, header ->
            sheet4Sb.append("""<c r="${getColumnLetter(colIdx)}1" t="inlineStr" s="1"><is><t>${escapeXml(header)}</t></is></c>""")
        }
        sheet4Sb.append("""</row>""")

        accList.forEachIndexed { rIdx, acc ->
            val rNum = rIdx + 2
            sheet4Sb.append("""<row r="$rNum">""")
            sheet4Sb.append("""<c r="A$rNum" s="0"><v>${acc.id}</v></c>""")
            sheet4Sb.append("""<c r="B$rNum" t="inlineStr" s="0"><is><t>${escapeXml(acc.bankName.ifBlank { acc.accountName })}</t></is></c>""")
            sheet4Sb.append("""<c r="C$rNum" t="inlineStr" s="0"><is><t>${escapeXml(acc.accountName)}</t></is></c>""")
            sheet4Sb.append("""<c r="D$rNum" t="inlineStr" s="0"><is><t>${escapeXml(acc.accountType.name)}</t></is></c>""")
            sheet4Sb.append("""<c r="E$rNum" s="0"><v>${acc.openingBalance}</v></c>""")
            sheet4Sb.append("""<c r="F$rNum" s="0"><v>${acc.currentBalance}</v></c>""")
            sheet4Sb.append("""</row>""")
        }
        sheet4Sb.append("""</sheetData>""")

        sheet4Sb.append("""<dataValidations count="1">""")
        sheet4Sb.append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="D2:D$accMaxRow">""")
        sheet4Sb.append("""<formula1>&quot;SAVINGS,CURRENT,CREDIT_CARD,WALLET,CASH,INVESTMENT&quot;</formula1>""")
        sheet4Sb.append("""</dataValidation>""")
        sheet4Sb.append("""</dataValidations>""")
        sheet4Sb.append("""</worksheet>""")

        zos.write(sheet4Sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 10. Sheet 5: Guide & OneDrive Tips
        zos.putNextEntry(ZipEntry("xl/worksheets/sheet5.xml"))
        val sheet5Sb = StringBuilder()
        sheet5Sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet5Sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet5Sb.append("""<cols>""")
        sheet5Sb.append("""<col min="1" max="1" width="30" customWidth="1"/>""")
        sheet5Sb.append("""<col min="2" max="2" width="70" customWidth="1"/>""")
        sheet5Sb.append("""</cols>""")

        sheet5Sb.append("""<sheetData>""")
        sheet5Sb.append("""<row r="1">""")
        sheet5Sb.append("""<c r="A1" t="inlineStr" s="2"><is><t>Topic / Feature</t></is></c>""")
        sheet5Sb.append("""<c r="B1" t="inlineStr" s="2"><is><t>How to Use &amp; Sync with Yosan App</t></is></c>""")
        sheet5Sb.append("""</row>""")

        val guideRows = listOf(
            "OneDrive / Cloud Backup" to "Save this file to your OneDrive or Google Drive folder. You can open and edit it anytime from your laptop or PC.",
            "Categories Sheet" to "Add new categories or edit colors/types in the 'Categories' sheet. Newly added categories immediately become available in the Category dropdown in the Transactions sheet.",
            "Rules Sheet" to "Add automated categorization keywords in the 'Rules' sheet. Set keyword, category, and priority (1-10).",
            "Transactions Sheet" to "Edit categories, amounts, descriptions, and types freely. Category and Account columns have native dropdown menus.",
            "Syncing with Yosan App" to "When you open the Yosan mobile app, simply tap 'Sync with Excel'. All changes from your laptop sync into the app database instantly.",
            "Adding New Transactions" to "To add new transactions on your laptop, add a new row at the bottom of the 'Transactions' sheet. You can leave 'Transaction ID' blank and the app will generate one automatically."
        )

        guideRows.forEachIndexed { idx, (topic, desc) ->
            val rNum = idx + 2
            sheet5Sb.append("""<row r="$rNum">""")
            sheet5Sb.append("""<c r="A$rNum" t="inlineStr" s="3"><is><t>${escapeXml(topic)}</t></is></c>""")
            sheet5Sb.append("""<c r="B$rNum" t="inlineStr" s="0"><is><t>${escapeXml(desc)}</t></is></c>""")
            sheet5Sb.append("""</row>""")
        }

        sheet5Sb.append("""</sheetData>""")
        sheet5Sb.append("""</worksheet>""")

        zos.write(sheet5Sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.finish()
        return bos.toByteArray()
    }

    // ==========================================
    // 2. MULTI-SHEET EXCEL WORKBOOK PARSER (XmlPullParser)
    // ==========================================

    fun parseFullWorkbook(bytes: ByteArray): ParsedWorkbookData {
        val bytesMap = mutableMapOf<String, ByteArray>()

        try {
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace("\\", "/")
                if (name == "xl/sharedStrings.xml" ||
                    name == "xl/workbook.xml" ||
                    name == "xl/_rels/workbook.xml.rels" ||
                    name.startsWith("xl/worksheets/")
                ) {
                    bytesMap[name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
            zip.close()

            // 1. Parse Shared strings
            val sharedStrings = bytesMap["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

            // 2. Map sheet names to worksheet file paths
            val relsMap = bytesMap["xl/_rels/workbook.xml.rels"]?.let { parseWorkbookRels(it) } ?: emptyMap()
            val sheetNameToFileName = bytesMap["xl/workbook.xml"]?.let { parseWorkbookSheets(it, relsMap) } ?: emptyMap()

            // 3. Parse Transactions Sheet
            val txFileName = sheetNameToFileName["transactions"]
                ?: bytesMap.keys.firstOrNull { it.contains("sheet1", ignoreCase = true) }
                ?: bytesMap.keys.firstOrNull { it.startsWith("xl/worksheets/") }

            val txGrid = if (txFileName != null && bytesMap.containsKey(txFileName)) {
                parseWorksheetGrid(bytesMap[txFileName]!!, sharedStrings)
            } else emptyMap()
            val parsedTransactions = parseTransactionsGrid(txGrid)

            // 4. Parse Categories Sheet
            val catFileName = sheetNameToFileName["categories"]
                ?: bytesMap.keys.firstOrNull { it.contains("sheet2", ignoreCase = true) }

            val catGrid = if (catFileName != null && bytesMap.containsKey(catFileName)) {
                parseWorksheetGrid(bytesMap[catFileName]!!, sharedStrings)
            } else emptyMap()
            val parsedCategories = parseCategoriesGrid(catGrid)

            // 5. Parse Rules Sheet
            val ruleFileName = sheetNameToFileName["rules"]
                ?: bytesMap.keys.firstOrNull { it.contains("sheet3", ignoreCase = true) }

            val ruleGrid = if (ruleFileName != null && bytesMap.containsKey(ruleFileName)) {
                parseWorksheetGrid(bytesMap[ruleFileName]!!, sharedStrings)
            } else emptyMap()
            val parsedRules = parseRulesGrid(ruleGrid)

            // 6. Parse Accounts Sheet
            val accFileName = sheetNameToFileName["accounts"]
                ?: bytesMap.keys.firstOrNull { it.contains("sheet4", ignoreCase = true) }

            val accGrid = if (accFileName != null && bytesMap.containsKey(accFileName)) {
                parseWorksheetGrid(bytesMap[accFileName]!!, sharedStrings)
            } else emptyMap()
            val parsedAccounts = parseAccountsGrid(accGrid)

            return ParsedWorkbookData(
                transactions = parsedTransactions,
                categories = parsedCategories,
                rules = parsedRules,
                accounts = parsedAccounts
            )
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("ExcelSyncService", "Failed to parse workbook", e)
            }
            return ParsedWorkbookData()
        }
    }

    fun parseExcelWorkbook(bytes: ByteArray): List<ExcelTransactionRow> {
        return parseFullWorkbook(bytes).transactions
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        try {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            var isInsideSi = false
            var isInsideT = false
            val sb = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "si") {
                            isInsideSi = true
                            sb.setLength(0)
                        } else if (tagName == "t") {
                            isInsideT = true
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> {
                        if (isInsideSi && isInsideT) {
                            sb.append(parser.text ?: "")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "t") {
                            isInsideT = false
                        } else if (tagName == "si") {
                            isInsideSi = false
                            list.add(sb.toString().trim())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseWorkbookRels(xmlBytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && (parser.name?.equals("relationship", ignoreCase = true) == true)) {
                    val id = parser.getAttributeValue(null, "Id") ?: parser.getAttributeValue(null, "id") ?: ""
                    val target = parser.getAttributeValue(null, "Target") ?: parser.getAttributeValue(null, "target") ?: ""
                    if (id.isNotEmpty() && target.isNotEmpty()) {
                        val cleaned = target.replace("\\", "/").removePrefix("/xl/").removePrefix("xl/")
                        val fullPath = if (cleaned.startsWith("worksheets/")) "xl/$cleaned" else "xl/worksheets/$cleaned"
                        map[id] = fullPath
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return map
    }

    private fun parseWorkbookSheets(xmlBytes: ByteArray, rels: Map<String, String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && (parser.name?.equals("sheet", ignoreCase = true) == true)) {
                    val name = parser.getAttributeValue(null, "name")?.trim()?.lowercase() ?: ""
                    var rId: String? = null
                    for (i in 0 until parser.attributeCount) {
                        val attrName = parser.getAttributeName(i).lowercase()
                        if (attrName == "id" || attrName.endsWith(":id") || attrName == "r:id") {
                            rId = parser.getAttributeValue(i)
                            break
                        }
                    }
                    if (name.isNotEmpty() && !rId.isNullOrEmpty()) {
                        val filePath = rels[rId] ?: "xl/worksheets/${rId.lowercase()}.xml"
                        map[name] = filePath
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return map
    }

    private fun parseWorksheetGrid(xmlBytes: ByteArray, sharedStrings: List<String>): Map<Int, Map<Int, String>> {
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()
        try {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            var currentRow = 0
            var currentCol = 0
            var cellType: String = ""
            var isInsideCell = false
            var isInsideValue = false
            var isInsideText = false
            var isInsideInlineStr = false
            val cellTextBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "row" -> {
                                var rAttr: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    val aName = parser.getAttributeName(i).lowercase()
                                    if (aName == "r" || aName.endsWith(":r")) {
                                        rAttr = parser.getAttributeValue(i)
                                        break
                                    }
                                }
                                currentRow = rAttr?.toIntOrNull() ?: (currentRow + 1)
                                currentCol = 0
                            }
                            "c" -> {
                                isInsideCell = true
                                cellType = ""
                                var cellRef: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    val aName = parser.getAttributeName(i).lowercase()
                                    if (aName == "t" || aName.endsWith(":t")) {
                                        cellType = parser.getAttributeValue(i).lowercase()
                                    } else if (aName == "r" || aName.endsWith(":r")) {
                                        cellRef = parser.getAttributeValue(i)
                                    }
                                }
                                cellTextBuilder.setLength(0)
                                if (cellRef != null) {
                                    val colLetters = cellRef.filter { it.isLetter() }.uppercase()
                                    currentCol = columnLetterToIndex(colLetters)
                                }
                            }
                            "v" -> isInsideValue = true
                            "t" -> isInsideText = true
                            "is" -> isInsideInlineStr = true
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> {
                        if (isInsideCell) {
                            val text = parser.text ?: ""
                            if (isInsideValue || isInsideText || isInsideInlineStr) {
                                cellTextBuilder.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "v" -> isInsideValue = false
                            "t" -> isInsideText = false
                            "is" -> isInsideInlineStr = false
                            "c" -> {
                                if (isInsideCell && currentRow > 0) {
                                    var finalVal = cellTextBuilder.toString().trim()
                                    if (cellType == "s" && finalVal.isNotEmpty()) {
                                        val sIdx = finalVal.toIntOrNull()
                                        if (sIdx != null && sIdx in sharedStrings.indices) {
                                            finalVal = sharedStrings[sIdx]
                                        }
                                    }
                                    grid.getOrPut(currentRow) { mutableMapOf() }[currentCol] = finalVal
                                    currentCol++
                                }
                                isInsideCell = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return grid
    }

    private fun parseTransactionsGrid(rawGrid: Map<Int, Map<Int, String>>): List<ExcelTransactionRow> {
        if (rawGrid.isEmpty()) return emptyList()
        val headerRow = rawGrid[1] ?: rawGrid.values.firstOrNull() ?: return emptyList()

        var syncIdCol = 0
        var dateCol = 1
        var descCol = 2
        var amountCol = 3
        var debitCol = -1
        var creditCol = -1
        var typeCol = 4
        var categoryCol = 5
        var accountCol = 6
        var notesCol = 7
        var balanceCol = 8
        var lastModifiedCol = 9

        headerRow.forEach { (colIdx, name) ->
            val lower = name.lowercase().replace("_", " ").replace("-", " ")
            when {
                lower.contains("transaction id") || lower.contains("sync id") || lower == "id" -> syncIdCol = colIdx
                lower.contains("date") -> dateCol = colIdx
                lower.contains("description") || lower.contains("narration") || lower.contains("details") || lower.contains("particulars") -> descCol = colIdx
                lower.contains("debit") || lower.contains("withdrawal") -> debitCol = colIdx
                lower.contains("credit") || lower.contains("deposit") -> creditCol = colIdx
                lower.contains("amount") -> amountCol = colIdx
                lower.contains("type") -> typeCol = colIdx
                lower.contains("category") -> categoryCol = colIdx
                lower.contains("account") || lower.contains("bank") -> accountCol = colIdx
                lower.contains("notes") || lower.contains("note") || lower.contains("remarks") -> notesCol = colIdx
                lower.contains("balance") -> balanceCol = colIdx
                lower.contains("last modified") || lower.contains("modified") || lower.contains("updated") -> lastModifiedCol = colIdx
            }
        }

        val parsedRows = mutableListOf<ExcelTransactionRow>()
        val dataRowKeys = rawGrid.keys.filter { it > 1 }.sorted()

        for (rNum in dataRowKeys) {
            val rowCells = rawGrid[rNum] ?: continue
            val desc = rowCells[descCol] ?: ""
            val rawAmt = rowCells[amountCol]?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            val debitAmt = if (debitCol >= 0) rowCells[debitCol]?.replace(",", "")?.toDoubleOrNull() ?: 0.0 else 0.0
            val creditAmt = if (creditCol >= 0) rowCells[creditCol]?.replace(",", "")?.toDoubleOrNull() ?: 0.0 else 0.0

            val effectiveAmount = if (rawAmt > 0) rawAmt else if (debitAmt > 0) debitAmt else creditAmt
            if (desc.isBlank() && effectiveAmount == 0.0) continue

            var syncId = rowCells[syncIdCol] ?: ""
            if (syncId.isBlank()) {
                syncId = UUID.randomUUID().toString()
            }

            val rawDate = rowCells[dateCol] ?: ""
            val dateStr = normalizeDate(rawDate)

            var typeStr = (rowCells[typeCol] ?: "").uppercase().trim()
            if (typeStr.isBlank()) {
                typeStr = if (creditAmt > 0) "INCOME" else "EXPENSE"
            }

            val type = try {
                TransactionType.valueOf(typeStr)
            } catch (e: Exception) {
                if (typeStr.contains("INC") || typeStr.contains("CR") || creditAmt > 0) TransactionType.INCOME else TransactionType.EXPENSE
            }

            val category = rowCells[categoryCol]?.trim()?.ifBlank { "Uncategorized" } ?: "Uncategorized"
            val account = rowCells[accountCol]?.trim()?.ifBlank { "Primary Account" } ?: "Primary Account"
            val notes = rowCells[notesCol] ?: ""
            val balanceVal = rowCells[balanceCol]?.replace(",", "")?.toDoubleOrNull()
            val lastModifiedVal = parseTimestamp(rowCells[lastModifiedCol] ?: "")

            parsedRows.add(
                ExcelTransactionRow(
                    syncId = syncId,
                    date = dateStr,
                    description = desc.ifBlank { "Transaction" },
                    amount = effectiveAmount,
                    type = type,
                    category = category,
                    account = account,
                    notes = notes,
                    runningBalance = balanceVal,
                    lastModified = lastModifiedVal
                )
            )
        }
        return parsedRows
    }

    private fun parseCategoriesGrid(rawGrid: Map<Int, Map<Int, String>>): List<ExcelCategoryRow> {
        if (rawGrid.isEmpty()) return emptyList()
        val dataRowKeys = rawGrid.keys.filter { it > 1 }.sorted()
        val list = mutableListOf<ExcelCategoryRow>()

        for (rNum in dataRowKeys) {
            val row = rawGrid[rNum] ?: continue
            val id = row[0]?.toLongOrNull() ?: 0L
            val name = row[1]?.trim() ?: ""
            if (name.isBlank()) continue

            val typeStr = (row[2] ?: "EXPENSE").uppercase().trim()
            val type = try {
                CategoryType.valueOf(typeStr)
            } catch (e: Exception) {
                if (typeStr.contains("INC")) CategoryType.INCOME else CategoryType.EXPENSE
            }
            val color = row[3]?.trim()?.ifBlank { "#10B981" } ?: "#10B981"
            val isSystem = row[4]?.trim()?.equals("TRUE", ignoreCase = true) ?: false

            list.add(ExcelCategoryRow(id = id, name = name, type = type, colorHex = color, isSystem = isSystem))
        }
        return list
    }

    private fun parseRulesGrid(rawGrid: Map<Int, Map<Int, String>>): List<ExcelRuleRow> {
        if (rawGrid.isEmpty()) return emptyList()
        val dataRowKeys = rawGrid.keys.filter { it > 1 }.sorted()
        val list = mutableListOf<ExcelRuleRow>()

        for (rNum in dataRowKeys) {
            val row = rawGrid[rNum] ?: continue
            val id = row[0]?.toLongOrNull() ?: 0L
            val keyword = row[1]?.trim() ?: ""
            val catName = row[2]?.trim() ?: ""
            if (keyword.isBlank() || catName.isBlank()) continue

            val typeStr = (row[3] ?: "EXPENSE").uppercase().trim()
            val type = try {
                TransactionType.valueOf(typeStr)
            } catch (e: Exception) {
                TransactionType.EXPENSE
            }
            val matchStr = (row[4] ?: "CONTAINS").uppercase().trim()
            val matchType = try {
                MatchType.valueOf(matchStr)
            } catch (e: Exception) {
                MatchType.CONTAINS
            }
            val priority = row[5]?.trim()?.toIntOrNull() ?: 1
            val isActive = row[6]?.trim()?.equals("FALSE", ignoreCase = true)?.not() ?: true

            list.add(ExcelRuleRow(id = id, keyword = keyword, categoryName = catName, transactionType = type, matchType = matchType, priority = priority, isActive = isActive))
        }
        return list
    }

    private fun parseAccountsGrid(rawGrid: Map<Int, Map<Int, String>>): List<ExcelAccountRow> {
        if (rawGrid.isEmpty()) return emptyList()
        val dataRowKeys = rawGrid.keys.filter { it > 1 }.sorted()
        val list = mutableListOf<ExcelAccountRow>()

        for (rNum in dataRowKeys) {
            val row = rawGrid[rNum] ?: continue
            val id = row[0]?.toLongOrNull() ?: 0L
            val bankName = row[1]?.trim() ?: ""
            val accName = row[2]?.trim() ?: bankName
            if (bankName.isBlank() && accName.isBlank()) continue

            val accType = row[3]?.trim()?.ifBlank { "Savings" } ?: "Savings"
            val opening = row[4]?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            val current = row[5]?.replace(",", "")?.toDoubleOrNull() ?: opening

            list.add(ExcelAccountRow(id = id, bankName = bankName, accountName = accName, accountType = accType, openingBalance = opening, currentBalance = current))
        }
        return list
    }

    // ==========================================
    // 3. MULTI-ENTITY RECONCILIATION ENGINE
    // ==========================================

    fun reconcile(
        appTransactions: List<TransactionEntity>,
        excelWorkbookData: ParsedWorkbookData,
        deletedSyncIds: List<String>,
        lastSyncTimestamp: Long,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        rules: List<CategorizationRuleEntity> = emptyList()
    ): SyncResult {
        val excelRows = excelWorkbookData.transactions
        val excelCategories = excelWorkbookData.categories
        val excelRules = excelWorkbookData.rules

        // 1. Reconcile Categories from Excel -> App
        val appCatMap = categories.associateBy { it.name.trim().lowercase() }
        val newCategoriesForApp = mutableListOf<CategoryEntity>()
        val updatedCategoriesForApp = mutableListOf<CategoryEntity>()

        for (ec in excelCategories) {
            val key = ec.name.trim().lowercase()
            val existing = appCatMap[key]
            if (existing == null && key.isNotBlank()) {
                newCategoriesForApp.add(
                    CategoryEntity(
                        name = ec.name.trim(),
                        type = ec.type,
                        colorHex = ec.colorHex,
                        isSystem = ec.isSystem
                    )
                )
            }
        }

        // Also auto-detect categories typed directly in the Transactions sheet
        for (row in excelRows) {
            val catName = row.category.trim()
            val catKey = catName.lowercase()
            if (catName.isNotBlank() && !appCatMap.containsKey(catKey) && !newCategoriesForApp.any { it.name.equals(catName, ignoreCase = true) }) {
                newCategoriesForApp.add(
                    CategoryEntity(
                        name = catName,
                        type = if (row.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE,
                        colorHex = "#10B981"
                    )
                )
            }
        }

        val allCategoriesCombined = (categories + newCategoriesForApp).associateBy { it.name.trim().lowercase() }

        // 2. Reconcile Rules from Excel -> App
        val appRuleMap = rules.associateBy { it.keyword.trim().lowercase() }
        val newRulesForApp = mutableListOf<CategorizationRuleEntity>()
        val updatedRulesForApp = mutableListOf<CategorizationRuleEntity>()

        for (er in excelRules) {
            val key = er.keyword.trim().lowercase()
            val existing = appRuleMap[key]
            val matchedCat = allCategoriesCombined[er.categoryName.trim().lowercase()]
            val catId = matchedCat?.id ?: 1L

            if (existing == null) {
                newRulesForApp.add(
                    CategorizationRuleEntity(
                        keyword = er.keyword.trim(),
                        categoryId = catId,
                        categoryName = er.categoryName.trim(),
                        transactionType = er.transactionType,
                        matchType = er.matchType,
                        priority = er.priority,
                        isActive = er.isActive
                    )
                )
            } else if (existing.categoryName != er.categoryName ||
                existing.isActive != er.isActive ||
                existing.matchType != er.matchType ||
                existing.priority != er.priority ||
                existing.transactionType != er.transactionType
            ) {
                updatedRulesForApp.add(
                    existing.copy(
                        categoryName = er.categoryName.trim(),
                        categoryId = catId,
                        transactionType = er.transactionType,
                        matchType = er.matchType,
                        priority = er.priority,
                        isActive = er.isActive
                    )
                )
            }
        }

        // 3. Reconcile Transactions
        val appMapBySyncId = appTransactions.associateBy { it.syncId.ifBlank { it.id.toString() } }
        val accountNameToIdMap = accounts.associate { (it.bankName.ifBlank { it.accountName }).trim().lowercase() to it.id }
        val defaultAccountId = accounts.firstOrNull()?.id ?: 1L

        val updatedForApp = mutableListOf<TransactionEntity>()
        val newForApp = mutableListOf<TransactionEntity>()
        val deleteFromApp = mutableListOf<String>()
        val reconciledRowsForExcel = mutableListOf<ExcelTransactionRow>()

        var addedInApp = 0
        var updatedInApp = 0

        // Process all transactions found in Excel
        for (excelRow in excelRows) {
            val existingAppTx = appMapBySyncId[excelRow.syncId]
                ?: appTransactions.firstOrNull {
                    it.transactionDate == excelRow.date &&
                            it.description.equals(excelRow.description, ignoreCase = true) &&
                            Math.abs(it.amount - excelRow.amount) < 0.01
                }

            val isCredit = excelRow.type == TransactionType.INCOME || excelRow.type == TransactionType.REFUND
            val catId = allCategoriesCombined[excelRow.category.trim().lowercase()]?.id
            val accId = accountNameToIdMap[excelRow.account.trim().lowercase()] ?: defaultAccountId

            if (existingAppTx != null) {
                // Check if Excel has modified values (Category, Description, Amount, Type, Date, Notes, Account)
                val isIdentical = existingAppTx.transactionDate == excelRow.date &&
                        existingAppTx.description == excelRow.description &&
                        Math.abs(existingAppTx.amount - excelRow.amount) < 0.001 &&
                        existingAppTx.transactionType == excelRow.type &&
                        existingAppTx.categoryName.equals(excelRow.category, ignoreCase = true) &&
                        existingAppTx.notes == excelRow.notes

                if (isIdentical) {
                    reconciledRowsForExcel.add(excelRow)
                } else {
                    // Update App DB with Excel's edited values
                    val isCat = !excelRow.category.equals("Uncategorized", ignoreCase = true) && excelRow.category.isNotBlank()
                    val updatedEntity = existingAppTx.copy(
                        transactionDate = excelRow.date,
                        description = excelRow.description,
                        amount = excelRow.amount,
                        debitAmount = if (isCredit) 0.0 else excelRow.amount,
                        creditAmount = if (isCredit) excelRow.amount else 0.0,
                        transactionType = excelRow.type,
                        categoryName = excelRow.category,
                        categoryId = catId ?: existingAppTx.categoryId,
                        accountId = accId,
                        notes = excelRow.notes,
                        balanceAfterTransaction = excelRow.runningBalance ?: existingAppTx.balanceAfterTransaction,
                        isCategorized = if (isCat) true else existingAppTx.isCategorized,
                        categorizationConfidence = if (isCat) 1.0f else existingAppTx.categorizationConfidence,
                        updatedAt = System.currentTimeMillis()
                    )
                    updatedForApp.add(updatedEntity)
                    reconciledRowsForExcel.add(excelRow.copy(lastModified = updatedEntity.updatedAt))
                    updatedInApp++
                }
            } else {
                // New transaction created in Excel
                val isCat = !excelRow.category.equals("Uncategorized", ignoreCase = true) && excelRow.category.isNotBlank()
                val newTx = TransactionEntity(
                    syncId = excelRow.syncId,
                    accountId = accId,
                    transactionDate = excelRow.date,
                    description = excelRow.description,
                    amount = excelRow.amount,
                    debitAmount = if (isCredit) 0.0 else excelRow.amount,
                    creditAmount = if (isCredit) excelRow.amount else 0.0,
                    transactionType = excelRow.type,
                    categoryName = excelRow.category,
                    categoryId = catId,
                    notes = excelRow.notes,
                    balanceAfterTransaction = excelRow.runningBalance,
                    source = "SYNC_EXCEL",
                    isCategorized = isCat,
                    categorizationConfidence = if (isCat) 1.0f else 0.0f,
                    createdAt = if (excelRow.lastModified > 0) excelRow.lastModified else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                newForApp.add(newTx)
                reconciledRowsForExcel.add(excelRow)
                addedInApp++
            }
        }

        // Transactions in App but not in Excel: keep in Excel workbook
        val excelSyncIdSet = excelRows.map { it.syncId }.toSet()
        for (appTx in appTransactions) {
            val syncKey = appTx.syncId.ifBlank { appTx.id.toString() }
            if (!excelSyncIdSet.contains(syncKey)) {
                val accountName = accounts.firstOrNull { it.id == appTx.accountId }?.let { it.bankName.ifBlank { it.accountName } } ?: "Primary Account"
                reconciledRowsForExcel.add(
                    ExcelTransactionRow(
                        syncId = syncKey,
                        date = appTx.transactionDate,
                        description = appTx.description,
                        amount = if (appTx.amount > 0) appTx.amount else maxOf(appTx.debitAmount, appTx.creditAmount),
                        type = appTx.transactionType,
                        category = appTx.categoryName,
                        account = accountName,
                        notes = appTx.notes,
                        runningBalance = appTx.balanceAfterTransaction,
                        lastModified = appTx.updatedAt
                    )
                )
            }
        }

        val message = "Excel Sync Complete: $updatedInApp transactions updated, $addedInApp added, ${newCategoriesForApp.size} categories & ${newRulesForApp.size} rules synced."

        return SyncResult(
            addedInApp = addedInApp,
            updatedInApp = updatedInApp,
            deletedInApp = 0,
            addedInExcel = 0,
            updatedInExcel = 0,
            deletedInExcel = 0,
            categoriesAdded = newCategoriesForApp.size,
            categoriesUpdated = updatedCategoriesForApp.size,
            rulesAdded = newRulesForApp.size,
            rulesUpdated = updatedRulesForApp.size,
            conflicts = emptyList(),
            errors = emptyList(),
            message = message,
            updatedTransactionsForApp = updatedForApp,
            newTransactionsForApp = newForApp,
            deleteSyncIdsFromApp = deleteFromApp,
            newCategoriesForApp = newCategoriesForApp,
            updatedCategoriesForApp = updatedCategoriesForApp,
            newRulesForApp = newRulesForApp,
            updatedRulesForApp = updatedRulesForApp,
            allReconciledRowsForExcel = reconciledRowsForExcel,
            allCategoriesForExcel = categories + newCategoriesForApp,
            allRulesForExcel = rules + newRulesForApp,
            allAccountsForExcel = accounts
        )
    }

    // Helpers
    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(input: String): String {
        return input
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
    }

    private fun columnLetterToIndex(letter: String): Int {
        var index = 0
        for (char in letter) {
            if (char in 'A'..'Z') {
                index = index * 26 + (char - 'A' + 1)
            }
        }
        return index - 1
    }

    private fun getColumnLetter(colIndex: Int): String {
        var temp = colIndex
        var letter = ""
        while (temp >= 0) {
            letter = ('A'.code + (temp % 26)).toChar() + letter
            temp = (temp / 26) - 1
        }
        return letter
    }
}
