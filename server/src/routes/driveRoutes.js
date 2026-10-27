const express = require('express');
const router = express.Router();
const driveController = require('../controllers/driveController');
const optionalAuthMiddleware = require('../middleware/optionalAuthMiddleware');

router.use(optionalAuthMiddleware);

router.post('/connect', driveController.connectDrive);
router.get('/status', driveController.getStatus);
router.post('/disconnect', driveController.disconnectDrive);
router.post('/backup/now', driveController.backupNow);
router.get('/backup/list', driveController.listBackups);
router.get('/backups', driveController.listBackups);
router.get('/backup/latest', driveController.getLatestBackup);
router.get('/backup/version/:version', driveController.getBackupVersion);
router.post('/backup/restore', driveController.restoreBackup);
router.post('/backup/restore-version', driveController.restoreBackupVersion);

module.exports = router;
