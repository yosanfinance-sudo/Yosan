const express = require('express');
const router = express.Router();
const financeController = require('../controllers/financeController');
const authMiddleware = require('../middleware/authMiddleware');

router.use(authMiddleware);

router.get('/accounts', financeController.getAccounts);
router.post('/accounts', financeController.createAccount);

router.get('/categories', financeController.getCategories);
router.post('/categories', financeController.createCategory);

router.get('/rules', financeController.getRules);
router.post('/rules', financeController.createRule);

router.get('/transactions', financeController.getTransactions);
router.get('/transactions/:id', financeController.getTransactionById);
router.post('/transactions', financeController.createTransaction);

router.get('/loans', financeController.getLoans);
router.post('/loans', financeController.createLoan);
router.post('/loans/:id/repay', financeController.recordRepayment);

router.get('/company-expenses', financeController.getCompanyExpenses);
router.post('/company-expenses', financeController.createCompanyExpense);

router.get('/dashboard', financeController.getDashboardData);

module.exports = router;
