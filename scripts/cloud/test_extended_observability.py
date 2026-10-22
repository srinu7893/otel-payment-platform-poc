import unittest
from unittest.mock import patch
from extended_observability import sandbox_login,recorded_check
from cloud_acceptance import request

class ExtendedColdStartTests(unittest.TestCase):
 def test_check_returns_session_in_memory_but_never_writes_token(self):
  from pathlib import Path
  import tempfile
  with tempfile.TemporaryDirectory() as folder:
   path=Path(folder)/'results.json';rows=[];session={'accessToken':'memory-only-secret'}
   self.assertIs(recorded_check('Registration',lambda:session,rows,path),session)
   self.assertNotIn('memory-only-secret',path.read_text());self.assertEqual(rows[0]['status'],'PASS')
   def fail():raise TimeoutError('sensitive-payload')
   with self.assertRaises(TimeoutError):recorded_check('Registration failed',fail,rows,path)
   self.assertEqual(rows[-1]['reason'],'TimeoutError');self.assertNotIn('sensitive-payload',path.read_text())
 def test_registration_and_login_allow_cold_chain_deadline(self):
  user={'username':'test-user','password':'secret'}
  with patch('extended_observability.request',side_effect=[{}, {'accessToken':'memory-only'}]) as call:
   self.assertEqual(sandbox_login('https://frontend.run.app',user),{'accessToken':'memory-only'})
   self.assertEqual(call.call_count,2)
   self.assertTrue(all(c.kwargs['timeout']==190 for c in call.call_args_list))
 def test_uncertain_registration_is_never_blindly_replayed(self):
  with patch('extended_observability.request',side_effect=TimeoutError) as call:
   with self.assertRaises(TimeoutError):sandbox_login('https://frontend.run.app',{'username':'test','password':'secret'})
   self.assertEqual(call.call_count,1)
 def test_request_timeout_is_bounded(self):
  with patch('cloud_acceptance.urllib.request.urlopen') as call:
   for timeout in (0,-1,241):
    with self.assertRaises(ValueError):request('https://frontend.run.app',timeout=timeout)
   call.assert_not_called()

if __name__=='__main__':unittest.main()
