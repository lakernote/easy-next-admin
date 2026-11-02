-- Generic Trace and metrics history are now served by Easy Observability; remove the duplicate local diagnosis tools.
UPDATE `sys_role_permission`
SET `deleted` = 1, `update_time` = NOW(), `remark` = 'Legacy local diagnostics removed'
WHERE `deleted` = 0
  AND `permission_resource_id` IN (
      SELECT `id` FROM `sys_menu`
      WHERE `permission_code` IN (
          'monitor:diagnosis:advanced-view', 'monitor:diagnosis:capture',
          'monitor:diagnosis:jfr-control', 'monitor:diagnosis:jfr-download'
      )
  );

UPDATE `sys_menu`
SET `deleted` = 1, `enable` = 0, `update_time` = NOW()
WHERE `deleted` = 0
  AND `permission_code` IN (
      'monitor:diagnosis:advanced-view', 'monitor:diagnosis:capture',
      'monitor:diagnosis:jfr-control', 'monitor:diagnosis:jfr-download'
  );

DROP TABLE IF EXISTS `observability_remote_call_log`;
