const db = require('../db');

class FinanceRepository {
  // Accounts
  getAccounts(userId) {
    return db.prepare('SELECT * FROM accounts WHERE user_id = ? ORDER BY is_default DESC, id ASC').all(userId);
  }

  insertAccount(userId, name, bank, numberMasked, type, balance, isDefault, now) {
    return db.prepare(`
      INSERT INTO accounts (user_id, account_name, bank_name, account_number_masked, account_type, opening_balance, current_balance, is_default, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(userId, name, bank, numberMasked, type, balance, balance, isDefault ? 1 : 0, now, now);
  }

  getAccountById(accountId, userId) {
    return db.prepare('SELECT * FROM accounts WHERE id = ? AND user_id = ?').get(accountId, userId);
  }

  // Categories
  getCategories(userId) {
    return db.prepare('SELECT * FROM categories WHERE user_id = ? ORDER BY type ASC, name ASC').all(userId);
  }

  insertCategory(userId, name, type, icon, color, now) {
    return db.prepare(`
      INSERT INTO categories (user_id, name, type, icon, color, is_default, created_at)
      VALUES (?, ?, ?, ?, ?, 0, ?)
    `).run(userId, name, type, icon, color, now);
  }

  getCategoryById(catId, userId) {
    return db.prepare('SELECT * FROM categories WHERE id = ? AND user_id = ?').get(catId, userId);
  }

  // Rules
  getRules(userId) {
    return db.prepare('SELECT * FROM categorization_rules WHERE user_id = ? ORDER BY priority DESC').all(userId);
  }

  insertRule(userId, pattern, categoryId, categoryName, transactionType, priority, matchType, now) {
    return db.prepare(`
      INSERT INTO categorization_rules (user_id, pattern, category_id, category_name, transaction_type, priority, match_type, is_active, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
    `).run(userId, pattern, categoryId, categoryName, transactionType, priority, matchType, now);
  }

  getRuleById(ruleId, userId) {
    return db.prepare('SELECT * FROM categorization_rules WHERE id = ? AND user_id = ?').get(ruleId, userId);
  }

  // Transactions
  getTransactions(userId) {
    return db.prepare('SELECT * FROM transactions WHERE user_id = ? ORDER BY transaction_date DESC, id DESC').all(userId);
  }

  getRecentTransactions(userId, limit) {
    return db.prepare('SELECT * FROM transactions WHERE user_id = ? ORDER BY transaction_date DESC LIMIT ?').all(userId, limit);
  }

  getTransactionById(txId, userId) {
    return db.prepare('SELECT * FROM transactions WHERE id = ? AND user_id = ?').get(txId, userId);
  }

  insertTransaction(userId, accountId, date, description, debit, credit, amount, type, categoryId, categoryName, notes, source, now) {
    return db.prepare(`
      INSERT INTO transactions (user_id, account_id, transaction_date, description, debit_amount, credit_amount, amount, transaction_type, category_id, category_name, notes, source, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(userId, accountId, date, description, debit, credit, amount, type, categoryId, categoryName, notes, source, now, now);
  }

  // Loans
  getLoans(userId) {
    return db.prepare('SELECT * FROM loans WHERE user_id = ? ORDER BY id DESC').all(userId);
  }

  getActiveLoans(userId) {
    return db.prepare("SELECT * FROM loans WHERE user_id = ? AND status != 'PAID'").all(userId);
  }

  getLoanById(loanId, userId) {
    return db.prepare('SELECT * FROM loans WHERE id = ? AND user_id = ?').get(loanId, userId);
  }

  insertLoan(userId, borrowerLenderName, loanType, amount, interestRate, dueDate, notes, now) {
    return db.prepare(`
      INSERT INTO loans (user_id, borrower_lender_name, loan_type, amount, total_repaid, remaining_amount, interest_rate, due_date, notes, status, created_at, updated_at)
      VALUES (?, ?, ?, ?, 0.0, ?, ?, ?, ?, 'ACTIVE', ?, ?)
    `).run(userId, borrowerLenderName, loanType, amount, amount, interestRate, dueDate, notes, now, now);
  }

  updateLoanStatus(loanId, userId, totalRepaid, remaining, status, now) {
    return db.prepare('UPDATE loans SET total_repaid = ?, remaining_amount = ?, status = ?, updated_at = ? WHERE id = ? AND user_id = ?')
      .run(totalRepaid, remaining, status, now, loanId, userId);
  }

  // Repayments
  insertRepayment(userId, loanId, amount, date, paymentMethod, notes, now) {
    return db.prepare(`
      INSERT INTO loan_repayments (user_id, loan_id, amount, repayment_date, payment_method, notes, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(userId, loanId, amount, date, paymentMethod, notes, now);
  }

  getRepaymentsSum(loanId, userId) {
    return db.prepare('SELECT SUM(amount) as total FROM loan_repayments WHERE loan_id = ? AND user_id = ?').get(loanId, userId);
  }

  // Company Expenses
  getCompanyExpenses(userId) {
    return db.prepare('SELECT * FROM company_expenses WHERE user_id = ? ORDER BY expense_date DESC').all(userId);
  }

  insertCompanyExpense(userId, date, title, amount, categoryName, notes, now) {
    return db.prepare(`
      INSERT INTO company_expenses (user_id, expense_date, title, amount, category_name, notes, is_reimbursed, created_at)
      VALUES (?, ?, ?, ?, ?, ?, 0, ?)
    `).run(userId, date, title, amount, categoryName, notes, now);
  }

  getCompanyExpenseById(expenseId, userId) {
    return db.prepare('SELECT * FROM company_expenses WHERE id = ? AND user_id = ?').get(expenseId, userId);
  }
}

module.exports = new FinanceRepository();
