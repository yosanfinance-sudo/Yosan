/**
 * AI Bank Statement Parsing Service
 * Integrates with Google Gemini AI (with intelligent local fallback) to parse raw statement text,
 * extracting only clean, necessary transaction details while filtering out disclaimers, branch addresses,
 * taxes, and page header/footer junk.
 */

function maskPii(text) {
  if (!text || typeof text !== 'string') return text;
  return text
    // UPI handles: example@upi -> e•••e@upi
    .replace(/\b([a-zA-Z0-9_\.]{1,2})[a-zA-Z0-9_\.]+(@[a-zA-Z0-9]+)\b/g, '$1••••$2')
    // Indian phone numbers / 10 digits: 9876543210 -> ••••3210
    .replace(/\b(?:\+?91[\-\s]?)?[6-9]\d{5}(\d{4})\b/g, '••••$1')
    // 16-digit card numbers -> ••••1234
    .replace(/\b\d{4}[\-\s]?\d{4}[\-\s]?\d{4}[\-\s]?(\d{4})\b/g, '••••$1')
    // Bank account numbers (8 to 18 digits) -> ••••1234
    .replace(/\b\d{4,14}(\d{4})\b/g, '••••$1');
}

/**
 * Filter out prominent non-transaction bank header/footer junk before sending to AI
 */
function cleanAndFilterRawStatementText(rawText) {
  if (!rawText) return '';
  const lines = rawText.split(/\r?\n/);
  const junkPatterns = [
    /statement\s+of\s+account/i,
    /account\s+summary/i,
    /registered\s+office/i,
    /branch\s+(address|code|name|ifsc)/i,
    /ifsc\s*code\s*:/i,
    /micr\s*code\s*:/i,
    /nomination\s+registered/i,
    /cheques?\s+are\s+credited\s+subject\s+to\s+realisation/i,
    /this\s+is\s+a\s+computer\s+generated\s+statement/i,
    /gstin\s*:/i,
    /cin\s*:/i,
    /page\s+\d+\s+of\s+\d+/i,
    /interest\s+rate\s+w\.e\.f/i,
    /toll[\s\-]?free\s+number/i,
    /charges\s+are\s+inclusive\s+of\s+gst/i
  ];

  const cleaned = lines.filter(line => {
    const trimmed = line.trim();
    if (!trimmed) return false;
    // Keep lines that have dates or numbers even if some keyword matches
    const hasDate = /\b\d{1,2}[\/\-\.](?:\d{1,2}|[a-zA-Z]{3})[\/\-\.]\d{2,4}\b/.test(trimmed);
    if (hasDate) return true;
    return !junkPatterns.some(pattern => pattern.test(trimmed));
  });

  return cleaned.join('\n');
}

/**
 * Intelligent Local Rule-Based Statement Cleaner & Parser
 * Operates offline or as a rock-solid fallback when Gemini API is unavailable.
 */
function localHeuristicStatementParser(text, fileName = '') {
  if (!text || typeof text !== 'string') return [];

  const lines = text.split(/\r?\n/);
  const transactions = [];

  const dateRegex = /\b(\d{1,2}[\/\-\.](?:\d{1,2}|[a-zA-Z]{3})[\/\-\.]\d{2,4}|\d{4}[\-\/]\d{2}[\-\/]\d{2})\b/;
  const amountRegex = /(?:INR|Rs\.?|₹)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)/g;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    // Must match a date
    const dateMatch = line.match(dateRegex);
    if (!dateMatch) continue;

    const rawDate = dateMatch[1];
    const normalizedDate = normalizeDateString(rawDate);
    if (!normalizedDate) continue;

    // Remove the date string so its numbers are not treated as monetary amounts
    let remainingLine = line.replace(rawDate, ' ').trim();

    // Extract reference number
    const refMatch = remainingLine.match(/\b(?:UPI\/?|IMPS\/?|NEFT\/?|REF\/?|CHQ\/?|UTR\/?|TXN\/?|NO:?\s*)([0-9a-zA-Z]{6,22})\b/i);
    const referenceNumber = refMatch ? refMatch[1] : '';

    // Remove raw UPI/IMPS/NEFT string token for amount searching
    let lineForAmounts = remainingLine.replace(/\b(?:UPI|IMPS|NEFT)[\/\-\:][0-9a-zA-Z\/\-\:]+/gi, ' ');

    // Find all monetary amounts in the line (prefer decimal numbers with .XX or comma formatted)
    const amounts = [];
    const decRegex = /(?:INR|Rs\.?|₹)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*\.[0-9]{2}|[0-9]{1,8}\.[0-9]{2})\b/g;
    let match;
    while ((match = decRegex.exec(lineForAmounts)) !== null) {
      const val = parseFloat(match[1].replace(/,/g, ''));
      if (!isNaN(val) && val > 0) {
        amounts.push(val);
      }
    }

    // If no decimal numbers found, try integer amounts with currency or clear word boundaries
    if (amounts.length === 0) {
      const intRegex = /(?:INR|Rs\.?|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})*|[0-9]{2,7})\b/g;
      while ((match = intRegex.exec(lineForAmounts)) !== null) {
        const val = parseFloat(match[1].replace(/,/g, ''));
        if (!isNaN(val) && val > 0) {
          amounts.push(val);
        }
      }
    }

    if (amounts.length === 0) continue;

    // Detect DR/CR or negative
    const isCredit = /\b(cr|credit|deposit|inflow|refund|salary)\b/i.test(line) || line.includes('+');
    const isDebit = /\b(dr|debit|withdrawal|out|to\s+transfer)\b/i.test(line) || !isCredit;

    let debitAmount = 0;
    let creditAmount = 0;
    let balance = null;

    if (amounts.length === 1) {
      if (isCredit) {
        creditAmount = amounts[0];
      } else {
        debitAmount = amounts[0];
      }
    } else if (amounts.length >= 2) {
      if (isCredit) {
        creditAmount = amounts[0];
        balance = amounts[1];
      } else {
        debitAmount = amounts[0];
        balance = amounts[1];
      }
    }

    const amount = debitAmount > 0 ? debitAmount : creditAmount;
    if (amount <= 0) continue;

    // Clean description: extract meaningful merchant/payee
    let cleanDesc = remainingLine;

    // If it's a UPI narrative like UPI/ref/DR/MERCHANT/...
    const upiParts = remainingLine.match(/UPI[\/\-\:][^/]+\/(?:DR|CR|dr|cr)\/([^/]+)(?:\/([^/]+))?/i);
    if (upiParts) {
      const merchant = upiParts[1].replace(/[0-9]{1,3}(?:,[0-9]{2,3})*\.[0-9]{2}/g, '').trim();
      let extra = upiParts[2] ? upiParts[2].replace(/[0-9]{1,3}(?:,[0-9]{2,3})*\.[0-9]{2}/g, '').trim() : '';
      cleanDesc = extra && !extra.toLowerCase().startsWith('hdfc') && !extra.toLowerCase().startsWith('sbin') && !extra.toLowerCase().startsWith('icic')
        ? `${merchant} - ${extra}`
        : merchant;
    } else {
      // Remove amounts from description
      amounts.forEach(a => {
        cleanDesc = cleanDesc.replace(new RegExp(`\\b${a}(?:\\.00)?\\b`, 'g'), ' ');
      });
      cleanDesc = cleanDesc
        .replace(/INR|Rs\.?|₹/gi, '')
        .replace(/\b(dr|cr|debit|credit)\b/gi, '')
        .replace(/^UPI[\/\-\:]\s*[0-9a-zA-Z]+\s*[\/\-\:]/i, '')
        .replace(/^IMPS[\/\-\:]\s*[0-9a-zA-Z]+\s*[\/\-\:]/i, '')
        .replace(/^NEFT[\/\-\:]\s*[0-9a-zA-Z]+\s*[\/\-\:]/i, '')
        .replace(/^POS[\/\-\:]\s*[0-9a-zA-Z]+\s*[\/\-\:]/i, '')
        .replace(/[0-9]{1,3}(?:,[0-9]{2,3})*\.[0-9]{2}/g, '')
        .replace(/\s+/g, ' ')
        .trim();
    }

    if (cleanDesc.length < 2) {
      cleanDesc = isCredit ? 'Incoming Credit' : 'Bank Outflow';
    }

    const suggestedCategory = deduceCategoryFromNarrative(cleanDesc);

    transactions.push({
      transactionDate: normalizedDate,
      description: cleanDesc.slice(0, 100),
      debitAmount: Number(debitAmount.toFixed(2)),
      creditAmount: Number(creditAmount.toFixed(2)),
      amount: Number(amount.toFixed(2)),
      transactionType: creditAmount > 0 ? 'INCOME' : 'EXPENSE',
      balanceAfterTransaction: balance !== null ? Number(balance.toFixed(2)) : null,
      suggestedCategory,
      referenceNumber: referenceNumber.slice(0, 30)
    });
  }

  return transactions;
}

function normalizeDateString(dateStr) {
  if (!dateStr) return null;
  const parts = dateStr.replace(/[\.\-]/g, '/').split('/');
  if (parts.length !== 3) return null;

  const now = new Date();
  const currentYear = now.getFullYear();

  let day, month, year;

  // Handle YYYY/MM/DD
  if (parts[0].length === 4) {
    year = parseInt(parts[0], 10);
    month = parseInt(parts[1], 10);
    day = parseInt(parts[2], 10);
  } else {
    // DD/MM/YYYY or DD/MMM/YYYY
    day = parseInt(parts[0], 10);
    const monthPart = parts[1].toLowerCase();
    const months = {
      jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6,
      jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12
    };
    if (months[monthPart]) {
      month = months[monthPart];
    } else {
      month = parseInt(parts[1], 10);
    }
    year = parseInt(parts[2], 10);
    if (year < 100) year += 2000;
  }

  if (isNaN(day) || isNaN(month) || isNaN(year)) return null;
  if (day < 1 || day > 31 || month < 1 || month > 12 || year < 2000 || year > currentYear + 1) return null;

  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function deduceCategoryFromNarrative(text) {
  const lower = text.toLowerCase();
  if (/swiggy|zomato|mcdonald|kfc|starbucks|domino|restaurant|cafe|food|dining|eats/i.test(lower)) return 'Food & Dining';
  if (/blinkit|zepto|instamart|bigbasket|dmart|grocery|supermarket|spencer/i.test(lower)) return 'Groceries';
  if (/amazon|flipkart|myntra|ajio|zara|nykaa|shopping|retail|store/i.test(lower)) return 'Shopping';
  if (/uber|ola|rapido|petrol|fuel|hpcl|bpcl|ioc|diesel|shell|metro|irctc/i.test(lower)) return 'Travel & Fuel';
  if (/salary|payroll|wages|stipend|bonus|incentive/i.test(lower)) return 'Salary';
  if (/electricity|bescom|tneb|airtel|jio|vodafone|broadband|wifi|water\s+bill|gas/i.test(lower)) return 'Utilities';
  if (/netflix|spotify|hotstar|prime|youtube|cinema|pvr|inox|movie/i.test(lower)) return 'Entertainment';
  if (/zerodha|groww|upstox|mf|mutual\s+fund|sip|investment|stocks/i.test(lower)) return 'Investment';
  if (/rent|maintenance|society|housing/i.test(lower)) return 'Rent & Housing';
  if (/pharmacy|apollo|1mg|medplus|hospital|clinic|doctor|health/i.test(lower)) return 'Medical & Health';
  return 'General';
}

class StatementAiService {
  /**
   * Intelligently parses statement text into clean transactions using Gemini AI with local fallback.
   */
  async parseStatement(rawText, fileName = '') {
    if (!rawText || typeof rawText !== 'string' || rawText.trim().length === 0) {
      throw new Error('INVALID_INPUT: Statement text content is empty or invalid.');
    }

    const filteredText = cleanAndFilterRawStatementText(rawText);
    const maskedText = maskPii(filteredText);
    const apiKey = process.env.GEMINI_API_KEY;

    // If Gemini API Key is not set, use high-speed intelligent local parser
    if (!apiKey || apiKey.trim() === '' || apiKey === 'YOUR_GEMINI_API_KEY') {
      const localResults = localHeuristicStatementParser(maskedText, fileName);
      return {
        success: true,
        transactions: localResults,
        totalParsed: localResults.length,
        source: 'local_ai_cleaner'
      };
    }

    try {
      const systemInstruction = `You are an expert financial bank statement parser for Indian and international bank statements.
Your mission is to parse raw statement text and extract ONLY genuine individual financial transactions into a structured JSON array.

CRITICAL RULES:
1. Ignore all bank disclaimers, branch addresses, GST numbers, customer IDs, header notices, and page footer lines.
2. Ignore opening balances, closing balances, and summary statistics.
3. Clean the transaction description:
   - Remove messy bank routing codes (e.g. "UPI/428919283921/DR/...", "NEFT-AXIS000123-...", "POS 123456").
   - Extract the clear merchant or payee name (e.g. "Swiggy", "Amazon", "Infosys Salary", "Shell Petrol").
4. Classify transaction type accurately:
   - "EXPENSE" for debits/withdrawals.
   - "INCOME" for credits/deposits/salary/refunds.
   - "TRANSFER" for account-to-account transfers.
5. Standardize transactionDate into ISO "YYYY-MM-DD" format.
6. Return STRICT JSON ARRAY ONLY with objects having these exact keys:
   - "transactionDate": "YYYY-MM-DD",
   - "description": "Clean merchant or description",
   - "debitAmount": number (0 if credit),
   - "creditAmount": number (0 if debit),
   - "amount": number (positive amount),
   - "transactionType": "EXPENSE" | "INCOME" | "TRANSFER",
   - "balanceAfterTransaction": number or null,
   - "suggestedCategory": string ("Food & Dining", "Groceries", "Shopping", "Travel & Fuel", "Salary", "Utilities", "Entertainment", "Investment", "Rent & Housing", "Medical & Health", or "General"),
   - "referenceNumber": string (UPI ID or Ref No or empty string "")
`;

      const prompt = `Parse the following bank statement lines and extract the valid transactions as a JSON array:
Statement file: ${fileName || 'bank_statement'}
---
${maskedText.slice(0, 40000)}
---
JSON:`;

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 18000);

      const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${apiKey}`;

      const res = await fetch(apiUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          system_instruction: {
            parts: [{ text: systemInstruction }]
          },
          contents: [
            {
              role: 'user',
              parts: [{ text: prompt }]
            }
          ],
          generationConfig: {
            temperature: 0.1,
            responseMimeType: 'application/json'
          }
        }),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        console.warn(`[GEMINI_STATEMENT_WARNING] Status ${res.status}. Falling back to local parser.`);
        const localResults = localHeuristicStatementParser(maskedText, fileName);
        return {
          success: true,
          transactions: localResults,
          totalParsed: localResults.length,
          source: 'local_ai_cleaner'
        };
      }

      const data = await res.json();
      const candidateText = data?.candidates?.[0]?.content?.parts?.[0]?.text;

      if (!candidateText || candidateText.trim().length === 0) {
        const localResults = localHeuristicStatementParser(maskedText, fileName);
        return {
          success: true,
          transactions: localResults,
          totalParsed: localResults.length,
          source: 'local_ai_cleaner'
        };
      }

      // Parse JSON from candidate
      let parsedJson;
      try {
        parsedJson = JSON.parse(candidateText.trim());
      } catch (parseErr) {
        // Handle code block wrappers if any
        const cleaned = candidateText.replace(/```(?:json)?/g, '').replace(/```/g, '').trim();
        parsedJson = JSON.parse(cleaned);
      }

      const txList = Array.isArray(parsedJson) ? parsedJson : (parsedJson.transactions || []);

      const sanitizedList = txList.map(tx => ({
        transactionDate: tx.transactionDate || new Date().toISOString().slice(0, 10),
        description: (tx.description || 'Transaction').slice(0, 100),
        debitAmount: Number(Number(tx.debitAmount || 0).toFixed(2)),
        creditAmount: Number(Number(tx.creditAmount || 0).toFixed(2)),
        amount: Number(Number(tx.amount || tx.debitAmount || tx.creditAmount || 0).toFixed(2)),
        transactionType: tx.transactionType === 'INCOME' ? 'INCOME' : (tx.transactionType === 'TRANSFER' ? 'TRANSFER' : 'EXPENSE'),
        balanceAfterTransaction: tx.balanceAfterTransaction !== null && tx.balanceAfterTransaction !== undefined ? Number(Number(tx.balanceAfterTransaction).toFixed(2)) : null,
        suggestedCategory: tx.suggestedCategory || 'General',
        referenceNumber: (tx.referenceNumber || '').slice(0, 30)
      }));

      return {
        success: true,
        transactions: sanitizedList,
        totalParsed: sanitizedList.length,
        source: 'gemini'
      };
    } catch (err) {
      console.warn(`[GEMINI_STATEMENT_ERROR] ${err.message}. Falling back to local parser.`);
      const localResults = localHeuristicStatementParser(maskedText, fileName);
      return {
        success: true,
        transactions: localResults,
        totalParsed: localResults.length,
        source: 'local_ai_cleaner'
      };
    }
  }
}

module.exports = new StatementAiService();
