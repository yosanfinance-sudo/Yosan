const financeService = require('../services/financeService');

class FinanceController {
  async getAccounts(req, res) {
    try {
      const result = await financeService.getAccounts(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_ACCOUNTS_FAILED', message: err.message });
    }
  }

  async createAccount(req, res) {
    try {
      const result = await financeService.createAccount(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      res.status(500).json({ error: 'CREATE_ACCOUNT_FAILED', message: err.message });
    }
  }

  async getCategories(req, res) {
    try {
      const result = await financeService.getCategories(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_CATEGORIES_FAILED', message: err.message });
    }
  }

  async createCategory(req, res) {
    try {
      const result = await financeService.createCategory(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      res.status(500).json({ error: 'CREATE_CATEGORY_FAILED', message: err.message });
    }
  }

  async getRules(req, res) {
    try {
      const result = await financeService.getRules(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_RULES_FAILED', message: err.message });
    }
  }

  async createRule(req, res) {
    try {
      const result = await financeService.createRule(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      res.status(500).json({ error: 'CREATE_RULE_FAILED', message: err.message });
    }
  }

  async getTransactions(req, res) {
    try {
      const result = await financeService.getTransactions(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_TRANSACTIONS_FAILED', message: err.message });
    }
  }

  async getTransactionById(req, res) {
    try {
      const result = await financeService.getTransactionById(req.params.id, req.user.id);
      if (!result) {
        return res.status(404).json({ error: 'NOT_FOUND', message: 'Transaction not found or access denied' });
      }
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_TRANSACTION_FAILED', message: err.message });
    }
  }

  async createTransaction(req, res) {
    try {
      const result = await financeService.createTransaction(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      res.status(500).json({ error: 'CREATE_TRANSACTION_FAILED', message: err.message });
    }
  }

  async getLoans(req, res) {
    try {
      const result = await financeService.getLoans(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_LOANS_FAILED', message: err.message });
    }
  }

  async createLoan(req, res) {
    try {
      const result = await financeService.createLoan(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      res.status(500).json({ error: 'CREATE_LOAN_FAILED', message: err.message });
    }
  }

  async recordRepayment(req, res) {
    try {
      const result = await financeService.recordRepayment(req.user.id, req.params.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      if (err.message === 'LOAN_NOT_FOUND') {
        return res.status(404).json({ error: 'NOT_FOUND', message: 'Loan record not found or access denied' });
      }
      res.status(500).json({ error: 'REPAYMENT_FAILED', message: err.message });
    }
  }

  async getCompanyExpenses(req, res) {
    try {
      const result = await financeService.getCompanyExpenses(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_COMPANY_EXPENSES_FAILED', message: err.message });
    }
  }

  async createCompanyExpense(req, res) {
    try {
      const result = await financeService.createCompanyExpense(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      res.status(500).json({ error: 'CREATE_COMPANY_EXPENSE_FAILED', message: err.message });
    }
  }

  async getDashboardData(req, res) {
    try {
      const result = await financeService.getDashboardData(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_DASHBOARD_FAILED', message: err.message });
    }
  }
}

module.exports = new FinanceController();
