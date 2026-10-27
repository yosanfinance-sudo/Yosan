const statementAiService = require('../services/statementAiService');

class StatementController {
  async handleAiParse(req, res) {
    try {
      const { textContent, fileName } = req.body;

      if (!textContent || typeof textContent !== 'string' || textContent.trim().length === 0) {
        return res.status(400).json({
          error: 'INVALID_TEXT_CONTENT',
          message: 'Statement text content is required and cannot be empty'
        });
      }

      const result = await statementAiService.parseStatement(textContent.trim(), fileName);

      return res.json(result);
    } catch (err) {
      console.error('[STATEMENT_AI_PARSE_ERROR]', err);
      return res.status(500).json({
        error: 'STATEMENT_PARSE_FAILED',
        message: err.message || 'An error occurred while parsing the statement'
      });
    }
  }
}

module.exports = new StatementController();
