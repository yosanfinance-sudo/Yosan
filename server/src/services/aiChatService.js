const financeRepository = require('../repositories/financeRepository');
const userRepository = require('../repositories/userRepository');

/**
 * Masks Personally Identifiable Information (PII) like full bank account numbers,
 * phone numbers, emails, and UPI IDs.
 */
function maskPii(text) {
  if (!text || typeof text !== 'string') return text;
  return text
    // UPI handles: example@upi -> e•••e@upi
    .replace(/\b([a-zA-Z0-9_\.]{1,2})[a-zA-Z0-9_\.]+(@[a-zA-Z0-9]+)\b/g, '$1••••$2')
    // Emails: test@domain.com -> t•••t@domain.com
    .replace(/\b([a-zA-Z0-9_\.]{1,2})[a-zA-Z0-9_\.]+(@[a-zA-Z0-9\.\-]+\.[a-zA-Z]{2,})\b/g, '$1••••$2')
    // Indian phone numbers / 10-12 digits: 9876543210 -> ••••3210
    .replace(/\b(?:\+?91[\-\s]?)?[6-9]\d{5}(\d{4})\b/g, '••••$1')
    // Bank account numbers (8 to 18 digits) -> ••••1234
    .replace(/\b\d{4,14}(\d{4})\b/g, '••••$1');
}

/**
 * Aggregates user's financial overview for AI context.
 */
function aggregateFinancialContext(userId) {
  const accounts = financeRepository.getAccounts(userId) || [];
  const allTxs = financeRepository.getTransactions(userId) || [];
  const loans = financeRepository.getActiveLoans(userId) || [];
  const categories = financeRepository.getCategories(userId) || [];
  const profile = userRepository.getUserProfile(userId);
  const currencySymbol = profile?.currency_symbol || '₹';

  // Accounts summary
  const totalBalance = accounts.reduce((acc, a) => acc + (a.current_balance || 0), 0);
  const accountsSummary = accounts.map(a => ({
    name: a.account_name,
    type: a.account_type,
    balance: a.current_balance || 0,
    maskedNumber: a.account_number_masked || 'N/A'
  }));

  // Current month transactions aggregation
  const now = new Date();
  const currentYearMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

  let monthlyIncome = 0;
  let monthlyExpense = 0;
  const categoryExpenses = {};

  allTxs.forEach(tx => {
    const isCurrentMonth = tx.transaction_date && tx.transaction_date.startsWith(currentYearMonth);
    const amount = Number(tx.amount || tx.debit_amount || tx.credit_amount || 0);

    if (tx.transaction_type === 'INCOME') {
      if (isCurrentMonth) monthlyIncome += amount;
    } else if (tx.transaction_type === 'EXPENSE') {
      if (isCurrentMonth) {
        monthlyExpense += amount;
        const cat = tx.category_name || 'Uncategorized';
        categoryExpenses[cat] = (categoryExpenses[cat] || 0) + amount;
      }
    }
  });

  // Category distribution
  const categoryDistribution = Object.entries(categoryExpenses)
    .map(([cat, amt]) => ({
      category: cat,
      amount: amt,
      share: monthlyExpense > 0 ? ((amt / monthlyExpense) * 100).toFixed(1) + '%' : '0%'
    }))
    .sort((a, b) => b.amount - a.amount);

  // Loans breakdown
  const loansSummary = loans.map(l => ({
    name: maskPii(l.borrower_lender_name),
    type: l.loan_type === 'GIVEN' ? 'Lent to Others (Receivable)' : 'Borrowed (Payable)',
    totalAmount: l.amount,
    remainingAmount: l.remaining_amount,
    dueDate: l.due_date || 'None',
    status: l.status
  }));

  // Highest expenses
  const allExpenses = allTxs
    .filter(tx => tx.transaction_type === 'EXPENSE' || (Number(tx.debit_amount || 0) > 0 && tx.transaction_type !== 'INCOME'))
    .sort((a, b) => Number(b.amount || b.debit_amount || 0) - Number(a.amount || a.debit_amount || 0));

  const highestExpenses = allExpenses.slice(0, 5).map(tx => ({
    date: tx.transaction_date,
    description: maskPii(tx.description),
    category: tx.category_name || 'Uncategorized',
    amount: Number(tx.amount || tx.debit_amount || 0)
  }));

  // All incomes
  const allIncomes = allTxs
    .filter(tx => tx.transaction_type === 'INCOME' || (Number(tx.credit_amount || 0) > 0 && tx.transaction_type !== 'EXPENSE'))
    .sort((a, b) => Number(b.amount || b.credit_amount || 0) - Number(a.amount || a.credit_amount || 0));

  // Recent 10 transactions (PII masked)
  const recentTransactions = allTxs.slice(0, 10).map(tx => ({
    date: tx.transaction_date,
    description: maskPii(tx.description),
    type: tx.transaction_type,
    amount: Number(tx.amount || tx.debit_amount || tx.credit_amount || 0),
    category: tx.category_name || 'Uncategorized'
  }));

  const userName = profile?.name ? profile.name.split(' ')[0] : 'there';

  return {
    userName,
    currencySymbol,
    totalBalance,
    accountsSummary,
    monthlyIncome,
    monthlyExpense,
    monthlySavings: monthlyIncome - monthlyExpense,
    categoryDistribution,
    loansSummary,
    recentTransactions,
    highestExpenses,
    allExpenses,
    allIncomes,
    allTxs
  };
}

/**
 * Intelligent Local Rule-Based Analyzer.
 * Accurately detects user intent and returns tailored Markdown table responses
 * specifically addressing what the user asked.
 */
function generateLocalStructuredResponse(ctx, userMessage) {
  const sym = ctx.currencySymbol;
  const msgRaw = (userMessage || '').trim();
  const msgLower = msgRaw.toLowerCase();

  // 1. Greetings, Conversational, Identity & Capabilities
  const greetingRegex = /^(hi|hello|hey|good\s*(morning|afternoon|evening)|namaste|who are you|what can you do|help|how are you|start)\b/i;
  if (greetingRegex.test(msgLower) || ['hi', 'hello', 'hey', 'help', 'who are you', 'how are you'].includes(msgLower)) {
    return `### 👋 Hello ${ctx.userName}! I am your Yosan AI Financial Assistant.

I analyze your real transactions, accounts, debts, and spending habits to give you direct answers.

| Category | What you can ask me |
| :--- | :--- |
| 🏆 Top Expenses | "What is my highest expense?", "Where did I spend the most?" |
| 🍔 Category Spending | "How much did I spend on Food?", "Show my Groceries expenses" |
| 🕒 Recent Activity | "Show recent transactions", "What did I spend today?" |
| 💰 Account Balances | "What is my bank balance?", "Show my net worth" |
| 🤝 Loans & Debts | "Who owes me money?", "Show my active loans" |
| 💡 Savings Advice | "How can I save more this month?", "Suggest a budget" |

You currently have **${sym}${ctx.totalBalance.toLocaleString('en-IN')}** across **${ctx.accountsSummary.length} accounts**. What would you like to analyze?`;
  }

  // 2. Highest / Largest / Top Expenses
  if (
    msgLower.includes('highest') ||
    msgLower.includes('biggest') ||
    msgLower.includes('largest') ||
    msgLower.includes('top expense') ||
    msgLower.includes('most expensive') ||
    msgLower.includes('maximum') ||
    msgLower.includes('where did i spend the most')
  ) {
    if (ctx.highestExpenses.length === 0) {
      return `### Top Expenses\n\nNo expense transactions have been recorded yet.\n\n| Rank | Description | Category | Amount |\n| :--- | :--- | :--- | :--- |\n| - | No expenses logged | - | ${sym}0.00 |\n\nImport your bank statement or add transactions to track your largest expenditures.`;
    }

    const top = ctx.highestExpenses[0];
    let rows = ctx.highestExpenses.map((e, idx) =>
      `| #${idx + 1} | ${e.description} | ${e.category} | ${e.date} | ${sym}${e.amount.toLocaleString('en-IN')} |`
    ).join('\n');

    return `### Top 5 Largest Expenses\n\nHere are your highest single expenses on record:\n\n| # | Description | Category | Date | Amount |\n| :--- | :--- | :--- | :--- | :--- |\n${rows}\n\n**Key Insight:** Your highest single expense is **${top.description}** at **${sym}${top.amount.toLocaleString('en-IN')}** (${top.category}). Tracking your top 5 expenses helps identify high-impact savings opportunities!`;
  }

  // 3. Recent / Latest Transactions
  if (
    msgLower.includes('recent') ||
    msgLower.includes('latest') ||
    msgLower.includes('history') ||
    msgLower.includes('last transaction') ||
    msgLower.includes('past transaction') ||
    msgLower.includes('recent activity')
  ) {
    if (ctx.recentTransactions.length === 0) {
      return `### Recent Activity\n\nNo transactions have been logged in Yosan yet.\n\n| Date | Description | Type | Amount |\n| :--- | :--- | :--- | :--- |\n| - | No records found | - | ${sym}0.00 |`;
    }

    let rows = ctx.recentTransactions.map(t => {
      const isPositive = t.type === 'INCOME' || t.type === 'REFUND';
      const prefix = isPositive ? '+' : '-';
      return `| ${t.date} | ${t.description} | ${t.category} | ${t.type} | ${prefix}${sym}${t.amount.toLocaleString('en-IN')} |`;
    }).join('\n');

    return `### Recent Transactions\n\nHere is your latest transaction activity:\n\n| Date | Description | Category | Type | Amount |\n| :--- | :--- | :--- | :--- | :--- |\n${rows}\n\n**Summary:** Displaying your last ${ctx.recentTransactions.length} recorded entries.`;
  }

  // 4. Income / Salary / Earnings Queries
  if (
    msgLower.includes('salary') ||
    msgLower.includes('income') ||
    msgLower.includes('earn') ||
    msgLower.includes('inflow') ||
    msgLower.includes('deposit') ||
    msgLower.includes('credited')
  ) {
    if (ctx.allIncomes.length === 0) {
      return `### Monthly Income & Salary\n\nNo income or salary records found for this period.\n\n| Source | Category | Date | Amount |\n| :--- | :--- | :--- | :--- |\n| Salary / Income | General | - | ${sym}0.00 |\n\nLog your monthly salary or deposits to calculate your accurate savings rate.`;
    }

    const incomeRows = ctx.allIncomes.slice(0, 5).map(inc => {
      const amt = Number(inc.amount || inc.credit_amount || 0);
      return `| ${inc.transaction_date} | ${maskPii(inc.description)} | ${inc.category_name || 'Income'} | +${sym}${amt.toLocaleString('en-IN')} |`;
    }).join('\n');

    return `### Income & Earnings Summary\n\nYour total income this month is **${sym}${ctx.monthlyIncome.toLocaleString('en-IN')}**.\n\n| Date | Description | Category | Amount |\n| :--- | :--- | :--- | :--- |\n${incomeRows}\n\n| Metric | Amount | Share of Income |\n| :--- | :--- | :--- |\n| Total Inflow | ${sym}${ctx.monthlyIncome.toLocaleString('en-IN')} | 100% |\n| Monthly Expenses | ${sym}${ctx.monthlyExpense.toLocaleString('en-IN')} | ${ctx.monthlyIncome > 0 ? ((ctx.monthlyExpense / ctx.monthlyIncome) * 100).toFixed(1) : 0}% |\n| Net Savings | ${sym}${ctx.monthlySavings.toLocaleString('en-IN')} | ${ctx.monthlyIncome > 0 ? ((ctx.monthlySavings / ctx.monthlyIncome) * 100).toFixed(1) : 0}% |`;
  }

  // 5. Debt / Loan Queries
  if (
    msgLower.includes('loan') ||
    msgLower.includes('debt') ||
    msgLower.includes('borrow') ||
    msgLower.includes('lend') ||
    msgLower.includes('owe')
  ) {
    if (ctx.loansSummary.length === 0) {
      return `### Active Loans & Debts\n\nYou currently have **no active loans or outstanding debts** recorded.\n\n| Category | Outstanding | Status |\n| :--- | :--- | :--- |\n| Borrowed (Payables) | ${sym}0.00 | Clear |\n| Lent (Receivables) | ${sym}0.00 | Clear |\n\nMaintaining zero high-interest debt is great for your financial stability!`;
    }

    let rows = ctx.loansSummary.map(l =>
      `| ${l.name} | ${l.type} | ${sym}${l.remainingAmount.toLocaleString('en-IN')} | ${l.dueDate} | ${l.status} |`
    ).join('\n');

    return `### Active Loans & Debts Overview\n\nHere is your current loan and debt summary:\n\n| Party | Type | Remaining | Due Date | Status |\n| :--- | :--- | :--- | :--- | :--- |\n${rows}\n\n**Financial Tip:** Prioritize paying off debts with the nearest due dates or highest interest rates first.`;
  }

  // 6. Account balances & Net worth
  if (
    msgLower.includes('account') ||
    msgLower.includes('balance') ||
    msgLower.includes('net worth') ||
    msgLower.includes('bank') ||
    msgLower.includes('how much money')
  ) {
    let rows = ctx.accountsSummary.map(a =>
      `| ${a.name} | ${a.type} | ${sym}${a.balance.toLocaleString('en-IN')} |`
    ).join('\n');

    return `### Account Balances Breakdown\n\nYour total net balance across all accounts is **${sym}${ctx.totalBalance.toLocaleString('en-IN')}**.\n\n| Account | Type | Current Balance |\n| :--- | :--- | :--- |\n${rows}\n\n**Tip:** Keep at least 3-6 months worth of essential expenses liquid in your savings account as an emergency fund.`;
  }

  // 7. Savings Tips & Budgeting Advice
  if (
    msgLower.includes('save') ||
    msgLower.includes('saving') ||
    msgLower.includes('budget') ||
    msgLower.includes('advice') ||
    msgLower.includes('tip') ||
    msgLower.includes('cut expense')
  ) {
    const savingsRate = ctx.monthlyIncome > 0
      ? (((ctx.monthlySavings) / ctx.monthlyIncome) * 100).toFixed(1)
      : '0.0';

    const targetNeeds = ctx.monthlyIncome * 0.50;
    const targetWants = ctx.monthlyIncome * 0.30;
    const targetSavings = ctx.monthlyIncome * 0.20;

    return `### Smart Savings & Budgeting Guide (50/30/20 Rule)

Based on your current monthly income of **${sym}${ctx.monthlyIncome.toLocaleString('en-IN')}**, here is your target allocation:

| Budget Pillar | Share | Target Amount | Current Status |
| :--- | :--- | :--- | :--- |
| Needs (Rent, Utilities, Food) | 50% | ${sym}${targetNeeds.toLocaleString('en-IN')} | Essential Living |
| Wants (Dining out, Shopping) | 30% | ${sym}${targetWants.toLocaleString('en-IN')} | Discretionary Spend |
| Savings & Debt Repayments | 20% | ${sym}${targetSavings.toLocaleString('en-IN')} | ${Number(savingsRate) >= 20 ? `On Track (${savingsRate}%)` : `Needs Focus (${savingsRate}%)`} |

#### Top 3 Actionable Savings Steps:
1. **Target Highest Spend**: Review your top category expenses for recurring subscriptions or impulse buys.
2. **Pay Yourself First**: Transfer 15-20% of your earnings to a separate savings or investment account right after payday.
3. **Emergency Fund**: Build an emergency cushion of 3-6 months worth of living costs before aggressive investing.`;
  }

  // 8. Specific Category Query (e.g., Food, Travel, Groceries, Shopping, Fuel, Rent, etc.)
  const knownCategories = [
    'food', 'dining', 'grocery', 'groceries', 'travel', 'fuel', 'shopping',
    'entertainment', 'bill', 'utility', 'utilities', 'rent', 'medical', 'health',
    'official', 'transport', 'education', 'recharge'
  ];

  const matchedCatKeyword = knownCategories.find(k => msgLower.includes(k)) ||
    ctx.categoryDistribution.map(c => c.category.toLowerCase()).find(c => msgLower.includes(c));

  if (matchedCatKeyword) {
    const matchingTxs = ctx.allExpenses.filter(tx => {
      const cat = (tx.category_name || '').toLowerCase();
      const desc = (tx.description || '').toLowerCase();
      return cat.includes(matchedCatKeyword) || desc.includes(matchedCatKeyword);
    });

    const catTotal = matchingTxs.reduce((sum, tx) => sum + Number(tx.amount || tx.debit_amount || 0), 0);
    const catName = matchingTxs[0]?.category_name || matchedCatKeyword.toUpperCase();

    if (matchingTxs.length === 0) {
      let availableCats = ctx.categoryDistribution.map(c => `| ${c.category} | ${sym}${c.amount.toLocaleString('en-IN')} | ${c.share} |`).join('\n');
      return `### ${catName} Spending\n\nYou currently have **no recorded expenses** under "${matchedCatKeyword}".\n\n#### Available Expense Categories:\n| Category | Total Spent | Share |\n| :--- | :--- | :--- |\n${availableCats || '| General | ' + sym + '0.00 | 100% |'}`;
    }

    const avgSpend = (catTotal / matchingTxs.length).toFixed(2);
    const itemRows = matchingTxs.slice(0, 6).map(tx =>
      `| ${tx.transaction_date} | ${maskPii(tx.description)} | ${sym}${Number(tx.amount || tx.debit_amount || 0).toLocaleString('en-IN')} |`
    ).join('\n');

    return `### ${catName} Spending Breakdown\n\nYou have spent **${sym}${catTotal.toLocaleString('en-IN')}** across **${matchingTxs.length} transactions** on ${catName} (averaging **${sym}${Number(avgSpend).toLocaleString('en-IN')}** per transaction).\n\n| Date | Description | Amount |\n| :--- | :--- | :--- |\n${itemRows}\n\n**Category Share:** This represents **${ctx.monthlyExpense > 0 ? ((catTotal / ctx.monthlyExpense) * 100).toFixed(1) : 0}%** of your total monthly expenditures.`;
  }

  // 9. Time-Based Queries (Today / Yesterday / This Week)
  const now = new Date();
  const todayStr = now.toISOString().split('T')[0];
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const yesterdayStr = yesterday.toISOString().split('T')[0];

  if (msgLower.includes('today') || msgLower.includes('yesterday')) {
    const targetDate = msgLower.includes('yesterday') ? yesterdayStr : todayStr;
    const targetLabel = msgLower.includes('yesterday') ? 'Yesterday' : 'Today';

    const dayTxs = ctx.allTxs.filter(tx => tx.transaction_date === targetDate);
    const dayExpense = dayTxs
      .filter(tx => tx.transaction_type === 'EXPENSE' || tx.debit_amount > 0)
      .reduce((sum, tx) => sum + Number(tx.amount || tx.debit_amount || 0), 0);

    if (dayTxs.length === 0) {
      return `### ${targetLabel}'s Financial Summary\n\nNo transactions were recorded for **${targetLabel} (${targetDate})**.\n\n| Metric | Amount | Status |\n| :--- | :--- | :--- |\n| Expenses | ${sym}0.00 | Clear |\n| Income | ${sym}0.00 | None |`;
    }

    const dayRows = dayTxs.map(t =>
      `| ${t.description} | ${t.category_name || 'General'} | ${t.transaction_type} | ${sym}${Number(t.amount || t.debit_amount || t.credit_amount || 0).toLocaleString('en-IN')} |`
    ).join('\n');

    return `### ${targetLabel}'s Spending (${targetDate})\n\nTotal outflow for ${targetLabel.toLowerCase()} is **${sym}${dayExpense.toLocaleString('en-IN')}** across **${dayTxs.length} transactions**.\n\n| Description | Category | Type | Amount |\n| :--- | :--- | :--- | :--- |\n${dayRows}`;
  }

  // 10. Default / Comprehensive Monthly Breakdown
  let catRows = ctx.categoryDistribution.length > 0
    ? ctx.categoryDistribution.map(c => `| ${c.category} | ${sym}${c.amount.toLocaleString('en-IN')} | ${c.share} |`).join('\n')
    : `| Uncategorized | ${sym}0.00 | 100% |`;

  const savingsRate = ctx.monthlyIncome > 0
    ? (((ctx.monthlySavings) / ctx.monthlyIncome) * 100).toFixed(1)
    : '0.0';

  return `### Monthly Financial Breakdown\n\nHere is your financial overview for the current month:\n\n| Metric | Amount | Description |\n| :--- | :--- | :--- |\n| Total Income | ${sym}${ctx.monthlyIncome.toLocaleString('en-IN')} | Inflow this month |\n| Total Expenses | ${sym}${ctx.monthlyExpense.toLocaleString('en-IN')} | Outflow this month |\n| Net Savings | ${sym}${ctx.monthlySavings.toLocaleString('en-IN')} | Savings rate: ${savingsRate}% |\n| Total Liquid Balance | ${sym}${ctx.totalBalance.toLocaleString('en-IN')} | Across all accounts |\n\n#### Category Distribution\n\n| Category | Amount | Share |\n| :--- | :--- | :--- |\n${catRows}\n\n**Actionable Advice:** ${
    Number(savingsRate) > 20
      ? 'Great job! Your savings rate is above 20%. Consider allocating excess savings towards index funds or paying down debts.'
      : 'Aim to keep non-essential expenses under 30% of your income following the 50/30/20 budgeting rule to increase your monthly savings buffer.'
  }`;
}

class AiChatService {
  async processChat(userId, userMessage, conversationHistory = [], clientContext = null) {
    const ctx = aggregateFinancialContext(userId);

    if (clientContext && typeof clientContext === 'object') {
      if (clientContext.userName) ctx.userName = clientContext.userName;
      if (clientContext.currencySymbol) ctx.currencySymbol = clientContext.currencySymbol;
      if (typeof clientContext.totalBalance === 'number') ctx.totalBalance = clientContext.totalBalance;
      if (typeof clientContext.monthlyIncome === 'number') ctx.monthlyIncome = clientContext.monthlyIncome;
      if (typeof clientContext.monthlyExpense === 'number') ctx.monthlyExpense = clientContext.monthlyExpense;
      if (typeof clientContext.monthlySavings === 'number') {
        ctx.monthlySavings = clientContext.monthlySavings;
      } else if (typeof clientContext.monthlyIncome === 'number' && typeof clientContext.monthlyExpense === 'number') {
        ctx.monthlySavings = clientContext.monthlyIncome - clientContext.monthlyExpense;
      }
      if (Array.isArray(clientContext.accountsSummary) && clientContext.accountsSummary.length > 0) {
        ctx.accountsSummary = clientContext.accountsSummary;
      }
      if (Array.isArray(clientContext.categoryDistribution) && clientContext.categoryDistribution.length > 0) {
        ctx.categoryDistribution = clientContext.categoryDistribution;
      }
      if (Array.isArray(clientContext.loansSummary) && clientContext.loansSummary.length > 0) {
        ctx.loansSummary = clientContext.loansSummary;
      }
      if (Array.isArray(clientContext.highestExpenses) && clientContext.highestExpenses.length > 0) {
        ctx.highestExpenses = clientContext.highestExpenses;
      }
      if (Array.isArray(clientContext.allExpenses) && clientContext.allExpenses.length > 0) {
        ctx.allExpenses = clientContext.allExpenses;
      }
      if (Array.isArray(clientContext.allIncomes) && clientContext.allIncomes.length > 0) {
        ctx.allIncomes = clientContext.allIncomes;
      }
      if (Array.isArray(clientContext.recentTransactions) && clientContext.recentTransactions.length > 0) {
        ctx.recentTransactions = clientContext.recentTransactions;
      }
      if (Array.isArray(clientContext.allTxs) && clientContext.allTxs.length > 0) {
        ctx.allTxs = clientContext.allTxs;
      }
    }

    const apiKey = process.env.GEMINI_API_KEY;

    // If GEMINI_API_KEY is not configured or in dev fallback mode, use intelligent local analyzer
    if (!apiKey || apiKey.trim() === '' || apiKey === 'YOUR_GEMINI_API_KEY') {
      return {
        reply: generateLocalStructuredResponse(ctx, userMessage),
        source: 'local_analyzer'
      };
    }

    try {
      const systemInstruction = `You are Yosan Financial Assistant, an intelligent, empathetic, and analytical AI personal finance advisor.
Your mission is to provide clear, actionable financial advice, summaries, breakdowns, and answers to the user's questions based on their real financial context provided below.

CRITICAL INSTRUCTIONS:
1. Directly and specifically answer the user's question.
   - If the user greets you or asks for general help, greet them warmly, state what you can do, and suggest 3-4 specific financial queries based on their data.
   - If the user asks about a specific category (e.g. Food, Travel), focus ONLY on that category. Do NOT dump the full monthly breakdown.
   - If the user asks for highest/largest expenses, provide their top expenses.
   - If the user asks for accounts or balances, answer with their account balances.
   - If the user asks for loans or debts, answer with their loans and debts.
   - If the user asks for recent transactions, show their recent transactions.
2. Whenever you present financial numbers, category distributions, account balances, loan/debt summaries, or comparisons, you MUST format them strictly using clean, valid Markdown tables with column headers and alignment (| :--- | :--- |).
3. Always prefix currency values with the user's currency symbol (${ctx.currencySymbol}).
4. Accompany tables with a concise 1-2 sentence actionable insight or recommendation.
5. Mask any sensitive numbers (like account digits or UPI IDs) as provided in context.

Real Financial Context:
- User Name: ${ctx.userName}
- Currency: ${ctx.currencySymbol}
- Total Net Balance: ${ctx.currencySymbol}${ctx.totalBalance.toFixed(2)}
- Accounts: ${JSON.stringify(ctx.accountsSummary)}
- Monthly Income: ${ctx.currencySymbol}${ctx.monthlyIncome.toFixed(2)}
- Monthly Expenses: ${ctx.currencySymbol}${ctx.monthlyExpense.toFixed(2)}
- Monthly Net Savings: ${ctx.currencySymbol}${ctx.monthlySavings.toFixed(2)}
- Category Distribution: ${JSON.stringify(ctx.categoryDistribution)}
- Top 5 Highest Expenses: ${JSON.stringify(ctx.highestExpenses)}
- Active Loans & Debts: ${JSON.stringify(ctx.loansSummary)}
- Recent Transactions: ${JSON.stringify(ctx.recentTransactions)}
`;

      const contents = [];

      // Add prior history if provided
      if (Array.isArray(conversationHistory)) {
        conversationHistory.slice(-4).forEach(h => {
          if (h.role && h.text) {
            contents.push({
              role: h.role === 'model' || h.role === 'assistant' ? 'model' : 'user',
              parts: [{ text: h.text }]
            });
          }
        });
      }

      // Add current user message
      contents.push({
        role: 'user',
        parts: [{ text: userMessage }]
      });

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 15000);

      const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${apiKey}`;

      const res = await fetch(apiUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          system_instruction: {
            parts: [{ text: systemInstruction }]
          },
          contents,
          generationConfig: {
            temperature: 0.3,
            maxOutputTokens: 1024
          }
        }),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        const errorText = await res.text();
        console.warn(`[GEMINI_API_WARNING] Status ${res.status}: ${errorText}. Falling back to local analyzer.`);
        return {
          reply: generateLocalStructuredResponse(ctx, userMessage),
          source: 'local_analyzer'
        };
      }

      const data = await res.json();
      const candidateText = data?.candidates?.[0]?.content?.parts?.[0]?.text;

      if (!candidateText || candidateText.trim().length === 0) {
        return {
          reply: generateLocalStructuredResponse(ctx, userMessage),
          source: 'local_analyzer'
        };
      }

      return {
        reply: candidateText.trim(),
        source: 'gemini'
      };
    } catch (err) {
      console.warn(`[GEMINI_FETCH_ERROR] ${err.message}. Falling back to local analyzer.`);
      return {
        reply: generateLocalStructuredResponse(ctx, userMessage),
        source: 'local_analyzer'
      };
    }
  }
}

module.exports = new AiChatService();
