import contextlib,io,json,os,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from otel_access import SCOPES,assess,admin_commands,main

class OtelAccessTest(unittest.TestCase):
    def test_signal_writer_does_not_count_as_signal_reader(self):
        result=assess(['monitoring.timeSeries.create','cloudtrace.traces.patch','logging.logEntries.create'],'signals')
        self.assertEqual(result['status'],'BLOCKED')
        self.assertEqual(set(result['missing']),SCOPES['signals'])
    def test_dashboard_editor_still_needs_metric_read_access(self):
        result=assess(SCOPES['dashboards']-{'monitoring.timeSeries.list'},'dashboards')
        self.assertEqual(result['missing'],['monitoring.timeSeries.list'])
    def test_metrics_scope_does_not_require_dashboard_write(self):
        result=assess(['monitoring.timeSeries.list'],'metrics')
        self.assertEqual(result['status'],'PASS')
        self.assertEqual(result['missing'],[])
    def test_admin_commands_validate_principal_and_never_execute(self):
        commands=admin_commands('valid-project','observer@valid-project.iam.gserviceaccount.com')
        self.assertEqual(len(commands),4)
        self.assertFalse(any('roles/owner' in c or 'allUsers' in c for c in commands))
        with self.assertRaises(ValueError):admin_commands('valid-project','user; command')
        with patch.object(sys,'argv',['otel_access.py','--project','valid-project','--principal','observer@valid-project.iam.gserviceaccount.com','--print-admin-commands']),patch('otel_access.subprocess.check_output') as call,contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(main(),0);call.assert_not_called()
    def test_read_only_preflight_saves_missing_permissions_without_token(self):
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory)/'result.json'
            response=io.StringIO(json.dumps({'permissions':['monitoring.timeSeries.list']}))
            with patch.object(sys,'argv',['otel_access.py','--project','valid-project','--scope','all','--out',str(out)]),patch('otel_access.subprocess.check_output',return_value='SECRET_TOKEN'),patch('otel_access.urllib.request.urlopen',return_value=response) as call,contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(main(),2)
            request=call.call_args.args[0]
            self.assertTrue(request.full_url.endswith(':testIamPermissions'))
            self.assertEqual(json.loads(request.data)['permissions'],sorted(SCOPES['all']))
            saved=out.read_text();self.assertNotIn('SECRET_TOKEN',saved)
            self.assertEqual(json.loads(saved)['status'],'BLOCKED')

if __name__=='__main__':unittest.main()
